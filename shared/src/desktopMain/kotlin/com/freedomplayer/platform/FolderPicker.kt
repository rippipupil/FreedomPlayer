package com.freedomplayer.platform

import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import java.awt.EventQueue
import java.io.File
import javax.swing.JFileChooser

/**
 * El selector de carpetas del sistema. En Windows, el mismo que usa el Explorador
 * (IFileOpenDialog en modo carpetas); en el resto, o si ese no se puede abrir, el de Swing.
 */
object FolderPicker {
    private val windows = System.getProperty("os.name").orEmpty().lowercase().contains("win")

    /** Abre el selector, espera a que se cierre y devuelve la carpeta elegida (null si se cancela). */
    fun choose(title: String, start: File?): File? {
        if (windows) {
            var outcome: Result<File?> = Result.failure(IllegalStateException("Sin abrir"))
            // COM en un hilo propio (apartamento STA), que es lo que pide el diálogo.
            val worker = Thread({ outcome = runCatching { WindowsFolderDialog.show(title, start) } }, "selector-de-carpetas")
            worker.isDaemon = true
            worker.start()
            worker.join()
            outcome.onSuccess { return it }
        }
        return swingChooser(title, start)
    }

    private fun swingChooser(title: String, start: File?): File? {
        var chosen: File? = null
        val show = {
            val chooser = JFileChooser(start?.takeIf { it.isDirectory })
            chooser.dialogTitle = title
            chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            chooser.isAcceptAllFileFilterUsed = false
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chosen = chooser.selectedFile
        }
        if (EventQueue.isDispatchThread()) show() else EventQueue.invokeAndWait { show() }
        return chosen
    }
}

/**
 * IFileOpenDialog con FOS_PICKFOLDERS, llamado por COM a mano con JNA: CoCreateInstance y los
 * métodos por su posición en la tabla del objeto (como en ShObjIdl_core.h).
 */
internal object WindowsFolderDialog {
    private const val COINIT_APARTMENTTHREADED = 0x2
    private const val COINIT_DISABLE_OLE1DDE = 0x4
    private const val CLSCTX_INPROC_SERVER = 0x1
    private const val FOS_PICKFOLDERS = 0x20
    private const val FOS_FORCEFILESYSTEM = 0x40
    private const val FOS_PATHMUSTEXIST = 0x800
    private val SIGDN_FILESYSPATH = 0x80058000L.toInt()

    /** HRESULT_FROM_WIN32(ERROR_CANCELLED): se cerró sin elegir. */
    private val CANCELLED = 0x800704C7L.toInt()

    const val CLSID_FILE_OPEN_DIALOG = "DC1C5A9C-E88A-4DDE-A5A1-60F82A20AEF7"
    private const val IID_FILE_OPEN_DIALOG = "D57C7288-D4AD-4768-BE02-9D969532D960"
    private const val IID_SHELL_ITEM = "43826D1E-E718-42EE-BC55-A1E261C37BFE"

    // Posición de cada método: IUnknown (0–2), IModalWindow (3) e IFileDialog (4–26).
    private const val RELEASE = 2
    private const val SHOW = 3
    private const val SET_OPTIONS = 9
    private const val GET_OPTIONS = 10
    private const val SET_FOLDER = 12
    private const val SET_TITLE = 17
    private const val GET_RESULT = 20

    // IShellItem: IUnknown (0–2), BindToHandler (3), GetParent (4), GetDisplayName (5).
    private const val ITEM_GET_DISPLAY_NAME = 5

    fun show(title: String, start: File?): File? {
        val ole32 = NativeLibrary.getInstance("ole32")
        val shell32 = NativeLibrary.getInstance("shell32")
        val init = ole32.getFunction("CoInitializeEx").invokeInt(arrayOf<Any?>(null, COINIT_APARTMENTTHREADED or COINIT_DISABLE_OLE1DDE))
        ok(init, "CoInitializeEx")
        try {
            val created = PointerByReference()
            ok(
                ole32.getFunction("CoCreateInstance").invokeInt(
                    arrayOf<Any?>(guid(CLSID_FILE_OPEN_DIALOG), null, CLSCTX_INPROC_SERVER, guid(IID_FILE_OPEN_DIALOG), created),
                ),
                "CoCreateInstance",
            )
            val dialog = created.value ?: error("CoCreateInstance sin diálogo")
            try {
                val options = IntByReference()
                ok(call(dialog, GET_OPTIONS, options), "GetOptions")
                ok(call(dialog, SET_OPTIONS, options.value or FOS_PICKFOLDERS or FOS_FORCEFILESYSTEM or FOS_PATHMUSTEXIST), "SetOptions")
                call(dialog, SET_TITLE, WString(title))
                if (start != null && start.isDirectory) {
                    val folder = PointerByReference()
                    val found = shell32.getFunction("SHCreateItemFromParsingName")
                        .invokeInt(arrayOf<Any?>(WString(start.absolutePath), null, guid(IID_SHELL_ITEM), folder))
                    val item = folder.value
                    if (found >= 0 && item != null) {
                        call(dialog, SET_FOLDER, item)
                        call(item, RELEASE)
                    }
                }
                val shown = call(dialog, SHOW, null)
                if (shown == CANCELLED) return null
                ok(shown, "Show")
                val result = PointerByReference()
                ok(call(dialog, GET_RESULT, result), "GetResult")
                val item = result.value ?: return null
                try {
                    val name = PointerByReference()
                    ok(call(item, ITEM_GET_DISPLAY_NAME, SIGDN_FILESYSPATH, name), "GetDisplayName")
                    val text = name.value ?: return null
                    val path = text.getWideString(0)
                    ole32.getFunction("CoTaskMemFree").invokeVoid(arrayOf<Any?>(text))
                    return File(path)
                } finally {
                    call(item, RELEASE)
                }
            } finally {
                call(dialog, RELEASE)
            }
        } finally {
            ole32.getFunction("CoUninitialize").invokeVoid(arrayOf<Any?>())
        }
    }

    /** Llama al método n.º [index] del objeto COM [obj] (el primer argumento es el propio objeto). */
    private fun call(obj: Pointer, index: Int, vararg args: Any?): Int {
        val method = obj.getPointer(0).getPointer(index.toLong() * Native.POINTER_SIZE)
        return Function.getFunction(method, Function.ALT_CONVENTION).invokeInt(arrayOf<Any?>(obj, *args))
    }

    private fun ok(hresult: Int, what: String) {
        check(hresult >= 0) { "$what: 0x%08X".format(hresult) }
    }

    /** Un GUID («XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX») como lo espera Windows: 16 bytes en memoria. */
    fun guid(text: String): Memory {
        val hex = text.replace("-", "")
        require(hex.length == 32) { "GUID mal escrito: $text" }
        val memory = Memory(16)
        memory.setInt(0, hex.substring(0, 8).toLong(16).toInt())
        memory.setShort(4, hex.substring(8, 12).toInt(16).toShort())
        memory.setShort(6, hex.substring(12, 16).toInt(16).toShort())
        for (i in 0 until 8) memory.setByte(8L + i, hex.substring(16 + 2 * i, 18 + 2 * i).toInt(16).toByte())
        return memory
    }
}
