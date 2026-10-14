package com.freedomplayer.platform

import com.freedomplayer.core.RepeatMode
import com.freedomplayer.data.AudioEngine
import com.freedomplayer.data.EngineSnapshot
import com.freedomplayer.data.PreviewSnapshot
import com.freedomplayer.data.StreamSource
import com.freedomplayer.data.Track
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FrameGrabber
import java.nio.ShortBuffer
import java.util.concurrent.atomic.AtomicLong
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.SourceDataLine

/** Por dónde sale el sonido: la tarjeta de sonido (Java Sound) o, en las pruebas, ninguna. */
interface AudioOut {
    /** Abre la salida con muestras de 16 bits con signo, little-endian e intercaladas. */
    fun open(sampleRate: Int, channels: Int)

    /** Escribe; se queda esperando si el búfer está lleno (también en pausa). */
    fun write(bytes: ByteArray, length: Int)
    fun pause()
    fun resume()

    /** Tira lo que quede en el búfer (al ir a otro punto o al parar). */
    fun flush()

    /** Espera a que suene lo que queda en el búfer. */
    fun drain()

    /** Muestras (por canal) que ya han sonado desde que se abrió. */
    val playedFrames: Long
    fun close()
}

/** La salida de audio de Java: funciona en Windows, Linux y macOS sin instalar nada. */
class JavaSoundOut(private val bufferMillis: Int = 500) : AudioOut {
    private var line: SourceDataLine? = null

    override fun open(sampleRate: Int, channels: Int) {
        val format = AudioFormat(sampleRate.toFloat(), 16, channels, true, false)
        val l = try {
            AudioSystem.getSourceDataLine(format)
        } catch (e: IllegalArgumentException) {
            throw LineUnavailableException("No hay salida de audio para $format")
        }
        l.open(format, sampleRate * channels * 2 * bufferMillis / 1000)
        l.start()
        line = l
    }

    override fun write(bytes: ByteArray, length: Int) {
        line?.write(bytes, 0, length)
    }

    override fun pause() {
        line?.stop()
    }

    override fun resume() {
        line?.start()
    }

    override fun flush() {
        line?.flush()
    }

    override fun drain() {
        line?.drain()
    }

    override val playedFrames: Long get() = line?.longFramePosition ?: 0L

    override fun close() {
        line?.close()
        line = null
    }
}

/**
 * Una canción (o una vista previa) sonando: FFmpeg la abre y la descodifica en su propio hilo
 * (archivos y direcciones con cabeceras; webm/opus, m4a/aac, mp3, flac…) y [AudioOut] la toca.
 * Se maneja desde fuera (play, pausa, ir a, volumen) sin esperar al hilo.
 */
class StreamPlayer(
    val source: String,
    private val headers: Map<String, String>,
    startMs: Long,
    play: Boolean,
    volume: Float,
    private val out: AudioOut,
) {
    @Volatile
    var volume: Float = volume

    @Volatile
    private var playing = play

    @Volatile
    private var stopped = false

    /** Abriendo el archivo o la dirección (aún no suena). */
    @Volatile
    var loading = true
        private set

    /** Ha sonado entera (se puede volver atrás con [seek]). */
    @Volatile
    var finished = false
        private set

    @Volatile
    var error: Throwable? = null
        private set

    @Volatile
    var durationMs = 0L
        private set

    private val seekRequest = AtomicLong(-1)
    private val lock = Any()
    private var outOpen = false
    private var rate = 0
    private var baseMs = startMs.coerceAtLeast(0)
    private var baseFrames = 0L

    init {
        if (startMs > 0) seekRequest.set(startMs)
        Thread(::run, "FreedomPlayer audio").apply {
            isDaemon = true
            start()
        }
    }

    val isPlaying: Boolean get() = playing && !finished && error == null

    val positionMs: Long
        get() {
            val pending = seekRequest.get()
            if (pending >= 0) return pending
            synchronized(lock) {
                if (!outOpen || rate <= 0) return baseMs
                val played = (out.playedFrames - baseFrames).coerceAtLeast(0)
                val p = baseMs + played * 1000 / rate
                return if (durationMs > 0) p.coerceAtMost(durationMs) else p
            }
        }

    fun play() {
        playing = true
        synchronized(lock) { if (outOpen) out.resume() }
    }

    fun pause() {
        playing = false
        synchronized(lock) { if (outOpen) out.pause() }
    }

    fun seek(positionMs: Long) {
        seekRequest.set(positionMs.coerceAtLeast(0))
        finished = false
        // Si el hilo espera con el búfer lleno (en pausa), así sigue y atiende el salto.
        synchronized(lock) { if (outOpen) out.flush() }
    }

    fun close() {
        stopped = true
        synchronized(lock) {
            if (outOpen) {
                out.pause()
                out.flush()
            }
        }
    }

    private fun run() {
        var grabber: FFmpegFrameGrabber? = null
        try {
            quietFfmpeg()
            val g = FFmpegFrameGrabber(source)
            grabber = g
            configure(g)
            g.start()
            if (stopped) return
            durationMs = (g.lengthInTime / 1000).coerceAtLeast(0)
            val sampleRate = g.sampleRate.takeIf { it > 0 } ?: 44_100
            val channels = g.audioChannels.coerceIn(1, 2)
            synchronized(lock) {
                if (stopped) return
                out.open(sampleRate, channels)
                outOpen = true
                rate = sampleRate
                baseFrames = out.playedFrames
                if (!playing) out.pause()
            }
            loading = false
            var bytes = ByteArray(16 * 1024)
            while (!stopped) {
                val target = seekRequest.get()
                if (target >= 0) {
                    g.setTimestamp(target * 1000)
                    synchronized(lock) {
                        out.flush()
                        baseMs = target
                        baseFrames = out.playedFrames
                    }
                    seekRequest.compareAndSet(target, -1)
                    finished = false
                }
                val frame = g.grabSamples()
                if (frame == null) {
                    // Fin: que suene lo que queda y esperar (por si se vuelve atrás).
                    out.drain()
                    if (seekRequest.get() < 0 && !stopped) finished = true
                    while (!stopped && seekRequest.get() < 0) Thread.sleep(20)
                    continue
                }
                val samples = frame.samples?.firstOrNull() as? ShortBuffer ?: continue
                val n = samples.remaining()
                if (bytes.size < n * 2) bytes = ByteArray(n * 2)
                val v = volume.coerceIn(0f, 1f)
                val gain = v * v
                val start = samples.position()
                for (i in 0 until n) {
                    val s = (samples.get(start + i) * gain).toInt()
                    bytes[2 * i] = (s and 0xFF).toByte()
                    bytes[2 * i + 1] = ((s shr 8) and 0xFF).toByte()
                }
                if (seekRequest.get() >= 0 || stopped) continue
                out.write(bytes, n * 2)
            }
        } catch (e: Throwable) {
            if (!stopped) error = e
        } finally {
            loading = false
            synchronized(lock) {
                if (outOpen) out.close()
                outOpen = false
            }
            runCatching {
                grabber?.stop()
                grabber?.release()
            }
        }
    }

    private fun configure(g: FFmpegFrameGrabber) {
        g.sampleMode = FrameGrabber.SampleMode.SHORT
        g.sampleFormat = avutil.AV_SAMPLE_FMT_S16
        g.audioChannels = 2
        if (source.startsWith("http://") || source.startsWith("https://")) {
            headers.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.let { g.setOption("user_agent", it.value) }
            val others = headers.filterKeys { !it.equals("User-Agent", ignoreCase = true) }
            if (others.isNotEmpty()) g.setOption("headers", others.entries.joinToString("") { "${it.key}: ${it.value}\r\n" })
            g.setOption("reconnect", "1")
            g.setOption("reconnect_streamed", "1")
            g.setOption("reconnect_delay_max", "5")
            // Sin respuesta en 20 s, error (en microsegundos).
            g.setOption("rw_timeout", "20000000")
        }
    }

    companion object {
        @Volatile
        private var quiet = false

        /** FFmpeg cuenta mucho por la consola: solo los errores. */
        private fun quietFfmpeg() {
            if (quiet) return
            quiet = true
            avutil.av_log_set_level(avutil.AV_LOG_ERROR)
        }

        /** El error, dicho para el aviso. */
        fun describe(e: Throwable): String = when {
            e is LineUnavailableException -> "no hay ninguna salida de audio disponible"
            e is UnsatisfiedLinkError || e is NoClassDefFoundError -> "no se pudo cargar FFmpeg"
            e.message.orEmpty().contains("avformat_open_input") -> "no se pudo abrir (puede que el archivo ya no esté)"
            else -> e.message ?: e.javaClass.simpleName
        }
    }
}

/**
 * El audio de verdad en el PC: FFmpeg (JavaCV, viene dentro de la app) y Java Sound. La app
 * decide la cola y su orden aleatorio; aquí se toca en ese orden y se pasa a la siguiente al
 * acabar cada una (con repetir una, todas o ninguna).
 */
class DesktopEngine(private val output: () -> AudioOut = { JavaSoundOut() }) : AudioEngine {
    private var tracks: List<Track> = emptyList()
    private var index = -1
    private var repeat = RepeatMode.Off
    private var volume = 1f
    private var wantPlay = false
    private var ended = false
    private var transitions = 0
    private var error: String? = null
    private var errorSerial = 0
    private var failedInARow = 0
    private var current: StreamPlayer? = null

    /** Por dónde va mientras no hay nada abierto (al abrir la app, en pausa). */
    private var idlePosition = 0L

    private var preview: StreamPlayer? = null
    private var previewDurationMs = 0L

    private fun open(positionMs: Long, play: Boolean) {
        current?.close()
        current = null
        val t = tracks.getOrNull(index) ?: return
        idlePosition = positionMs
        current = StreamPlayer(t.uri, emptyMap(), positionMs, play, volume, output())
    }

    override fun load(tracks: List<Track>, index: Int, positionMs: Long, play: Boolean) {
        current?.close()
        current = null
        this.tracks = tracks
        this.index = if (tracks.isEmpty()) -1 else index.coerceIn(0, tracks.lastIndex)
        ended = false
        failedInARow = 0
        transitions++
        idlePosition = positionMs.coerceAtLeast(0)
        wantPlay = play && this.index >= 0
        // Al abrir la app (en pausa) no se abre nada hasta pulsar play.
        if (wantPlay) open(idlePosition, true)
    }

    override fun reorder(tracks: List<Track>, index: Int) {
        this.tracks = tracks
        this.index = if (tracks.isEmpty()) -1 else index.coerceIn(0, tracks.lastIndex)
    }

    override fun play() {
        if (index < 0) return
        if (ended) {
            ended = false
            idlePosition = 0
            current?.close()
            current = null
        }
        wantPlay = true
        val c = current
        if (c == null || c.error != null) open(idlePosition, true) else c.play()
    }

    override fun pause() {
        wantPlay = false
        current?.let {
            idlePosition = it.positionMs
            it.pause()
        }
    }

    override fun seekTo(positionMs: Long) {
        ended = false
        val p = positionMs.coerceAtLeast(0)
        idlePosition = p
        val c = current
        if (c != null && c.error == null) c.seek(p)
    }

    override fun skipTo(index: Int) {
        if (index !in tracks.indices) return
        this.index = index
        ended = false
        transitions++
        current?.close()
        current = null
        idlePosition = 0
        if (wantPlay) open(0, true)
    }

    override fun setRepeat(mode: RepeatMode) {
        repeat = mode
    }

    override fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
        current?.volume = this.volume
        preview?.volume = this.volume
    }

    override fun startPreview(source: StreamSource) {
        stopPreview()
        previewDurationMs = source.durationSec * 1000L
        preview = StreamPlayer(source.url, source.headers, 0, true, volume, output())
    }

    override fun previewPlay() {
        val p = preview ?: return
        if (p.finished) p.seek(0)
        p.play()
    }

    override fun previewPause() {
        preview?.pause()
    }

    override fun previewSeek(positionMs: Long) {
        preview?.seek(positionMs)
    }

    override fun stopPreview() {
        preview?.close()
        preview = null
    }

    /** Si la que suena ha acabado (o ha fallado), pasa a la siguiente según «repetir». */
    private fun advance() {
        val c = current ?: return
        val failure = c.error
        if (failure != null) {
            c.close()
            current = null
            val title = tracks.getOrNull(index)?.title ?: "la canción"
            error = "No se pudo reproducir «$title»: ${StreamPlayer.describe(failure)}."
            errorSerial++
            failedInARow++
            val next = when {
                index < tracks.lastIndex -> index + 1
                repeat == RepeatMode.All -> 0
                else -> -1
            }
            if (wantPlay && next >= 0 && failedInARow < tracks.size) {
                index = next
                transitions++
                open(0, true)
            } else {
                wantPlay = false
                idlePosition = 0
            }
            return
        }
        if (!c.finished) {
            if (!c.loading && c.positionMs > 0) failedInARow = 0
            return
        }
        c.close()
        current = null
        when {
            repeat == RepeatMode.One -> {
                transitions++
                open(0, wantPlay)
            }
            index < tracks.lastIndex -> {
                index++
                transitions++
                open(0, wantPlay)
            }
            repeat == RepeatMode.All -> {
                index = 0
                transitions++
                open(0, wantPlay)
            }
            else -> {
                // Fin de la cola: se para al principio de la última.
                wantPlay = false
                ended = true
                idlePosition = 0
            }
        }
    }

    override fun snapshot(): EngineSnapshot {
        advance()
        val c = current
        return EngineSnapshot(
            index = index,
            playing = wantPlay && c != null && c.error == null,
            positionMs = c?.positionMs ?: idlePosition,
            durationMs = c?.durationMs ?: 0L,
            ended = ended,
            transitions = transitions,
            error = error,
            errorSerial = errorSerial,
            preview = preview?.let { p ->
                PreviewSnapshot(
                    playing = p.isPlaying,
                    loading = p.loading,
                    positionMs = p.positionMs,
                    durationMs = p.durationMs.takeIf { it > 0 } ?: previewDurationMs,
                    ended = p.finished,
                    error = p.error?.let { StreamPlayer.describe(it).replaceFirstChar { c -> c.uppercase() } + "." },
                )
            },
        )
    }

    override fun release() {
        current?.close()
        current = null
        stopPreview()
    }
}
