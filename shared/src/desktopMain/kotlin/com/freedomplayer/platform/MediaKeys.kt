package com.freedomplayer.platform

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.win32.StdCallLibrary
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Una tecla multimedia del teclado. */
enum class MediaKey { PlayPause, Next, Previous, Stop }

/**
 * Las teclas multimedia del teclado en Windows (reproducir/pausar, siguiente, anterior y parar),
 * aunque la ventana no tenga el foco o esté en la bandeja. Se registran como teclas rápidas del
 * sistema (RegisterHotKey) en un hilo propio que espera sus mensajes. Si otra app ya las tiene
 * o no es Windows, simplemente no se registran.
 */
object MediaKeys {
    /** Los códigos de Windows de cada tecla (VK_MEDIA_…). */
    val codes: Map<MediaKey, Int> = mapOf(
        MediaKey.Next to 0xB0,
        MediaKey.Previous to 0xB1,
        MediaKey.Stop to 0xB2,
        MediaKey.PlayPause to 0xB3,
    )

    private const val WM_HOTKEY = 0x0312
    private const val WM_QUIT = 0x0012
    private const val MOD_NOREPEAT = 0x4000

    val supported: Boolean get() = System.getProperty("os.name").orEmpty().lowercase().startsWith("windows")

    /**
     * Empieza a escuchar; [onKey] se llama desde el hilo de las teclas. Devuelve con qué parar, o
     * null si aquí no se puede (no es Windows, falla la biblioteca del sistema o no se ha podido
     * registrar ninguna tecla).
     */
    fun listen(onKey: (MediaKey) -> Unit): AutoCloseable? {
        if (!supported) return null
        val user32 = runCatching { Native.load("user32", User32::class.java) }.getOrNull() ?: return null
        val kernel32 = runCatching { Native.load("kernel32", Kernel32::class.java) }.getOrNull() ?: return null
        val ready = CountDownLatch(1)
        var threadId = 0
        var registered = emptyList<MediaKey>()
        val thread = Thread({
            // Las teclas rápidas son del hilo que las registra: se registran y se esperan aquí.
            threadId = kernel32.GetCurrentThreadId()
            registered = MediaKey.entries.filter { key -> user32.RegisterHotKey(null, key.ordinal + 1, MOD_NOREPEAT, codes.getValue(key)) }
            ready.countDown()
            if (registered.isEmpty()) return@Thread
            try {
                val msg = Msg()
                while (user32.GetMessageW(msg, null, 0, 0) > 0) {
                    if (msg.message != WM_HOTKEY) continue
                    val key = MediaKey.entries.getOrNull(Pointer.nativeValue(msg.wParam).toInt() - 1) ?: continue
                    runCatching { onKey(key) }
                }
            } finally {
                registered.forEach { user32.UnregisterHotKey(null, it.ordinal + 1) }
            }
        }, "teclas-multimedia")
        thread.isDaemon = true
        thread.start()
        if (!ready.await(3, TimeUnit.SECONDS) || registered.isEmpty()) return null
        return AutoCloseable { user32.PostThreadMessageW(threadId, WM_QUIT, null, null) }
    }

    /** MSG de Windows. */
    @Structure.FieldOrder("hwnd", "message", "wParam", "lParam", "time", "x", "y")
    class Msg : Structure() {
        @JvmField var hwnd: Pointer? = null

        @JvmField var message: Int = 0

        @JvmField var wParam: Pointer? = null

        @JvmField var lParam: Pointer? = null

        @JvmField var time: Int = 0

        @JvmField var x: Int = 0

        @JvmField var y: Int = 0
    }

    internal interface User32 : StdCallLibrary {
        fun RegisterHotKey(hWnd: Pointer?, id: Int, fsModifiers: Int, vk: Int): Boolean

        fun UnregisterHotKey(hWnd: Pointer?, id: Int): Boolean

        fun GetMessageW(msg: Msg, hWnd: Pointer?, wMsgFilterMin: Int, wMsgFilterMax: Int): Int

        fun PostThreadMessageW(idThread: Int, msg: Int, wParam: Pointer?, lParam: Pointer?): Boolean
    }

    internal interface Kernel32 : StdCallLibrary {
        fun GetCurrentThreadId(): Int
    }
}
