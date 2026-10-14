package com.freedomplayer.platform

import com.freedomplayer.core.Crossfade
import com.freedomplayer.core.EqBands
import com.freedomplayer.core.Equalizer
import com.freedomplayer.core.Loudness
import com.freedomplayer.core.LoudnessMeter
import com.freedomplayer.core.RepeatMode
import com.freedomplayer.data.AudioEngine
import com.freedomplayer.data.EngineSnapshot
import com.freedomplayer.data.LoudnessAnalyzer
import com.freedomplayer.data.PreviewSnapshot
import com.freedomplayer.data.SoundSettings
import com.freedomplayer.data.StreamSource
import com.freedomplayer.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
 *
 * Por el camino pasa por el ecualizador ([eqGains]), el volumen igualado ([gain]) y los fundidos:
 * de entrada los primeros [fadeInMs] y de salida los últimos [fadeOutMs] de la canción. Los
 * fundidos se calculan con el tiempo de la canción, así que no importa lo que haya en el búfer.
 */
class StreamPlayer(
    val source: String,
    private val headers: Map<String, String>,
    startMs: Long,
    play: Boolean,
    volume: Float,
    private val out: AudioOut,
    private val fadeInMs: Long = 0,
    gain: Float = 1f,
    eq: List<Float> = EqBands.flat,
) {
    @Volatile
    var volume: Float = volume

    /** Volumen igualado de esta canción (lineal; 1 = tal cual). */
    @Volatile
    var gain: Float = gain

    @Volatile
    var eqGains: List<Float> = eq

    /** Fundido de salida en los últimos ms de la canción (el motor lo pone antes de que lleguen). */
    @Volatile
    var fadeOutMs: Long = 0

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
            var floats = FloatArray(4 * 1024)
            val eq = Equalizer(sampleRate, channels)
            var eqApplied: List<Float>? = null
            var preamp = 1f
            // Por dónde va lo descodificado (en tramas), para los fundidos.
            var clock = 0L
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
                    clock = target * sampleRate / 1000
                    eq.reset()
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
                val frames = n / channels
                if (bytes.size < n * 2) bytes = ByteArray(n * 2)
                if (floats.size < n) floats = FloatArray(n)
                val start = samples.position()
                for (i in 0 until n) floats[i] = samples.get(start + i) / 32768f
                val wanted = eqGains
                if (wanted !== eqApplied) {
                    eq.setGains(wanted)
                    eqApplied = wanted
                    preamp = Loudness.linear(EqBands.preampDb(wanted))
                }
                eq.process(floats, frames)
                val v = volume.coerceIn(0f, 1f)
                val base = v * v * gain * preamp * 32767f
                val fadeIn = fadeInMs
                val fadeOut = fadeOutMs
                val total = durationMs
                var i = 0
                for (f in 0 until frames) {
                    var k = base
                    if (fadeIn > 0 || fadeOut > 0) {
                        val t = (clock + f) * 1000 / sampleRate
                        if (fadeIn > 0 && t < fadeIn) k *= Crossfade.fadeIn(t.toFloat() / fadeIn)
                        if (fadeOut > 0 && total > 0 && t >= total - fadeOut) k *= Crossfade.fadeOut((t - (total - fadeOut)).toFloat() / fadeOut)
                    }
                    for (ch in 0 until channels) {
                        val s = (floats[i] * k).toInt().coerceIn(-32768, 32767)
                        bytes[2 * i] = (s and 0xFF).toByte()
                        bytes[2 * i + 1] = ((s shr 8) and 0xFF).toByte()
                        i++
                    }
                }
                clock += frames
                if (seekRequest.get() >= 0 || stopped) continue
                out.write(bytes, frames * channels * 2)
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
 * acabar cada una (con repetir una, todas o ninguna). Con fundido, la siguiente empieza unos
 * segundos antes de que acabe la que suena, que se va apagando mientras la otra sube.
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

    /** Temporizador «al acabar esta canción» y si ya se ha parado ahí (play sigue con la siguiente). */
    private var pauseAtEnd = false
    private var pausedAtEnd = false

    private var sound = SoundSettings()

    /** La que se va apagando durante un fundido (la que suena de verdad es [current]). */
    private var fading: StreamPlayer? = null

    /** Hay un fundido en marcha (para las pruebas). */
    val crossfading: Boolean get() = fading != null

    /** Por dónde va mientras no hay nada abierto (al abrir la app, en pausa). */
    private var idlePosition = 0L

    private var preview: StreamPlayer? = null
    private var previewDurationMs = 0L

    private fun open(positionMs: Long, play: Boolean, fadeInMs: Long = 0) {
        current?.close()
        current = null
        val t = tracks.getOrNull(index) ?: return
        idlePosition = positionMs
        current = StreamPlayer(t.uri, emptyMap(), positionMs, play, volume, output(), fadeInMs, gainOf(t), sound.eqGains)
    }

    private fun gainOf(t: Track): Float = if (sound.normalize) Loudness.linear(t.gainDb) else 1f

    /** Corta el fundido en marcha (al saltar, pausar o cambiar la cola). */
    private fun stopFading() {
        fading?.close()
        fading = null
    }

    override fun load(tracks: List<Track>, index: Int, positionMs: Long, play: Boolean) {
        stopFading()
        current?.close()
        current = null
        this.tracks = tracks
        this.index = if (tracks.isEmpty()) -1 else index.coerceIn(0, tracks.lastIndex)
        ended = false
        pausedAtEnd = false
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
        fading?.play()
        if (pausedAtEnd) {
            pausedAtEnd = false
            wantPlay = true
            current?.close()
            current = null
            moveOn()
            return
        }
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
        fading?.pause()
        current?.let {
            idlePosition = it.positionMs
            it.pause()
        }
    }

    override fun seekTo(positionMs: Long) {
        stopFading()
        ended = false
        pausedAtEnd = false
        val p = positionMs.coerceAtLeast(0)
        idlePosition = p
        val c = current
        if (c != null && c.error == null) c.seek(p)
    }

    override fun skipTo(index: Int) {
        if (index !in tracks.indices) return
        stopFading()
        this.index = index
        ended = false
        pausedAtEnd = false
        transitions++
        current?.close()
        current = null
        idlePosition = 0
        if (wantPlay) open(0, true)
    }

    override fun setRepeat(mode: RepeatMode) {
        repeat = mode
    }

    override fun setPauseAtEndOfSong(on: Boolean) {
        pauseAtEnd = on
    }

    override fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
        current?.volume = this.volume
        fading?.volume = this.volume
        preview?.volume = this.volume
    }

    override fun setSound(sound: SoundSettings) {
        val before = this.sound
        this.sound = sound
        for (p in listOfNotNull(current, fading, preview)) p.eqGains = sound.eqGains
        if (before.normalize != sound.normalize) tracks.getOrNull(index)?.let { t -> current?.gain = gainOf(t) }
        if (sound.crossfadeMs <= 0) current?.fadeOutMs = 0
    }

    override fun startPreview(source: StreamSource) {
        stopPreview()
        previewDurationMs = source.durationSec * 1000L
        preview = StreamPlayer(source.url, source.headers, 0, true, volume, output(), eq = sound.eqGains)
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

    /** La que viene después de la que suena según «repetir» (sin repetir una), o -1. */
    private fun nextIndex(): Int = when {
        repeat == RepeatMode.One -> -1
        index < tracks.lastIndex -> index + 1
        repeat == RepeatMode.All && tracks.size > 1 -> 0
        else -> -1
    }

    /**
     * Fundido: un poco antes ya se avisa a la que suena de que se apague al final (lo que va
     * descodificando lleva medio segundo de ventaja) y, al llegar, empieza la siguiente subiendo.
     */
    private fun crossfade(c: StreamPlayer) {
        val len = sound.crossfadeMs
        if (len <= 0 || !wantPlay || pauseAtEnd || fading != null) return
        val total = c.durationMs
        if (c.loading || c.error != null || c.finished || total < len * 2) return
        val next = nextIndex()
        if (next < 0) return
        val at = c.positionMs
        if (c.fadeOutMs == 0L && at >= total - len - 1_500) c.fadeOutMs = len
        if (c.fadeOutMs > 0 && at >= total - len) {
            fading = c
            current = null
            index = next
            transitions++
            failedInARow = 0
            open(0, true, fadeInMs = len)
        }
    }

    /** Si la que suena ha acabado (o ha fallado), pasa a la siguiente según «repetir». */
    private fun advance() {
        fading?.let { f -> if (f.finished || f.error != null) stopFading() }
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
            crossfade(c)
            return
        }
        if (pausedAtEnd) return
        if (pauseAtEnd) {
            // Temporizador: se queda al final de esta, en pausa.
            pausedAtEnd = true
            wantPlay = false
            idlePosition = c.positionMs
            return
        }
        c.close()
        current = null
        moveOn()
    }

    /** Ha acabado la que sonaba: la siguiente según «repetir», o fin de la cola. */
    private fun moveOn() {
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
        stopFading()
        current?.close()
        current = null
        stopPreview()
    }
}

/** Mide lo fuerte que suena un archivo (LUFS) descodificándolo entero con FFmpeg, sin tocarlo. */
object FfmpegLoudness : LoudnessAnalyzer {
    override suspend fun measure(path: String): Float? = withContext(Dispatchers.IO) { measureNow(path) }

    fun measureNow(path: String): Float? {
        val g = FFmpegFrameGrabber(path)
        return try {
            g.sampleMode = FrameGrabber.SampleMode.SHORT
            g.sampleFormat = avutil.AV_SAMPLE_FMT_S16
            g.audioChannels = 2
            g.start()
            val rate = g.sampleRate.takeIf { it > 0 } ?: return null
            val channels = g.audioChannels.coerceIn(1, 2)
            val meter = LoudnessMeter(rate, channels)
            var floats = FloatArray(8 * 1024)
            while (true) {
                val frame = g.grabSamples() ?: break
                val samples = frame.samples?.firstOrNull() as? ShortBuffer ?: continue
                val n = samples.remaining()
                if (floats.size < n) floats = FloatArray(n)
                val start = samples.position()
                for (i in 0 until n) floats[i] = samples.get(start + i) / 32768f
                meter.add(floats, n / channels)
            }
            meter.integrated()
        } catch (e: Exception) {
            null
        } finally {
            runCatching {
                g.stop()
                g.release()
            }
        }
    }
}
