package com.freedomplayer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import okio.Path.Companion.toPath

/**
 * Qué se le pasa a Coil para una carátula: las direcciones web tal cual y los archivos del
 * dispositivo como ruta (así funcionan también las de Windows, «C:\…»). null si no hay imagen.
 */
fun coverModel(cover: String?): Any? = when {
    cover.isNullOrBlank() -> null
    cover.startsWith("http://") || cover.startsWith("https://") -> cover
    cover.startsWith("content:") || cover.startsWith("file:") -> cover
    cover.startsWith("demo:") -> null
    else -> cover.toPath()
}

/**
 * La imagen de una carátula, encima de su color (que se ve mientras carga o si no hay imagen).
 * Entra con un fundido corto (200 ms); sin fundido si se reduce el movimiento.
 */
@Composable
fun CoverImage(cover: String?, modifier: Modifier = Modifier) {
    val model = remember(cover) { coverModel(cover) } ?: return
    val context = LocalPlatformContext.current
    val reduce = LocalReduceMotion.current
    val request = remember(model, reduce) {
        ImageRequest.Builder(context).data(model).crossfade(!reduce).build()
    }
    AsyncImage(model = request, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
}
