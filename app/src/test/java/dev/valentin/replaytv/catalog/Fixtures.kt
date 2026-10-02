package dev.valentin.replaytv.catalog

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** Extraits réels de tf1.fr (scripts, page, réponses GraphQL), figés le 2 octobre 2026. */
object Fixtures {
    fun text(name: String): String =
        checkNotNull(Fixtures::class.java.classLoader?.getResourceAsStream("fixtures/tf1/$name")) { "fixture $name introuvable" }
            .bufferedReader()
            .use { it.readText() }

    fun json(name: String): JsonElement = Json.parseToJsonElement(text(name))
}
