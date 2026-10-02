package dev.valentin.replaytv.catalog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Résout les identifiants des requêtes GraphQL persistées de tf1.fr.
 *
 * Le serveur n'accepte que des requêtes désignées par un identifiant (« requesting by query is
 * forbidden »). Le site publie dans ses scripts une table nom → empreinte
 * (`"ProgramCatalogDocument",0,{__meta__:{hash:"sha256:…"}}`) que l'on retrouve de façon
 * déterministe : page HTML du catalogue → chemins `static/chunks/<nom>.js` → scripts → table.
 *
 * La table est mise en cache sur disque. On la réutilise tant que les requêtes réussissent, et on
 * la redécouvre quand le serveur ne reconnaît plus un identifiant ([reportUnknown]) ou quand le
 * cache a plus de sept jours. Une seule découverte à la fois, et pas de nouvelle tentative avant
 * une heure quand la précédente a échoué.
 */
class Tf1QueryResolver(
    private val cacheFile: File,
    private val fetch: suspend (url: String) -> String,
    private val now: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit = {},
) {
    @Serializable
    data class Cache(val fetchedAt: Long, val hashes: Map<String, String>)

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val mutex = Mutex()

    @Volatile
    private var cache: Cache? = null

    @Volatile
    private var loaded = false
    private var lastFailureAt: Long? = null
    private val invalidated = HashSet<String>()

    /** Empreinte `sha256:…` de la requête nommée, ou null si elle est introuvable. */
    suspend fun hash(name: String): String? {
        val current = load()
        val fresh = current != null && now() - current.fetchedAt < MAX_AGE_MS
        val known = current?.hashes?.get(name)
        val suspect = synchronized(invalidated) { name in invalidated }
        if (fresh && known != null && !suspect) return known
        val refreshed = discover()
        return refreshed?.hashes?.get(name) ?: known
    }

    /** Le serveur a refusé l'identifiant de cette requête : la prochaine résolution redécouvrira la table. */
    fun reportUnknown(name: String) {
        log("identifiant refusé par le serveur pour $name")
        synchronized(invalidated) { invalidated += name }
    }

    private suspend fun load(): Cache? {
        if (loaded) return cache
        val read = withContext(Dispatchers.IO) {
            runCatching { if (cacheFile.exists()) json.decodeFromString(Cache.serializer(), cacheFile.readText()) else null }.getOrNull()
        }
        cache = read
        loaded = true
        return read
    }

    private suspend fun discover(): Cache? = mutex.withLock {
        val current = cache
        // Une découverte vient d'aboutir pendant que l'on attendait le verrou : inutile de recommencer.
        if (current != null && now() - current.fetchedAt < RECENT_MS && synchronized(invalidated) { invalidated.isEmpty() }) return current
        lastFailureAt?.let { if (now() - it < RETRY_AFTER_FAILURE_MS) return null }

        val table = runCatching { fetchTable() }.getOrElse { log("découverte impossible : ${it.message}"); emptyMap() }
        if (table.size < MIN_ENTRIES) {
            log("découverte échouée : ${table.size} requête(s) trouvée(s)")
            lastFailureAt = now()
            return null
        }
        log("table découverte : ${table.size} requêtes")
        val fresh = Cache(now(), table)
        cache = fresh
        synchronized(invalidated) { invalidated.clear() }
        withContext(Dispatchers.IO) {
            runCatching {
                cacheFile.parentFile?.mkdirs()
                cacheFile.writeText(json.encodeToString(Cache.serializer(), fresh))
            }
        }
        fresh
    }

    private suspend fun fetchTable(): Map<String, String> {
        val html = fetch(CATALOG_PAGE)
        val firstLevel = extractChunkPaths(html)
        val scripts = fetchAll(firstLevel)
        val table = HashMap<String, String>()
        scripts.values.forEach { table += parseQueryTable(it) }
        if (table.size < MIN_ENTRIES) {
            // La table peut vivre dans un script chargé par un autre script : un niveau de plus.
            val secondLevel = scripts.values.flatMapTo(HashSet()) { extractChunkPaths(it) } - firstLevel
            fetchAll(secondLevel).values.forEach { table += parseQueryTable(it) }
        }
        return table
    }

    private suspend fun fetchAll(paths: Set<String>): Map<String, String> = coroutineScope {
        val gate = Semaphore(PARALLEL_FETCHES)
        paths.take(MAX_CHUNKS).map { path ->
            async { gate.withPermit { path to (runCatching { fetch("$NEXT_BASE/$path") }.getOrDefault("")) } }
        }.awaitAll().toMap()
    }

    companion object {
        const val CATALOG_PAGE = "https://www.tf1.fr/programmes-tv"
        const val NEXT_BASE = "https://www.tf1.fr/_next"
        private const val MAX_AGE_MS = 7L * 24 * 3600 * 1000
        private const val RECENT_MS = 60_000L
        private const val RETRY_AFTER_FAILURE_MS = 3_600_000L
        private const val MIN_ENTRIES = 5
        private const val MAX_CHUNKS = 120
        private const val PARALLEL_FETCHES = 6

        private val CHUNK_PATH = Regex("""static/chunks/[A-Za-z0-9_\-.\[\]%/]+?\.js""")
        private val QUERY_ENTRY = Regex(""""([A-Za-z0-9_]+Document)",0,\{__meta__:\{hash:"(sha256:[0-9a-f]{64})"\}\}""")

        /** Chemins `static/chunks/<nom>.js` cités dans une page HTML ou un script. */
        fun extractChunkPaths(text: String): Set<String> = CHUNK_PATH.findAll(text).mapTo(LinkedHashSet()) { it.value }

        /** Table nom → empreinte trouvée dans un script, vide s'il n'en contient pas. */
        fun parseQueryTable(script: String): Map<String, String> =
            QUERY_ENTRY.findAll(script).associate { it.groupValues[1] to it.groupValues[2] }
    }
}
