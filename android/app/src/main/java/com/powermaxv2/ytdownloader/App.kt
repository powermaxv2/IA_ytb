package com.powermaxv2.ytdownloader

import android.app.Application
import com.powermaxv2.ytdownloader.core.AppUpdater
import com.powermaxv2.ytdownloader.core.DownloadManager
import com.powermaxv2.ytdownloader.core.Engine
import com.powermaxv2.ytdownloader.core.Instagram
import com.powermaxv2.ytdownloader.core.Notifications
import com.powermaxv2.ytdownloader.core.Prefs
import com.powermaxv2.ytdownloader.core.UpdateCheckJob
import com.powermaxv2.ytdownloader.core.compress.CompressorController
import com.powermaxv2.ytdownloader.core.convert.ConverterController
import com.powermaxv2.ytdownloader.core.files.WorkDir
import com.powermaxv2.ytdownloader.core.pdfjoin.JoinController
import com.powermaxv2.ytdownloader.core.scan.ScanController
import com.powermaxv2.ytdownloader.core.sticker.StickerController
import com.powermaxv2.ytdownloader.core.trim.TrimController
import com.powermaxv2.ytdownloader.core.wipe.WipeController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

class App : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var prefs: Prefs
        private set
    lateinit var engine: Engine
        private set
    lateinit var downloads: DownloadManager
        private set
    lateinit var updater: AppUpdater
        private set

    /** The Convertisseur's state: lives as long as the app, so a conversion survives leaving its screen. */
    val converter: ConverterController by lazy { ConverterController(this, scope, engine) }

    /** The Compresseur's state, kept the same way (a compression survives leaving its screen). */
    val compressor: CompressorController by lazy { CompressorController(this, scope, engine) }

    /** The Scanner's pages, kept the same way (they stay when the user leaves the tool; gone at the next launch). */
    val scanner: ScanController by lazy { ScanController(this, scope) }

    /** The Découpeur's state, kept the same way (a cut survives leaving its screen). */
    val trimmer: TrimController by lazy { TrimController(this, scope, engine) }
    /** The Assembleur PDF's pages and work, kept the same way. */
    val pdfJoin: JoinController by lazy { JoinController(this, scope) }

    /** The Gomme's state, kept the same way (a cleaning survives leaving its screen). */
    val wiper: WipeController by lazy { WipeController(this, scope) }

    /** The Stickers tool's pictures and work, kept the same way (a pack being made survives leaving its screen). */
    val stickers: StickerController by lazy { StickerController(this, scope, engine) }

    /** True while the main screen is visible (Android only lets a visible app open the install confirmation). */
    @Volatile
    var inForeground = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Leftovers of downloads interrupted by a crash or a force-stop.
        File(cacheDir, "jobs").deleteRecursively()
        File(cacheDir, "analyze").deleteRecursively()
        // Copies of the files given to the tools (Convertisseur, Aperçu) never outlive a launch.
        WorkDir.wipeAll(this)
        // The Instagram login is gone (1.4.0): the session it stored on the phone is deleted.
        Instagram.purgeAccountData(this)

        prefs = Prefs(this)
        engine = Engine(this, prefs, scope)
        downloads = DownloadManager(this, scope, engine, prefs)
        updater = AppUpdater(this, prefs, scope)
        Notifications.createChannels(this)
        UpdateCheckJob.schedule(this, prefs.settings.value.autoCheckAppUpdates)
        engine.start()
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
