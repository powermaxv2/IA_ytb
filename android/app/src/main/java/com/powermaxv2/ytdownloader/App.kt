package com.powermaxv2.ytdownloader

import android.app.Application
import com.powermaxv2.ytdownloader.core.AppUpdater
import com.powermaxv2.ytdownloader.core.DownloadManager
import com.powermaxv2.ytdownloader.core.Engine
import com.powermaxv2.ytdownloader.core.Instagram
import com.powermaxv2.ytdownloader.core.Notifications
import com.powermaxv2.ytdownloader.core.Prefs
import com.powermaxv2.ytdownloader.core.UpdateCheckJob
import com.powermaxv2.ytdownloader.core.files.WorkDir
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
