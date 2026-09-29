package com.powermaxv2.ytdownloader

import android.app.Application
import com.powermaxv2.ytdownloader.core.DownloadManager
import com.powermaxv2.ytdownloader.core.Engine
import com.powermaxv2.ytdownloader.core.InstagramSession
import com.powermaxv2.ytdownloader.core.Notifications
import com.powermaxv2.ytdownloader.core.Prefs
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
    val instagram = InstagramSession(this)

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Leftovers of downloads interrupted by a crash or a force-stop.
        File(cacheDir, "jobs").deleteRecursively()
        File(cacheDir, "analyze").deleteRecursively()

        prefs = Prefs(this)
        engine = Engine(this, prefs, scope)
        downloads = DownloadManager(this, scope, engine, prefs, instagram)
        Notifications.createChannels(this)
        engine.start()
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
