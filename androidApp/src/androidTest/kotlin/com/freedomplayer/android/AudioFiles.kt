package com.freedomplayer.android

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.freedomplayer.data.Track
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/** Lo que comparten las pruebas del emulador: audio hecho a mano y el hilo principal. */
object AudioFiles {
    /** Un pitido de [seconds] segundos en WAV estéreo de 16 bits. */
    fun wav(dir: File, name: String, seconds: Double, rate: Int = 44_100): File {
        val frames = (seconds * rate).toInt()
        val bytes = ByteBuffer.allocate(44 + frames * 4).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray())
        bytes.putInt(36 + frames * 4)
        bytes.put("WAVE".toByteArray())
        bytes.put("fmt ".toByteArray())
        bytes.putInt(16)
        bytes.putShort(1)
        bytes.putShort(2)
        bytes.putInt(rate)
        bytes.putInt(rate * 4)
        bytes.putShort(4)
        bytes.putShort(16)
        bytes.put("data".toByteArray())
        bytes.putInt(frames * 4)
        for (i in 0 until frames) {
            val v = (sin(2 * PI * 440 * i / rate) * 6_000).toInt().toShort()
            bytes.putShort(v)
            bytes.putShort(v)
        }
        return File(dir, name).apply { writeBytes(bytes.array()) }
    }

    fun track(id: String, file: File) = Track(id, file.absolutePath, "Canción $id", "Artista", null, null, 0)

    /** Como en la app, el reproductor se toca siempre desde el hilo principal. */
    fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    /** Espera hasta [timeoutMs] a que [check] se cumpla (mirando cada 25 ms en el hilo principal). */
    fun <T> waitFor(what: String, timeoutMs: Long = 20_000, read: () -> T, check: (T) -> Boolean): T {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (true) {
            val value = onMain(read)
            if (check(value)) return value
            if (SystemClock.uptimeMillis() > end) throw AssertionError("No llega: $what. Último estado: $value")
            Thread.sleep(25)
        }
    }
}
