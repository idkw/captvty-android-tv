package dev.valentin.replaytv.catalog

import dev.valentin.replaytv.model.CatalogItem
import dev.valentin.replaytv.model.CatalogRow
import dev.valentin.replaytv.model.Channel
import dev.valentin.replaytv.model.Section
import dev.valentin.replaytv.model.Source
import dev.valentin.replaytv.ytdlp.YtDlp
import okhttp3.OkHttpClient

class CatalogRepository(http: OkHttpClient, ytDlp: YtDlp, private val m6: M6Catalog) {

    private val franceTv = FranceTvCatalog(http)
    private val arte = ArteCatalog(http, ytDlp)
    private val tf1 = Tf1Catalog(http)

    suspend fun section(section: Section): List<CatalogRow> = when (section.source) {
        Source.FRANCE_TV -> franceTv.page(section.code)
        Source.ARTE -> arte.page(section.code)
        Source.TF1 -> tf1.page(section.code)
        Source.M6 -> m6.page(section.code)
    }

    suspend fun channel(channel: Channel): List<CatalogRow> = when (channel.source) {
        Source.FRANCE_TV -> franceTv.channelPage(channel.code)
        Source.ARTE -> arte.channel()
        Source.TF1 -> tf1.channel(channel.code)
        Source.M6 -> m6.channel(channel.code)
    }

    suspend fun collection(item: CatalogItem.Collection): List<CatalogRow> = when (item.source) {
        Source.FRANCE_TV -> franceTv.page(item.url.removePrefix(FranceTvCatalog.BASE))
        Source.ARTE -> arte.collection(item.url)
        Source.TF1 -> tf1.program(item.url)
        Source.M6 -> m6.program(item.url)
    }
}
