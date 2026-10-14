package com.freedomplayer.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Las 5 bandas del ecualizador. */
object EqBands {
    /** Frecuencia central de cada banda, en Hz. */
    val centers = listOf(60f, 230f, 910f, 3_600f, 14_000f)
    val labels = listOf("60 Hz", "230 Hz", "910 Hz", "3,6 kHz", "14 kHz")

    /** Lo máximo que sube o baja cada banda, en dB. */
    const val MAX_DB = 12f
    val flat: List<Float> = List(5) { 0f }

    fun clamp(gains: List<Float>): List<Float> = List(5) { i -> (gains.getOrNull(i) ?: 0f).coerceIn(-MAX_DB, MAX_DB) }

    /**
     * Ganancia a [hz] para un ecualizador con otras bandas (el del sistema en Android): la de la
     * banda si coincide, o la mezcla de las dos de al lado en escala logarítmica.
     */
    fun gainAt(gains: List<Float>, hz: Float): Float {
        val g = clamp(gains)
        if (hz <= centers.first()) return g.first()
        if (hz >= centers.last()) return g.last()
        val i = centers.indexOfLast { it <= hz }
        val a = centers[i]
        val b = centers[i + 1]
        val t = kotlin.math.ln(hz / a) / kotlin.math.ln(b / a)
        return g[i] + (g[i + 1] - g[i]) * t
    }

    /** Lo que se baja todo antes de ecualizar para que lo que se sube no sature (la mitad de la subida mayor). */
    fun preampDb(gains: List<Float>): Float {
        val up = gains.maxOrNull() ?: 0f
        return if (up <= 0f) 0f else -up / 2f
    }

    /** «0,0,0,0,0» ↔ ganancias (así se guardan en los ajustes). */
    fun format(gains: List<Float>): String = clamp(gains).joinToString(",") { (kotlin.math.round(it * 10f) / 10f).toString() }

    fun parse(text: String?): List<Float> =
        clamp(text?.split(',')?.map { it.trim().toFloatOrNull() ?: 0f } ?: flat)
}

/** Ecualizaciones hechas. «Personalizado» es lo que el usuario mueve a mano. */
enum class EqPreset(val label: String, val gains: List<Float>) {
    Flat("Plano", listOf(0f, 0f, 0f, 0f, 0f)),
    Bass("Más graves", listOf(6f, 4f, 0f, 0f, 0f)),
    Vocal("Voz", listOf(-2f, -1f, 3f, 4f, 1f)),
    Rock("Rock", listOf(4f, 2f, -2f, 2f, 4f)),
    Electronic("Electrónica", listOf(5f, 2f, 0f, 2f, 5f)),
    Acoustic("Acústica", listOf(3f, 1f, 1f, 2f, 3f)),
    Night("Noche", listOf(-3f, 0f, 1f, -2f, -5f)),
    Custom("Personalizado", listOf(0f, 0f, 0f, 0f, 0f));

    companion object {
        val choices: List<EqPreset> get() = entries.filter { it != Custom }

        /** El preset que coincide con unas ganancias, o «Personalizado». */
        fun matching(gains: List<Float>): EqPreset =
            choices.firstOrNull { p -> p.gains.indices.all { kotlin.math.abs(p.gains[it] - (gains.getOrNull(it) ?: 0f)) < 0.05f } } ?: Custom
    }
}

/**
 * Un filtro biquad (las fórmulas del «Audio EQ Cookbook» de R. Bristow-Johnson) para muestras
 * intercaladas de [channels] canales. Guarda el estado de cada canal entre llamadas.
 */
class Biquad(private val channels: Int) {
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0
    private val x1 = DoubleArray(channels)
    private val x2 = DoubleArray(channels)
    private val y1 = DoubleArray(channels)
    private val y2 = DoubleArray(channels)

    fun set(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) {
        this.b0 = b0 / a0
        this.b1 = b1 / a0
        this.b2 = b2 / a0
        this.a1 = a1 / a0
        this.a2 = a2 / a0
    }

    fun peaking(rate: Int, freq: Double, q: Double, db: Double) {
        val a = 10.0.pow(db / 40)
        val w = 2 * PI * safe(freq, rate) / rate
        val alpha = sin(w) / (2 * q)
        set(1 + alpha * a, -2 * cos(w), 1 - alpha * a, 1 + alpha / a, -2 * cos(w), 1 - alpha / a)
    }

    fun lowShelf(rate: Int, freq: Double, db: Double) {
        val a = 10.0.pow(db / 40)
        val w = 2 * PI * safe(freq, rate) / rate
        val c = cos(w)
        val beta = 2 * sqrt(a) * sin(w) / 2 * sqrt(2.0)
        set(
            a * ((a + 1) - (a - 1) * c + beta),
            2 * a * ((a - 1) - (a + 1) * c),
            a * ((a + 1) - (a - 1) * c - beta),
            (a + 1) + (a - 1) * c + beta,
            -2 * ((a - 1) + (a + 1) * c),
            (a + 1) + (a - 1) * c - beta,
        )
    }

    fun highShelf(rate: Int, freq: Double, db: Double) {
        val a = 10.0.pow(db / 40)
        val w = 2 * PI * safe(freq, rate) / rate
        val c = cos(w)
        val beta = 2 * sqrt(a) * sin(w) / 2 * sqrt(2.0)
        set(
            a * ((a + 1) + (a - 1) * c + beta),
            -2 * a * ((a - 1) + (a + 1) * c),
            a * ((a + 1) + (a - 1) * c - beta),
            (a + 1) - (a - 1) * c + beta,
            2 * ((a - 1) - (a + 1) * c),
            (a + 1) - (a - 1) * c - beta,
        )
    }

    /** Filtra [frames] tramas de [samples] (intercaladas) en el sitio. */
    fun process(samples: FloatArray, frames: Int) {
        var i = 0
        for (f in 0 until frames) {
            for (ch in 0 until channels) {
                val x = samples[i].toDouble()
                val y = b0 * x + b1 * x1[ch] + b2 * x2[ch] - a1 * y1[ch] - a2 * y2[ch]
                x2[ch] = x1[ch]
                x1[ch] = x
                y2[ch] = y1[ch]
                y1[ch] = y
                samples[i] = y.toFloat()
                i++
            }
        }
    }

    fun reset() {
        x1.fill(0.0)
        x2.fill(0.0)
        y1.fill(0.0)
        y2.fill(0.0)
    }

    /** Por debajo de Nyquist, aunque la música venga a poca frecuencia de muestreo. */
    private fun safe(freq: Double, rate: Int): Double = freq.coerceAtMost(rate * 0.45)
}

/**
 * Ecualizador de 5 bandas: estantes en los extremos (60 Hz y 14 kHz) y campanas en medio.
 * Con todas las bandas a 0 no toca el sonido.
 */
class Equalizer(private val rate: Int, private val channels: Int) {
    private val bands = List(5) { Biquad(channels) }
    private var gains: List<Float> = EqBands.flat
    private var flat = true

    fun setGains(newGains: List<Float>) {
        val g = EqBands.clamp(newGains)
        if (g == gains) return
        gains = g
        flat = g.all { kotlin.math.abs(it) < 0.05f }
        val c = EqBands.centers
        bands[0].lowShelf(rate, c[0].toDouble(), g[0].toDouble())
        for (i in 1..3) bands[i].peaking(rate, c[i].toDouble(), 0.7, g[i].toDouble())
        bands[4].highShelf(rate, c[4].toDouble(), g[4].toDouble())
    }

    fun process(samples: FloatArray, frames: Int) {
        if (flat) return
        for (band in bands) band.process(samples, frames)
    }

    fun reset() = bands.forEach { it.reset() }
}

/**
 * Sonoridad integrada de una canción (LUFS, como EBU R128 / ITU-R BS.1770): filtro K, bloques de
 * 400 ms que se solapan un 75 %, umbral absoluto de −70 LUFS y relativo de −10 LU.
 */
class LoudnessMeter(rate: Int, private val channels: Int) {
    private val shelf = Biquad(channels)
    private val highPass = Biquad(channels)
    private val quarter = maxOf(1, rate / 10)

    /** Suma de cuadrados de cada trozo de 100 ms (todos los canales). */
    private val quarters = ArrayList<Double>()
    private var sum = 0.0
    private var count = 0
    private var scratch = FloatArray(0)

    init {
        // Coeficientes del filtro K para cualquier frecuencia de muestreo (los de libebur128).
        run {
            val f0 = 1681.974450955533
            val g = 3.999843853973347
            val q = 0.7071752369554196
            val k = tan(PI * f0 / rate)
            val vh = 10.0.pow(g / 20)
            val vb = vh.pow(0.4996667741545416)
            val a0 = 1 + k / q + k * k
            shelf.set(vh + vb * k / q + k * k, 2 * (k * k - vh), vh - vb * k / q + k * k, a0, 2 * (k * k - 1), 1 - k / q + k * k)
        }
        run {
            val f0 = 38.13547087602444
            val q = 0.5003270373238773
            val k = tan(PI * f0 / rate)
            highPass.set(1.0, -2.0, 1.0, 1 + k / q + k * k, 2 * (k * k - 1), 1 - k / q + k * k)
        }
    }

    /** Añade [frames] tramas (intercaladas, de −1 a 1). */
    fun add(samples: FloatArray, frames: Int) {
        val n = frames * channels
        if (scratch.size < n) scratch = FloatArray(n)
        samples.copyInto(scratch, 0, 0, n)
        shelf.process(scratch, frames)
        highPass.process(scratch, frames)
        var i = 0
        for (f in 0 until frames) {
            var s = 0.0
            for (ch in 0 until channels) {
                val v = scratch[i++].toDouble()
                s += v * v
            }
            sum += s
            count++
            if (count == quarter) {
                quarters.add(sum)
                sum = 0.0
                count = 0
            }
        }
    }

    /** null si no hay bastante audio (menos de 400 ms) o es silencio. */
    fun integrated(): Float? {
        if (quarters.size < 4) return null
        val blockFrames = quarter * 4.0
        val blocks = DoubleArray(quarters.size - 3) { i -> (quarters[i] + quarters[i + 1] + quarters[i + 2] + quarters[i + 3]) / blockFrames }
        fun lufs(meanSquare: Double) = -0.691 + 10 * log10(meanSquare)
        val loud = blocks.filter { it > 0 && lufs(it) > -70 }
        if (loud.isEmpty()) return null
        val relative = lufs(loud.average()) - 10
        val gated = loud.filter { lufs(it) > relative }
        if (gated.isEmpty()) return null
        return lufs(gated.average()).toFloat()
    }
}

/** Volumen igualado: cuánto subir o bajar cada canción para que todas suenen igual de fuertes. */
object Loudness {
    /** Nivel al que se dejan las canciones (el de Apple Music). */
    const val TARGET_LUFS = -16f

    /** dB a aplicar a una canción de [lufs]; sin medir, nada. Subir tiene un límite: [maxBoostDb]. */
    fun gainDb(lufs: Float?, maxBoostDb: Float = 0f): Float =
        if (lufs == null) 0f else (TARGET_LUFS - lufs).coerceIn(-18f, maxBoostDb)

    fun linear(db: Float): Float = 10f.pow(db / 20f)
}

/** Fundido entre canciones: curvas de igual potencia (la suma suena igual de fuerte). */
object Crossfade {
    /** Segundos que se pueden elegir. */
    val choices = listOf(3, 6, 9, 12)
    const val DEFAULT_SECONDS = 6

    /** Ganancia de la que entra, con [p] de 0 (empieza) a 1 (acaba el fundido). */
    fun fadeIn(p: Float): Float = sin(p.coerceIn(0f, 1f) * PI / 2).toFloat()

    /** Ganancia de la que se va. */
    fun fadeOut(p: Float): Float = cos(p.coerceIn(0f, 1f) * PI / 2).toFloat()
}
