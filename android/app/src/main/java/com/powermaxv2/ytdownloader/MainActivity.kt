package com.powermaxv2.ytdownloader

import android.content.ContentResolver
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powermaxv2.ytdownloader.ui.AppRoot
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.Tab
import com.powermaxv2.ytdownloader.ui.hub.ToolRegistry
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
        // A file shared to "Convertir avec Gulp" / "Aperçu avec Gulp" / "Analyser avec Gulp" or opened with Gulp: the
        // alias says which tool.
        val fileTool = when (intent?.component?.className) {
            CONVERT_ENTRY -> ToolRegistry.CONVERTER
            PREVIEW_ENTRY -> ToolRegistry.PREVIEW
            CHAT_ENTRY -> ToolRegistry.CHAT_STATS
            else -> null
        }
        if (fileTool != null) {
            val uri = when (intent?.action) {
                Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
                Intent.ACTION_VIEW -> intent.data
                else -> null
            }
            // content:// only: file:// paths (and anything else) could point into our own private files.
            if (uri != null && uri.scheme == ContentResolver.SCHEME_CONTENT) {
                viewModel.openToolWithFile(fileTool, uri)
            } else {
                viewModel.openTool(fileTool)
                if (uri != null) viewModel.toast("Ce fichier ne peut pas être ouvert ici.", success = false)
            }
            return
        }
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

        /** Exported activity-alias "Convertir avec Gulp" (SEND of any file). */
        const val CONVERT_ENTRY = "com.powermaxv2.ytdownloader.ConvertEntry"

        /** Exported activity-alias "Aperçu avec Gulp" (SEND of any file, VIEW of a content:// file). */
        const val PREVIEW_ENTRY = "com.powermaxv2.ytdownloader.PreviewEntry"

        /** Exported activity-alias "Analyser avec Gulp" (SEND of a WhatsApp export: .txt or .zip). */
        const val CHAT_ENTRY = "com.powermaxv2.ytdownloader.ChatEntry"
    }
}
