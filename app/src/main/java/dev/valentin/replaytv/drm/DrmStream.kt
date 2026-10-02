package dev.valentin.replaytv.drm

/** Un flux DASH chiffré en Widevine, et de quoi obtenir sa licence auprès de la chaîne. */
data class DrmStream(
    val manifestUrl: String,
    val licenseUrl: String,
    val licenseHeaders: Map<String, String> = emptyMap(),
)
