package com.freedomplayer.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import java.io.IOException

/** Guardar imágenes de la app (las tarjetas del Wrapped) como PNG en el PC. */
object ImageFiles {
    /** Imágenes › FreedomPlayer (en Windows, «Imágenes» es la carpeta Pictures del usuario). */
    fun picturesDir(): File = File(File(System.getProperty("user.home"), "Pictures"), "FreedomPlayer")

    /** Guarda [image] como PNG en [dir] con el nombre [name] (si ya existe, con «-2», «-3»…). */
    fun savePng(image: ImageBitmap, dir: File, name: String): File {
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("No se puede crear $dir")
        val bytes = Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes
            ?: throw IOException("No se pudo pasar la imagen a PNG")
        val base = name.removeSuffix(".png")
        var file = File(dir, "$base.png")
        var n = 2
        while (file.exists()) file = File(dir, "$base-${n++}.png")
        file.writeBytes(bytes)
        return file
    }
}
