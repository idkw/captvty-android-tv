package dev.valentin.replaytv.catalog

import android.os.SystemClock
import dev.valentin.replaytv.model.CatalogItem
import dev.valentin.replaytv.model.Channels
import dev.valentin.replaytv.model.Source
import dev.valentin.replaytv.model.searchKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * M6+ n'a pas de moteur de recherche accessible : on charge la liste des programmes de M6, W9 et
 * 6ter (environ 750, 2,6 Mo) au démarrage de l'application, et la recherche filtre cette liste sur
 * la box. La liste est rechargée quand elle a plus de 30 minutes, au moment d'une recherche.
 */
class M6ProgramIndex(
    private val m6: M6Catalog,
    private val scope: CoroutineScope,
    private val now: () -> Long = SystemClock::elapsedRealtime,
) {
    private class Entry(val item: CatalogItem, val key: String)

    private val mutex = Mutex()
    private var entries: List<Entry>? = null
    private var loadedAt = 0L

    fun preload() {
        scope.launch { runCatching { entries() } }
    }

    suspend fun search(query: String): List<CatalogItem> {
        val key = query.searchKey()
        return entries().filter { key in it.key }.map { it.item }
    }

    /** Si le rechargement échoue, la liste précédente reste utilisée. */
    private suspend fun entries(): List<Entry> = mutex.withLock {
        val cached = entries
        if (cached != null && now() - loadedAt < MAX_AGE_MS) return cached
        val fresh = runCatching { load() }.getOrElse { error -> return cached ?: throw error }
        entries = fresh
        loadedAt = now()
        fresh
    }

    private suspend fun load(): List<Entry> = coroutineScope {
        SERVICES
            .map { service -> async { m6.channel(service).flatMap { it.items } } }
            .awaitAll()
            .flatten()
            .distinctBy { it.url }
            .map { Entry(it, "${it.title} ${it.subtitle.orEmpty()}".searchKey()) }
    }

    private companion object {
        const val MAX_AGE_MS = 30 * 60 * 1000L
        val SERVICES = (Channels.main + Channels.others).filter { it.source == Source.M6 }.map { it.code }
    }
}
