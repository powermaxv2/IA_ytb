package com.powermaxv2.ytdownloader.core.convert

import android.content.Context
import com.powermaxv2.ytdownloader.core.Engine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * Runs the FFmpeg bundled by youtubedl-android (`<nativeLibraryDir>/libffmpeg.so`) as a separate process, with
 * the same library path youtubedl-android uses. Arguments go straight to the process (no shell).
 */
class FfmpegRunner(private val context: Context, private val engine: Engine) {

    /**
     * Runs FFmpeg with [args] (from [FfmpegArgs.build]) writing [output]. [duration] (seconds, when known) turns
     * FFmpeg's position into [onProgress] 0..1; without it the length is read from FFmpeg's log. Cancelling the
     * coroutine kills FFmpeg and deletes the partial [output]. Throws [ConversionError] on failure.
     */
    suspend fun run(args: List<String>, output: File, target: Target?, duration: Double?, onProgress: (Float) -> Unit) {
        try {
            engine.awaitReady()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ConversionError("Le moteur de conversion n'a pas pu démarrer. Redémarrez Gulp et réessayez.", e.message)
        }
        val executable = File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")
        if (!executable.exists()) throw ConversionError("Le moteur de conversion est absent de cette installation de Gulp.")
        val packages = File(context.noBackupFilesDir, "youtubedl-android/packages")
        val libraryPath = listOf(File(packages, "python/usr/lib"), File(packages, "ffmpeg/usr/lib"))
            .joinToString(":") { it.absolutePath }

        withContext(Dispatchers.IO) {
            val builder = ProcessBuilder(listOf(executable.absolutePath) + args)
                .directory(output.parentFile)
            builder.environment()["LD_LIBRARY_PATH"] = libraryPath
            val process = try {
                builder.start()
            } catch (e: Exception) {
                throw ConversionError("Le moteur de conversion n'a pas pu démarrer. Redémarrez Gulp et réessayez.", e.toString())
            }
            runCatching { process.outputStream.close() }
            val tail = ArrayDeque<String>()
            val logDuration = AtomicReference<Double?>(null)
            val limit = FfmpegArgs.maxSeconds(target ?: Target.MP4)?.toDouble()
            fun length(): Double? = (duration ?: logDuration.get())?.let { d -> if (limit != null) minOf(d, limit) else d }

            // FFmpeg's log and its progress each on their own thread: a full pipe would block FFmpeg (and us).
            val stderr = thread(name = "ffmpeg-log", isDaemon = true) {
                runCatching {
                    process.errorStream.bufferedReader().forEachLine { line ->
                        if (logDuration.get() == null) FfmpegProgress.parseDuration(line)?.let { logDuration.set(it) }
                        synchronized(tail) {
                            tail.addLast(line)
                            while (tail.size > LOG_LINES) tail.removeFirst()
                        }
                    }
                }
            }
            val stdout = thread(name = "ffmpeg-progress", isDaemon = true) {
                runCatching {
                    process.inputStream.bufferedReader().forEachLine { line ->
                        if (FfmpegProgress.isEnd(line)) {
                            onProgress(1f)
                        } else {
                            FfmpegProgress.parseOutTime(line)?.let { seconds ->
                                FfmpegProgress.fraction(seconds, length())?.let(onProgress)
                            }
                        }
                    }
                }
            }
            val code = try {
                runInterruptible { process.waitFor() }
            } catch (e: CancellationException) {
                process.destroy()
                output.delete()
                throw e
            } finally {
                if (isAlive(process)) process.destroy()
            }
            stdout.join(2_000)
            stderr.join(2_000)
            if (code != 0) {
                val log = synchronized(tail) { tail.toList() }
                output.delete()
                throw ConversionError(FfmpegErrors.message(log, target), log.joinToString("\n"))
            }
            if (!output.exists() || output.length() == 0L) {
                output.delete()
                throw ConversionError("La conversion n'a produit aucun fichier.")
            }
        }
    }

    private fun isAlive(process: Process): Boolean = try {
        process.exitValue()
        false
    } catch (e: IllegalThreadStateException) {
        true
    }

    private companion object {
        /** Lines of FFmpeg's log kept for the error message and the "Détails" of a failure. */
        const val LOG_LINES = 40
    }
}
