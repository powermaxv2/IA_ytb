package com.powermaxv2.ytdownloader.core.ringtone

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.annotation.RequiresApi
import com.powermaxv2.ytdownloader.core.MediaExporter
import com.powermaxv2.ytdownloader.core.SavedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume

/**
 * Saves a cut sound where Android's sound pickers look: Ringtones/Gulp, Notifications/Gulp or Alarms/Gulp, flagged
 * as a ringtone / notification / alarm (and not as music, so it stays out of the music apps). Then, when the user
 * allowed Gulp to change the system settings, makes it the phone's default sound.
 */
object RingtoneSaver {
    private const val MIME = "audio/mpeg"

    suspend fun save(context: Context, file: File, use: RingtoneUse, title: String): SavedFile = withContext(Dispatchers.IO) {
        val name = file.name
        val location = "${use.directory}/${MediaExporter.FOLDER}"
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveWithMediaStore(context, file, name, use, title, location)
        } else {
            saveLegacy(context, file, name, use, title)
        }
        SavedFile(uri, MIME, name, file.length(), location)
    }

    private fun flags(use: RingtoneUse, title: String) = ContentValues().apply {
        put(MediaStore.Audio.Media.TITLE, title)
        put(MediaStore.Audio.Media.IS_RINGTONE, if (use == RingtoneUse.RINGTONE) 1 else 0)
        put(MediaStore.Audio.Media.IS_NOTIFICATION, if (use == RingtoneUse.NOTIFICATION) 1 else 0)
        put(MediaStore.Audio.Media.IS_ALARM, if (use == RingtoneUse.ALARM) 1 else 0)
        put(MediaStore.Audio.Media.IS_MUSIC, 0)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveWithMediaStore(context: Context, file: File, name: String, use: RingtoneUse, title: String, location: String): Uri {
        val resolver = context.contentResolver
        val values = flags(use, title).apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, MIME)
            put(MediaStore.MediaColumns.RELATIVE_PATH, location)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: throw IOException("Impossible de créer le son dans $location")
        try {
            resolver.openOutputStream(uri)?.use { output -> file.inputStream().use { it.copyTo(output, 128 * 1024) } }
                ?: throw IOException("Impossible d'écrire dans $location")
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        return uri
    }

    @Suppress("DEPRECATION")
    private suspend fun saveLegacy(context: Context, file: File, name: String, use: RingtoneUse, title: String): Uri {
        val publicDir = when (use) {
            RingtoneUse.RINGTONE -> Environment.DIRECTORY_RINGTONES
            RingtoneUse.NOTIFICATION -> Environment.DIRECTORY_NOTIFICATIONS
            RingtoneUse.ALARM -> Environment.DIRECTORY_ALARMS
        }
        val dir = File(Environment.getExternalStoragePublicDirectory(publicDir), MediaExporter.FOLDER)
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Autorisez l'accès au stockage pour enregistrer le son.")
        var target = File(dir, name)
        var n = 1
        while (target.exists()) {
            target = File(dir, "${name.substringBeforeLast('.')} ($n).mp3")
            n++
        }
        file.copyTo(target)
        val scanned = withTimeoutOrNull(5_000) {
            suspendCancellableCoroutine<Uri?> { continuation ->
                MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(MIME)) { _, uri ->
                    if (continuation.isActive) continuation.resume(uri)
                }
            }
        } ?: return Uri.fromFile(target)
        // The scanner files sounds by folder, but says so explicitly too (some phones only read the flags).
        runCatching { context.contentResolver.update(scanned, flags(use, title), null, null) }
        return scanned
    }

    /** Whether Gulp may change the default sounds (Android 6+ asks once, in a settings page of its own). */
    fun canSetDefault(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(context)

    /** Makes [uri] the phone's default sound for [use]. False when Gulp is not allowed or the phone refused. */
    fun setDefault(context: Context, uri: Uri, use: RingtoneUse): Boolean {
        if (!canSetDefault(context) || uri.scheme != "content") return false
        val type = when (use) {
            RingtoneUse.RINGTONE -> RingtoneManager.TYPE_RINGTONE
            RingtoneUse.NOTIFICATION -> RingtoneManager.TYPE_NOTIFICATION
            RingtoneUse.ALARM -> RingtoneManager.TYPE_ALARM
        }
        return try {
            RingtoneManager.setActualDefaultRingtoneUri(context, type, uri)
            true
        } catch (e: SecurityException) {
            false
        } catch (e: RuntimeException) {
            false
        }
    }
}
