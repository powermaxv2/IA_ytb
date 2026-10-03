package com.powermaxv2.ytdownloader

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powermaxv2.ytdownloader.ui.AppRoot
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.Tab
import com.powermaxv2.ytdownloader.ui.skin.tokensFor
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        applySystemBars(tokensFor(App.instance.prefs.settings.value.skin).isLight)
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            // Status / navigation bar icons follow the skin: dark on Papier, light on the dark skins.
            val settings by viewModel.prefs.settings.collectAsStateWithLifecycle()
            val light = tokensFor(settings.skin).isLight
            LaunchedEffect(light) { applySystemBars(light) }
            AppRoot(viewModel)
        }
    }

    private fun applySystemBars(light: Boolean) {
        val style = if (light) {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        } else {
            SystemBarStyle.dark(Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        App.instance.inForeground = true
    }

    override fun onResume() {
        super.onResume()
        val updater = App.instance.updater
        // Back from "Installer des applis inconnues": the waiting update installs if it is now allowed.
        updater.resumeInstall()
        App.instance.scope.launch { runCatching { updater.check(force = false) } }
    }

    override fun onStop() {
        App.instance.inForeground = false
        super.onStop()
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let(viewModel::onSharedText)
            ACTION_SHOW_DOWNLOADS -> viewModel.showInDownloader(Tab.DOWNLOADS)
            // Only from our own notification (non-exported alias): MainActivity is exported for the launcher and Partager.
            ACTION_APP_UPDATE -> if (intent.component?.className == UPDATE_ENTRY) viewModel.startAppUpdate()
        }
    }

    companion object {
        const val ACTION_SHOW_DOWNLOADS = "com.powermaxv2.ytdownloader.SHOW_DOWNLOADS"
        const val ACTION_APP_UPDATE = "com.powermaxv2.ytdownloader.APP_UPDATE"

        /** activity-alias of the manifest that the update notification opens. */
        const val UPDATE_ENTRY = "com.powermaxv2.ytdownloader.UpdateEntry"
    }
}
