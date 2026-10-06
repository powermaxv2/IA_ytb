package com.powermaxv2.ytdownloader.core.preview

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext

/** Bars of a sound ([bars] 0..1) and whether only its beginning was decoded ([partial]). */
class WaveformData(val bars: FloatArray, val partial: Boolean)

/**
 * Decodes a sound with MediaExtractor + MediaCodec (what the phone already has) to measure its loudness, at most
 * [Waveform.MAX_DURATION_US] of sound or [Waveform.MAX_PCM_BYTES] of PCM. Cancellable between buffers; the codec
 * and the extractor are always released. Returns null when the phone cannot decode the file.
 */
object WaveformDecoder {
    private const val TIMEOUT_US = 10_000L

    /** Rounds without any buffer moving before giving up (a stuck decoder). */
    private const val MAX_IDLE_ROUNDS = 500

    suspend fun decode(context: Context, uri: Uri, bars: Int = Waveform.BUCKETS): WaveformData? = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return@withContext null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext null
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            var channels = format.intOr(MediaFormat.KEY_CHANNEL_COUNT, 1).coerceAtLeast(1)
            var sampleRate = format.intOr(MediaFormat.KEY_SAMPLE_RATE, 44_100).coerceAtLeast(1)
            var floatPcm = false

            val decoder = MediaCodec.createDecoderByType(mime)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()

            val series = PeakSeries(4096)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var pcmBytes = 0L
            var partial = false
            var idle = 0
            var shorts = ShortArray(0)
            var floats = FloatArray(0)
            while (true) {
                coroutineContext.ensureActive()
                var moved = false
                if (!inputDone) {
                    val index = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (index >= 0) {
                        moved = true
                        val buffer = decoder.getInputBuffer(index)
                        val size = if (buffer == null) -1 else extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(index, 0, size, extractor.sampleTime.coerceAtLeast(0), 0)
                            extractor.advance()
                        }
                    }
                }
                val out = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    out >= 0 -> {
                        moved = true
                        val buffer = decoder.getOutputBuffer(out)
                        if (buffer != null && info.size > 0) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            val ordered = buffer.order(ByteOrder.nativeOrder())
                            // Slices of ~20 ms: frames × channels samples each.
                            val sliceFrames = (sampleRate / 50).coerceAtLeast(1)
                            if (floatPcm) {
                                val fb = ordered.asFloatBuffer()
                                val n = fb.remaining()
                                if (floats.size < n) floats = FloatArray(n)
                                fb.get(floats, 0, n)
                                slices(series, info.presentationTimeUs, n, channels, sliceFrames, sampleRate) { a, b -> Waveform.peakFloat(floats, a, b) }
                            } else {
                                val sb = ordered.asShortBuffer()
                                val n = sb.remaining()
                                if (shorts.size < n) shorts = ShortArray(n)
                                sb.get(shorts, 0, n)
                                slices(series, info.presentationTimeUs, n, channels, sliceFrames, sampleRate) { a, b -> Waveform.peak16(shorts, a, b) }
                            }
                            pcmBytes += info.size
                        }
                        val end = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        decoder.releaseOutputBuffer(out, false)
                        if (end) break
                        if (info.presentationTimeUs >= Waveform.MAX_DURATION_US || pcmBytes >= Waveform.MAX_PCM_BYTES) {
                            partial = durationUs <= 0 || info.presentationTimeUs < durationUs - 1_000_000
                            break
                        }
                    }
                    out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        moved = true
                        val f = decoder.outputFormat
                        channels = f.intOr(MediaFormat.KEY_CHANNEL_COUNT, channels).coerceAtLeast(1)
                        sampleRate = f.intOr(MediaFormat.KEY_SAMPLE_RATE, sampleRate).coerceAtLeast(1)
                        floatPcm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                            f.intOr(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT) == AudioFormat.ENCODING_PCM_FLOAT
                    }
                }
                idle = if (moved) 0 else idle + 1
                if (idle > MAX_IDLE_ROUNDS) break
            }
            if (series.size == 0) return@withContext null
            WaveformData(Waveform.buckets(series, durationUs, bars), partial)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Unsupported codec, damaged file, decoder errors: no waveform, the player still works.
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    /** Splits [n] interleaved samples starting at [startUs] into ~20 ms slices and records each one's peak. */
    private inline fun slices(
        series: PeakSeries,
        startUs: Long,
        n: Int,
        channels: Int,
        sliceFrames: Int,
        sampleRate: Int,
        peak: (Int, Int) -> Float,
    ) {
        val sliceSamples = sliceFrames * channels
        var from = 0
        while (from < n) {
            val to = minOf(from + sliceSamples, n)
            val frame = from / channels
            series.add(startUs + frame * 1_000_000L / sampleRate, peak(from, to))
            from = to
        }
    }

    private fun MediaFormat.intOr(key: String, fallback: Int): Int =
        if (containsKey(key)) runCatching { getInteger(key) }.getOrDefault(fallback) else fallback
}
