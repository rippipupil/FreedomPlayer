package com.freedomplayer.platform

import kotlin.test.Test
import kotlin.test.assertContentEquals

/** El selector de carpetas de Windows no se puede abrir aquí, pero sí comprobar cómo le pasa los GUID. */
class FolderPickerTest {
    @Test
    fun guidsAreLaidOutLikeWindowsExpects() {
        val bytes = WindowsFolderDialog.guid(WindowsFolderDialog.CLSID_FILE_OPEN_DIALOG).getByteArray(0, 16)
        val expected = listOf(0x9C, 0x5A, 0x1C, 0xDC, 0x8A, 0xE8, 0xDE, 0x4D, 0xA5, 0xA1, 0x60, 0xF8, 0x2A, 0x20, 0xAE, 0xF7)
            .map { it.toByte() }
            .toByteArray()
        assertContentEquals(expected, bytes)
    }
}
