package com.freedomplayer.platform

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.freedomplayer.core.LoudnessMeter
import com.freedomplayer.data.LoudnessAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteOrder

/**
 * Mide lo fuerte que suena un archivo (LUFS) en Android: lo descodifica entero con los
 * descodificadores del sistema (MediaExtractor y MediaCodec: opus/webm, aac/m4a, mp3, wav…)
 * sin que suene, más deprisa que en tiempo real.
 */
object AndroidLoudness : LoudnessAnalyzer {
    override suspend fun measure(path: String): Float? = withContext(Dispatchers.IO) {
        try {
            measureNow(path)
        } catch (e: Exception) {
            null
        }
    }

    fun measureNow(path: String): Float? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(path)
            val track = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val c = MediaCodec.createDecoderByType(mime)
            codec = c
            c.configure(format, null, null, 0)
            c.start()
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            var meter: LoudnessMeter? = null
            var floats = FloatArray(8 * 1024)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var idle = 0
            while (true) {
                if (!inputDone) {
                    val inIndex = c.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buffer = c.getInputBuffer(inIndex) ?: return null
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            c.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            c.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = c.dequeueOutputBuffer(info, 10_000)
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val f = c.outputFormat
                    rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    if (f.containsKey(MediaFormat.KEY_PCM_ENCODING)) encoding = f.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    meter = null
                    continue
                }
                if (outIndex < 0) {
                    // Por si un descodificador se queda callado: no esperar para siempre.
                    if (inputDone && ++idle > 300) break
                    continue
                }
                idle = 0
                val out = c.getOutputBuffer(outIndex)
                if (out != null && info.size > 0 && channels > 0) {
                    out.position(info.offset)
                    out.limit(info.offset + info.size)
                    val ordered = out.slice().order(ByteOrder.nativeOrder())
                    val n: Int
                    if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
                        val fb = ordered.asFloatBuffer()
                        n = fb.remaining()
                        if (floats.size < n) floats = FloatArray(n)
                        fb.get(floats, 0, n)
                    } else {
                        val sb = ordered.asShortBuffer()
                        n = sb.remaining()
                        if (floats.size < n) floats = FloatArray(n)
                        for (i in 0 until n) floats[i] = sb.get(i) / 32768f
                    }
                    val m = meter ?: LoudnessMeter(rate, channels).also { meter = it }
                    m.add(floats, n / channels)
                }
                c.releaseOutputBuffer(outIndex, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }
            return meter?.integrated()
        } finally {
            runCatching {
                codec?.stop()
                codec?.release()
            }
            extractor.release()
        }
    }
}
