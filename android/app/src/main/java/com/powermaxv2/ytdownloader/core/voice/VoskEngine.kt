package com.powermaxv2.ytdownloader.core.voice

import kotlinx.coroutines.ensureActive
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Vosk (Apache-2.0, Alpha Cephei) listening to raw 16 kHz mono PCM. Each stretch of speech ends in a final result
 * that becomes a [VoiceSentence]; in between, [onProgress] gets the sentences so far, the words being heard and how
 * far it is in the sound. Runs on the calling (IO) thread; cancellable between blocks; native objects always closed.
 */
object VoskEngine {
    private const val SAMPLE_RATE = 16_000f

    /** A quarter of a second of sound per block. */
    private const val BLOCK_BYTES = 8_000

    /** Live updates at most this often (the screen does not need every block). */
    private const val UPDATE_MS = 120L

    suspend fun transcribe(modelDir: File, pcm: File, onProgress: (List<VoiceSentence>, String, Long) -> Unit): List<VoiceSentence> {
        runCatching { LibVosk.setLogLevel(LogLevel.WARNINGS) }
        val sentences = ArrayList<VoiceSentence>()
        Model(modelDir.absolutePath).use { model ->
            Recognizer(model, SAMPLE_RATE).use { recognizer ->
                recognizer.setWords(true)
                val buffer = ByteArray(BLOCK_BYTES)
                var read = 0L
                var lastUpdate = 0L
                pcm.inputStream().buffered(64 * 1024).use { input ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.readNBytesCompat(buffer)
                        if (n <= 0) break
                        read += n
                        val heardMs = read / 32
                        if (recognizer.acceptWaveForm(buffer, n)) {
                            VoiceText.parseResult(recognizer.result).takeIf { it.isNotEmpty() }?.let { sentences += VoiceSentence(it) }
                            onProgress(sentences.toList(), "", heardMs)
                            lastUpdate = System.currentTimeMillis()
                        } else {
                            val now = System.currentTimeMillis()
                            if (now - lastUpdate >= UPDATE_MS) {
                                onProgress(sentences.toList(), VoiceText.parsePartial(recognizer.partialResult), heardMs)
                                lastUpdate = now
                            }
                        }
                    }
                }
                VoiceText.parseResult(recognizer.finalResult).takeIf { it.isNotEmpty() }?.let { sentences += VoiceSentence(it) }
            }
        }
        return sentences
    }

    /** Fills [buffer] as far as the stream allows (blocks stay whole until the end). */
    private fun java.io.InputStream.readNBytesCompat(buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val n = read(buffer, total, buffer.size - total)
            if (n < 0) break
            total += n
        }
        return total
    }
}
