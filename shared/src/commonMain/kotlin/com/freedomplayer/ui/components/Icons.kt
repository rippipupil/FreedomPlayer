package com.freedomplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp

/** Convierte un trazado SVG («M4 11 L12 4 …») en un Path de Compose. */
internal fun svgPath(d: String): Path = PathParser().parsePathString(d).toPath()

/**
 * Un icono del diseño, en una caja de 24×24 como los SVG originales:
 * una parte con trazo (línea de [strokeWidth]) y otra rellena.
 */
class FpIcon(
    private val stroke: String?,
    private val fill: String? = null,
    val strokeWidth: Float = 1.8f,
    val cap: StrokeCap = StrokeCap.Square,
    val join: StrokeJoin = StrokeJoin.Miter,
) {
    val strokePath: Path? by lazy { stroke?.let(::svgPath) }
    val fillPath: Path? by lazy { fill?.let(::svgPath) }
}

/** Dibuja [icon] ocupando el lienzo (caja de 24×24 escalada). */
fun DrawScope.drawFpIcon(icon: FpIcon, color: Color, viewBox: Float = 24f) {
    val k = this.size.minDimension / viewBox
    scale(k, k, pivot = Offset.Zero) {
        icon.fillPath?.let { drawPath(it, color) }
        icon.strokePath?.let {
            drawPath(it, color, style = Stroke(width = icon.strokeWidth, cap = icon.cap, join = icon.join))
        }
    }
}

/** Un icono de [size] dp en [color]. Es decorativo: la etiqueta la lleva el botón que lo contiene. */
@Composable
fun FpIconView(icon: FpIcon, size: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawFpIcon(icon, color) }
}

/** Los iconos de los tableros (trazados copiados de los SVG del diseño). */
object FpIcons {
    // Menú de los paneles
    val Home = FpIcon("M4 11 L12 4 L20 11 V20 H14 V14 H10 V20 H4 Z")
    val Search = FpIcon("M10.5 4.5 A6 6 0 1 1 10.5 16.5 A6 6 0 1 1 10.5 4.5 Z M15 15 L20 20")
    val Ranking = FpIcon("M12 2 L14 10 L22 12 L14 14 L12 22 L10 14 L2 12 L10 10 Z")
    val Stats = FpIcon("M5 20 V12 M12 20 V5 M19 20 V9")
    val Settings = FpIcon("M4 7 H13 M19 7 H20 M16 4.5 V9.5 M4 17 H5 M11 17 H20 M8 14.5 V19.5")

    // Pestañas laterales
    val Note = FpIcon("M11 16 V3 L19 7 V11", "M4 16 L11 14 L11.5 19 L4.5 21 Z", strokeWidth = 2f)
    val NoteBold = FpIcon("M11 16 V3 L19 7 V11", "M4 16 L11 14 L11.5 19 L4.5 21 Z", strokeWidth = 2.4f)
    val Shelf = FpIcon("M4 3 V21 M20 3 V21 M4 12 H20 M4 21 H20 M8 12 V7 M11 12 V6 M14 12 V8 M16 12 L18 8 M8 21 V16 M11 21 V15.5")
    val ShelfPc = FpIcon("M4 3 V21 M20 3 V21 M4 12 H20 M4 21 H20 M8 12 V7 M11 12 V6 M14 12 V8 M8 21 V16 M11 21 V15.5", strokeWidth = 2f)

    // Lupas: la del filtro de los paneles y la del buscador
    val Filter = FpIcon("M5 11 A6 6 0 1 1 17 11 A6 6 0 1 1 5 11 Z M16 16 L20 20", strokeWidth = 2f)
    val SearchField = FpIcon("M4.5 10.5 A6 6 0 1 1 16.5 10.5 A6 6 0 1 1 4.5 10.5 Z M15 15 L20 20", strokeWidth = 2f)

    // Reproducción
    val Play = FpIcon(null, "M7 4 L20 12 L7 20 Z")
    val Pause = FpIcon(null, "M6 5 H10 V19 H6 Z M14 5 H18 V19 H14 Z")
    val NextMini = FpIcon(null, "M6 5 L15 12 L6 19 Z M17 5 H19.6 V19 H17 Z")
    val Next = FpIcon(null, "M6 5 L15 12 L6 19 Z M16.4 5 H19 V19 H16.4 Z")
    val Previous = FpIcon(null, "M18 5 L9 12 L18 19 Z M5 5 H7.6 V19 H5 Z")
    val Shuffle = FpIcon("M3 7 H7 L15 17 H21 M3 17 H7 L9.5 14 M13 10 L15 7 H21 M18 4 L21 7 L18 10 M18 14 L21 17 L18 20")
    val Repeat = FpIcon("M4 11 V7 H18 M15 4 L18 7 L15 10 M20 13 V17 H6 M9 20 L6 17 L9 14")
    val ChevronDown = FpIcon("M6 9 L12 15 L18 9", strokeWidth = 2f)
    val Back = FpIcon("M15 5 L8 12 L15 19", strokeWidth = 2f)
    val More = FpIcon(null, "M4 11 H7 V14 H4 Z M10.5 11 H13.5 V14 H10.5 Z M17 11 H20 V14 H17 Z")
    val Equalizer = FpIcon("M6 4 V20 M12 4 V20 M18 4 V20 M4 14 H8 M10 8 H14 M16 16 H20")
    val Moon = FpIcon("M20 14.5 A8 8 0 1 1 9.5 4 A6.5 6.5 0 0 0 20 14.5 Z")
    val Pencil = FpIcon("M4 20 H8 L19 9 L15 5 L4 16 Z M13 7 L17 11")
    val Queue = FpIcon("M4 6 H20 M4 12 H14 M4 18 H11 M17 15 V21 L21 18 Z")
    val Lyrics = FpIcon("M4 5 H20 V16 H11 L6 20 V16 H4 Z M8 9 H16 M8 12 H13")
    val Volume = FpIcon("M4 9 H8 L13 5 V19 L8 15 H4 Z M16.5 9 Q18.5 12 16.5 15")
    val Mute = FpIcon("M4 9 H8 L13 5 V19 L8 15 H4 Z M16 9.5 L21 14.5 M21 9.5 L16 14.5")

    // Añadir y listas
    val Plus = FpIcon("M12 5 V19 M5 12 H19", strokeWidth = 2.4f)
    val Check = FpIcon("M5 12.5 L10 17 L19 7", strokeWidth = 2.6f)
    val Close = FpIcon("M6 6 L18 18 M18 6 L6 18", strokeWidth = 2f)
    val Download = FpIcon("M12 4 V15 M7 10 L12 15 L17 10 M5 20 H19", strokeWidth = 2f)
    val Folder = FpIcon("M3 5.5 H9.5 L11.5 8 H21 V19 H3 Z M3 11 H21")
    val Link = FpIcon(
        "M10 14 L14 10 M8.5 11.5 L6.5 13.5 A3.5 3.5 0 0 0 11.5 18.5 L13.5 16.5 " +
            "M15.5 12.5 L17.5 10.5 A3.5 3.5 0 0 0 12.5 5.5 L10.5 7.5",
        strokeWidth = 2f,
    )
    val Handle = FpIcon("M5 8 H19 M5 12 H19 M5 16 H19", strokeWidth = 2f)
    val Sparkle = FpIcon(null, "M12 1 L14.2 9.8 L23 12 L14.2 14.2 L12 23 L9.8 14.2 L1 12 L9.8 9.8 Z")

    // Barra de título del PC
    val MiniWindow = FpIcon("M3 5 H21 V19 H3 Z M12 12 H19 V17 H12 Z", "M12 12 H19 V17 H12 Z")
    val Minimize = FpIcon("M4 12 H20", strokeWidth = 2f)
    val Maximize = FpIcon("M5 5 H19 V19 H5 Z", strokeWidth = 2f)
    val Restore = FpIcon("M8 8 H19 V19 H8 Z M5 15 V5 H15", strokeWidth = 2f)
    val CloseWindow = FpIcon("M5 5 L19 19 M19 5 L5 19", strokeWidth = 2f)
}
