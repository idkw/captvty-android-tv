package dev.valentin.replaytv.catalog

import dev.valentin.replaytv.model.CatalogItem
import dev.valentin.replaytv.model.CatalogRow
import dev.valentin.replaytv.model.Section
import dev.valentin.replaytv.model.Source
import dev.valentin.replaytv.ytdlp.YtDlp
import okhttp3.OkHttpClient

class CatalogRepository(http: OkHttpClient, ytDlp: YtDlp) {

    private val franceTv = FranceTvCatalog(http)
    private val arte = ArteCatalog(http, ytDlp)

    suspend fun section(section: Section): List<CatalogRow> = when (section.source) {
        Source.FRANCE_TV -> franceTv.page(section.code)
        Source.ARTE -> arte.page(section.code)
    }

    suspend fun collection(item: CatalogItem.Collection): List<CatalogRow> = when (item.source) {
        Source.FRANCE_TV -> franceTv.page(item.url.removePrefix(FranceTvCatalog.BASE))
        Source.ARTE -> arte.collection(item.url)
    }
}
