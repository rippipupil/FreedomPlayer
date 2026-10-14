package com.freedomplayer.platform

import com.freedomplayer.core.RepeatMode
import com.freedomplayer.data.EngineSnapshot
import com.freedomplayer.data.Track
import org.bytedeco.ffmpeg.global.avcodec
import org.bytedeco.javacv.FFmpegFrameRecorder
import java.io.DataOutputStream
import java.io.File
import java.nio.ShortBuffer
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * El audio del PC con FFmpeg de verdad (el que va dentro de la app): abrir, sonar entera, ir a
 * un punto, pausa, la cola con «repetir» y un archivo que falta. La salida es de mentira y va
 * más deprisa que el tiempo real, para que las pruebas duren poco.
 */
class DesktopAudioTest {
    private val dir: File = Files.createTempDirectory("fp-audio").toFile()

    @AfterTest
    fun clean() {
        dir.deleteRecursively()
    }

    /** Un pitido de [seconds] segundos en estéreo. */
    private fun tone(seconds: Double, rate: Int): ShortArray {
        val frames = (seconds * rate).toInt()
        return ShortArray(frames * 2) { i ->
            val t = (i / 2).toDouble() / rate
            (sin(2 * PI * 440 * t) * 8000).toInt().toShort()
        }
    }

    /** WAV sin comprimir, escrito a mano. */
    private fun wav(name: String, seconds: Double, rate: Int = 44_100): File {
        val data = tone(seconds, rate)
        val file = File(dir, name)
        DataOutputStream(file.outputStream().buffered()).use { out ->
            fun le32(v: Int) = out.writeInt(Integer.reverseBytes(v))
            fun le16(v: Int) = out.writeShort(java.lang.Short.reverseBytes(v.toShort()).toInt())
            out.writeBytes("RIFF")
            le32(36 + data.size * 2)
            out.writeBytes("WAVEfmt ")
            le32(16)
            le16(1)
            le16(2)
            le32(rate)
            le32(rate * 4)
            le16(4)
            le16(16)
            out.writeBytes("data")
            le32(data.size * 2)
            data.forEach { le16(it.toInt()) }
        }
        return file
    }

    /** Comprimido con FFmpeg, como lo que se descarga de YouTube (opus en webm, aac en m4a). */
    private fun encoded(name: String, format: String, codec: Int, seconds: Double, rate: Int): File {
        val file = File(dir, name)
        val recorder = FFmpegFrameRecorder(file, 2)
        recorder.format = format
        recorder.audioCodec = codec
        recorder.sampleRate = rate
        recorder.audioBitrate = 128_000
        recorder.start()
        recorder.recordSamples(rate, 2, ShortBuffer.wrap(tone(seconds, rate)))
        recorder.stop()
        recorder.release()
        return file
    }

    /** Una salida que «suena» [speed] veces más deprisa que el tiempo real. */
    private class FakeOut(private val speed: Double = 25.0) : AudioOut {
        @Volatile
        private var paused = false

        @Volatile
        private var flushed = false
        private var rate = 44_100
        private var channels = 2
        private val frames = AtomicLong()

        override fun open(sampleRate: Int, channels: Int) {
            rate = sampleRate
            this.channels = channels
        }

        override fun write(bytes: ByteArray, length: Int) {
            flushed = false
            var left = length / (2 * channels)
            // Suena a trocitos: la pausa para enseguida, como una tarjeta de sonido.
            while (left > 0) {
                while (paused && !flushed) Thread.sleep(1)
                if (flushed) return
                val n = minOf(left, 256)
                Thread.sleep(0, (n * 1_000_000_000.0 / rate / speed).toInt().coerceAtMost(999_999))
                frames.addAndGet(n.toLong())
                left -= n
            }
        }

        override fun pause() {
            paused = true
        }

        override fun resume() {
            paused = false
        }

        override fun flush() {
            flushed = true
        }

        override fun drain() = Unit

        override val playedFrames: Long get() = frames.get()

        override fun close() = Unit
    }

    private fun waitFor(what: String, timeoutMs: Long = 10_000, check: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!check()) {
            if (System.currentTimeMillis() > end) fail("No llega: $what")
            Thread.sleep(5)
        }
    }

    private fun near(expected: Long, actual: Long, tolerance: Long = 120) =
        assertTrue(abs(expected - actual) <= tolerance, "esperaba unos $expected ms y es $actual")

    @Test
    fun aSongPlaysToTheEnd() {
        val file = wav("a.wav", 1.0)
        val player = StreamPlayer(file.absolutePath, emptyMap(), 0, true, 1f, FakeOut())
        waitFor("el final") { player.finished }
        assertNull(player.error)
        near(1000, player.durationMs)
        near(1000, player.positionMs)
        player.close()
    }

    @Test
    fun seekingWhilePausedAndThenPlaying() {
        val file = wav("b.wav", 3.0)
        val player = StreamPlayer(file.absolutePath, emptyMap(), 0, false, 1f, FakeOut())
        waitFor("que se abra") { !player.loading }
        player.seek(2_000)
        near(2_000, player.positionMs)
        Thread.sleep(100)
        near(2_000, player.positionMs, tolerance = 150)
        assertTrue(!player.finished, "en pausa no avanza")
        player.play()
        waitFor("el final") { player.finished }
        near(3_000, player.positionMs)
        player.close()
    }

    @Test
    fun startsWhereItWasLeft() {
        val file = wav("c.wav", 2.0)
        val player = StreamPlayer(file.absolutePath, emptyMap(), 1_500, false, 1f, FakeOut())
        waitFor("que se abra") { !player.loading }
        Thread.sleep(50)
        near(1_500, player.positionMs, tolerance = 150)
        player.close()
    }

    @Test
    fun compressedFormatsFromYouTubeAlsoPlay() {
        val files = listOf(
            runCatching { encoded("d.webm", "webm", avcodec.AV_CODEC_ID_OPUS, 1.0, 48_000) },
            runCatching { encoded("e.m4a", "ipod", avcodec.AV_CODEC_ID_AAC, 1.0, 44_100) },
        )
        // Si este FFmpeg no trae alguno de los codificadores, se prueba con el que haya.
        val made = files.mapNotNull { it.getOrNull() }
        assertTrue(made.isNotEmpty(), "No se pudo crear ningún archivo: ${files.map { it.exceptionOrNull()?.message }}")
        for (file in made) {
            val player = StreamPlayer(file.absolutePath, emptyMap(), 0, true, 1f, FakeOut())
            waitFor("el final de ${file.name}") { player.finished || player.error != null }
            assertNull(player.error, "${file.name}: ${player.error}")
            assertTrue(player.durationMs >= 900, "${file.name} dura ${player.durationMs} ms")
            // Lo que ha sonado es el segundo entero (la duración del contenedor puede ir con relleno).
            near(1_000, player.positionMs, tolerance = 150)
            player.close()
        }
    }

    private fun track(id: String, file: File) = Track(id, file.absolutePath, "Canción $id", "Artista", null, null, 0)

    private fun EngineSnapshot.describe() = "index=$index playing=$playing pos=$positionMs ended=$ended transitions=$transitions"

    @Test
    fun theQueueFollowsOnAndStopsAtTheEnd() {
        val tracks = listOf(track("1", wav("1.wav", 0.4)), track("2", wav("2.wav", 0.4)), track("3", wav("3.wav", 0.4)))
        val engine = DesktopEngine { FakeOut() }
        engine.load(tracks, 0, 0, play = true)
        val start = engine.snapshot().transitions
        waitFor("la tercera") { engine.snapshot().index == 2 }
        waitFor("el final de la cola") { engine.snapshot().ended }
        val end = engine.snapshot()
        assertEquals(2, end.index, end.describe())
        assertTrue(!end.playing, end.describe())
        assertEquals(0, end.positionMs, "vuelve al principio de la última")
        assertEquals(start + 2, end.transitions)
        engine.release()
    }

    @Test
    fun repeatOneAndRepeatAll() {
        val tracks = listOf(track("1", wav("1.wav", 0.3)), track("2", wav("2.wav", 0.3)))
        val engine = DesktopEngine { FakeOut() }
        engine.setRepeat(RepeatMode.One)
        engine.load(tracks, 1, 0, play = true)
        val start = engine.snapshot().transitions
        waitFor("que se repita") { engine.snapshot().transitions >= start + 2 }
        assertEquals(1, engine.snapshot().index, "repetir una: siempre la misma")

        engine.setRepeat(RepeatMode.All)
        waitFor("que vuelva a la primera") { engine.snapshot().index == 0 }
        assertTrue(engine.snapshot().playing)
        engine.release()
    }

    @Test
    fun pauseSeekAndSkip() {
        val tracks = listOf(track("1", wav("1.wav", 3.0)), track("2", wav("2.wav", 3.0)))
        val engine = DesktopEngine { FakeOut(speed = 2.0) }
        engine.load(tracks, 0, 0, play = true)
        waitFor("que suene") { engine.snapshot().positionMs > 100 }
        engine.pause()
        val paused = engine.snapshot().positionMs
        Thread.sleep(150)
        near(paused, engine.snapshot().positionMs, tolerance = 60)
        assertTrue(!engine.snapshot().playing)
        engine.seekTo(2_000)
        near(2_000, engine.snapshot().positionMs, tolerance = 150)
        engine.play()
        assertTrue(engine.snapshot().playing)
        engine.skipTo(1)
        val s = engine.snapshot()
        assertEquals(1, s.index)
        assertTrue(s.positionMs < 300, s.describe())
        engine.release()
    }

    @Test
    fun aMissingFileIsReportedAndTheNextOnePlays() {
        val tracks = listOf(track("x", File(dir, "no-existe.webm")), track("2", wav("2.wav", 0.5)))
        val engine = DesktopEngine { FakeOut() }
        engine.load(tracks, 0, 0, play = true)
        waitFor("el aviso") { engine.snapshot().errorSerial > 0 }
        val s = engine.snapshot()
        assertNotNull(s.error)
        assertTrue(s.error!!.contains("Canción x"), s.error)
        assertEquals(1, s.index, "sigue con la siguiente")
        engine.release()
    }

    @Test
    fun previewPlaysApartFromTheQueue() {
        val song = track("1", wav("1.wav", 2.0))
        val clip = wav("p.wav", 0.5)
        val engine = DesktopEngine { FakeOut() }
        engine.load(listOf(song), 0, 0, play = false)
        engine.startPreview(com.freedomplayer.data.StreamSource(clip.absolutePath, durationSec = 1))
        waitFor("el final de la vista previa") { engine.snapshot().preview?.ended == true }
        val s = engine.snapshot()
        near(500, s.preview!!.durationMs)
        assertTrue(!s.playing, "la cola sigue en pausa")
        engine.stopPreview()
        assertNull(engine.snapshot().preview)
        engine.release()
    }
}
