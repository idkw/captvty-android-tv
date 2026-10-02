package dev.valentin.replaytv

import android.app.Application
import android.content.Context
import dev.valentin.replaytv.catalog.CatalogRepository
import dev.valentin.replaytv.download.DownloadManager
import dev.valentin.replaytv.download.DownloadService
import dev.valentin.replaytv.ytdlp.YtDlp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class ReplayTvApp : Application() {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", USER_AGENT)
                        .header("Accept-Language", "fr-FR,fr;q=0.9")
                        .build(),
                )
            }
            .build()
    }

    lateinit var ytDlp: YtDlp
        private set
    lateinit var catalog: CatalogRepository
        private set
    lateinit var downloads: DownloadManager
        private set

    override fun onCreate() {
        super.onCreate()
        ytDlp = YtDlp(this, scope)
        catalog = CatalogRepository(http, ytDlp)
        downloads = DownloadManager(this, ytDlp, scope)
        DownloadService.ensureChannel(this)
        ytDlp.initAsync()
    }

    companion object {
        // Les sites de replay servent parfois une page dégradée aux user-agents inconnus.
        const val USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

        fun from(context: Context): ReplayTvApp = context.applicationContext as ReplayTvApp
    }
}
