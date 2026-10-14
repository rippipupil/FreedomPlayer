package com.freedomplayer.ui.theme

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.freedomplayer.resources.Res
import com.freedomplayer.resources.chakrapetch_bold
import com.freedomplayer.resources.chakrapetch_medium
import com.freedomplayer.resources.chakrapetch_semibold
import com.freedomplayer.resources.nunito_bold
import com.freedomplayer.resources.nunito_extrabold
import com.freedomplayer.resources.nunito_regular
import com.freedomplayer.resources.nunito_semibold
import org.jetbrains.compose.resources.Font

/**
 * Paleta «noche neón + fantasmitas» (tablero Tema · Paleta). Los nombres siguen los del diseño:
 * Noche, Panel, Neón, Rosa fantasma, Hueso, Violeta, Jade y Bruma, más los tonos que usan los tableros.
 */
object Fp {
    val Night = Color(0xFF110C1B)
    val Ink = Color(0xFF120E1C)
    val Panel = Color(0xFF1A1329)
    val Card = Color(0xFF181127)
    val Drawer = Color(0xFF161024)
    val Tab = Color(0xFF1D152D)
    val Bar = Color(0xFF150F22)
    val ToastBg = Color(0xFF241A38)
    val Neon = Color(0xFFFF3FA4)
    val NeonSoft = Color(0xFFFF8CC6)
    val Lamp = Color(0xFFFFE3F1)
    val Pink = Color(0xFFF4A6CB)
    val Bone = Color(0xFFF3EEF4)
    val Violet = Color(0xFF5B57D9)
    val Jade = Color(0xFF2F9C82)
    val JadeLight = Color(0xFF8FE3CB)
    val JadeDeep = Color(0xFF173A33)
    val Mist = Color(0xFFB7A9C9)
    val MistDim = Color(0xFF9C8DB0)
    val Idle = Color(0xFFCDBFDF)
    val StarOff = Color(0xFF3A2C52)
    val StarLine = Color(0xFF6E5A8A)
    val LyricPast = Color(0xFF6E5A8A)
    val Scrim = Color(0x9E08050E)
    val Line = Pink.copy(alpha = 0.22f)
    val Track = Pink.copy(alpha = 0.16f)

    /** Colores del número de puesto en «Más escuchadas» (1, 2, 3). */
    val MostPlayedPos = listOf(Color(0xFFFF3FA4), Color(0xFFC9568F), Color(0xFF9A4C7E))

    /** Colores del número de puesto en el ranking general (1, 2, 3 y el resto). */
    val RankPos = listOf(Color(0xFFFF3FA4), Color(0xFFE06AAE), Color(0xFFB7609A), Color(0xFF8C7AA3))

    /** Colores de carátula provisionales (mientras no haya imágenes). */
    val Covers = listOf(
        0xFF7A1F55, 0xFF3B2F8F, 0xFF1E5C50, 0xFF4A2A6B, 0xFF5B2340, 0xFF2A5E3F, 0xFF2B3F5E, 0xFF552243,
    )
}

/** Chakra Petch (títulos, etiquetas numeradas, tiempos y botones) y Nunito (canciones y textos). */
@Immutable
class FpFonts(val chakra: FontFamily, val nunito: FontFamily)

val LocalFpFonts = staticCompositionLocalOf { FpFonts(FontFamily.SansSerif, FontFamily.SansSerif) }

/** true en el PC (ventana ancha): cambia medidas y la disposición de algunas pantallas. */
val LocalWide = staticCompositionLocalOf { false }

@Composable
fun FreedomTheme(wide: Boolean, content: @Composable () -> Unit) {
    val chakra = FontFamily(
        Font(Res.font.chakrapetch_medium, FontWeight.Medium),
        Font(Res.font.chakrapetch_semibold, FontWeight.SemiBold),
        Font(Res.font.chakrapetch_bold, FontWeight.Bold),
    )
    val nunito = FontFamily(
        Font(Res.font.nunito_regular, FontWeight.Normal),
        Font(Res.font.nunito_semibold, FontWeight.SemiBold),
        Font(Res.font.nunito_bold, FontWeight.Bold),
        Font(Res.font.nunito_extrabold, FontWeight.ExtraBold),
    )
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Fp.Neon,
            onPrimary = Fp.Ink,
            secondary = Fp.Violet,
            tertiary = Fp.Jade,
            background = Fp.Night,
            onBackground = Fp.Bone,
            surface = Fp.Panel,
            onSurface = Fp.Bone,
        ),
    ) {
        CompositionLocalProvider(
            LocalFpFonts provides FpFonts(chakra, nunito),
            LocalWide provides wide,
            LocalTextStyle provides TextStyle(fontFamily = nunito, color = Fp.Bone, fontSize = 14.sp),
            LocalTextSelectionColors provides TextSelectionColors(
                handleColor = Fp.Neon,
                backgroundColor = Fp.Neon.copy(alpha = 0.35f),
            ),
            content = content,
        )
    }
}
