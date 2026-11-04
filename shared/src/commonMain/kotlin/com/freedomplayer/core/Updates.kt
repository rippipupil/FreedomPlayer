package com.freedomplayer.core

/** Un archivo de una versión publicada (el APK, el instalador de Windows…). */
data class ReleaseAsset(val name: String, val url: String, val sizeBytes: Long)

/** Una versión publicada en GitHub Releases. */
data class AppRelease(
    /** «0.5.0» (la etiqueta sin la «v»). */
    val version: String,
    val notes: String,
    val pageUrl: String,
    val assets: List<ReleaseAsset>,
) {
    /** El archivo cuyo nombre termina en [suffix] (p. ej. «-android.apk»). */
    fun asset(suffix: String): ReleaseAsset? = assets.firstOrNull { it.name.endsWith(suffix, ignoreCase = true) }
}

/**
 * Comparar versiones «X.Y.Z» y «X.Y.Z-algo». Las compilaciones de prueba se numeran
 * 0.4.<n.º de ejecución> y las publicadas X.Y.Z, así que basta con comparar número a número;
 * una versión con guion (beta) va antes que la misma sin guion.
 */
object AppVersions {
    private val pattern = Regex("""^v?(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.]+))?$""")

    private data class Parsed(val numbers: List<Int>, val pre: String?)

    private fun parse(v: String): Parsed? {
        val m = pattern.matchEntire(v.trim()) ?: return null
        val numbers = m.groupValues.subList(1, 4).map { it.toIntOrNull() ?: return null }
        return Parsed(numbers, m.groupValues[4].ifEmpty { null })
    }

    fun isValid(v: String): Boolean = parse(v) != null

    /** Negativo si [a] es anterior a [b], 0 si son iguales, positivo si es posterior. null si alguna no es X.Y.Z. */
    fun compare(a: String, b: String): Int? {
        val pa = parse(a) ?: return null
        val pb = parse(b) ?: return null
        for (i in 0 until 3) {
            val c = pa.numbers[i].compareTo(pb.numbers[i])
            if (c != 0) return c
        }
        return when {
            pa.pre == pb.pre -> 0
            pa.pre == null -> 1
            pb.pre == null -> -1
            else -> pa.pre.compareTo(pb.pre)
        }
    }

    /**
     * ¿Merece la pena avisar de [candidate] teniendo [current]? Nunca si la actual no es una
     * versión (p. ej. «dev» al compilar en el ordenador).
     */
    fun isNewer(candidate: String, current: String): Boolean = (compare(candidate, current) ?: 0) > 0
}
