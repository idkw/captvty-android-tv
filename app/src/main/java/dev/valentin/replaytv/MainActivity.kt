package dev.valentin.replaytv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.valentin.replaytv.ui.ReplayTvRoot
import dev.valentin.replaytv.ui.theme.ReplayTvTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = ReplayTvApp.from(this)
        setContent {
            ReplayTvTheme {
                ReplayTvRoot(app)
            }
        }
    }
}
