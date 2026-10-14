package com.freedomplayer.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freedomplayer.ui.theme.Fp
import com.freedomplayer.ui.theme.LocalFpFonts
import com.freedomplayer.ui.theme.LocalWide

/** Esquinas cortadas en diagonal (arriba a la izquierda y abajo a la derecha): la seña del diseño. */
fun cut(size: Dp): Shape = CutCornerShape(topStart = size, bottomEnd = size)

/**
 * Márgenes de cada pantalla, que pone el armazón: arriba (barra de estado), abajo
 * (mini-reproductor y barra de navegación) y, en el PC, a los lados (96 dp como en el diseño,
 * menos si la ventana es estrecha).
 */
@Immutable
data class ScreenInsets(val top: Dp, val bottom: Dp, val wideSide: Dp = 96.dp)

val LocalScreenInsets = staticCompositionLocalOf { ScreenInsets(top = 44.dp, bottom = 28.dp) }

// ---------- Texto ----------

/** Chakra Petch: títulos, etiquetas en mayúsculas, números, tiempos y botones. */
@Composable
fun Chakra(
    text: String,
    size: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = Fp.Bone,
    weight: FontWeight = FontWeight.SemiBold,
    spacing: TextUnit = 0.sp,
    align: TextAlign? = null,
    maxLines: Int = 1,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = size,
        fontWeight = weight,
        fontFamily = LocalFpFonts.current.chakra,
        letterSpacing = spacing,
        textAlign = align,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Nunito: nombres de canciones y textos. */
@Composable
fun Nunito(
    text: String,
    size: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = Fp.Bone,
    weight: FontWeight = FontWeight.Normal,
    maxLines: Int = Int.MAX_VALUE,
    lineHeight: TextUnit = TextUnit.Unspecified,
    align: TextAlign? = null,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = size,
        fontWeight = weight,
        fontFamily = LocalFpFonts.current.nunito,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        lineHeight = lineHeight,
        textAlign = align,
    )
}

@Composable
fun Nunito(
    text: AnnotatedString,
    size: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = Fp.Bone,
    weight: FontWeight = FontWeight.Normal,
    maxLines: Int = 1,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = size,
        fontWeight = weight,
        fontFamily = LocalFpFonts.current.nunito,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Título de canción: Nunito 14 extranegrita, en una línea. */
@Composable
fun SongTitle(text: String, modifier: Modifier = Modifier, color: Color = Fp.Bone, size: TextUnit = 14.sp) {
    Nunito(text, size, modifier, color = color, weight = FontWeight.ExtraBold, maxLines = 1)
}

/** Línea secundaria: Nunito 12 en Bruma. */
@Composable
fun SubText(text: String, modifier: Modifier = Modifier, color: Color = Fp.Mist, size: TextUnit = 12.sp) {
    Nunito(text, size, modifier, color = color, maxLines = 1)
}

/**
 * Cabecera de pantalla: etiqueta pequeña (Chakra 11, espaciado 3, Bruma) encima del título
 * (Chakra 32, negrita). En el PC miden 12 y 40.
 */
@Composable
fun ScreenHeader(
    label: String,
    title: String,
    modifier: Modifier = Modifier,
    sparkle: Boolean = false,
    labelColor: Color = Fp.Mist,
) {
    val wide = LocalWide.current
    Column(modifier) {
        Chakra(label, if (wide) 12.sp else 11.sp, color = labelColor, spacing = 3.sp)
        Row(
            Modifier.padding(top = 2.dp).semantics { heading() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Chakra(title, if (wide) 40.sp else 32.sp, weight = FontWeight.Bold, spacing = 0.5.sp)
            if (sparkle) FpIconView(FpIcons.Sparkle, if (wide) 18.dp else 16.dp, Fp.Neon)
        }
    }
}

/** Sección numerada («01 RECIENTES»): número en Neón, nombre en Rosa fantasma y una línea fina. */
@Composable
fun SectionHeader(
    number: String,
    title: String,
    modifier: Modifier = Modifier,
    line: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val size = if (LocalWide.current) 12.sp else 11.sp
    Row(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Chakra(number, size, color = Fp.Neon, spacing = 3.sp)
        Chakra(title, size, color = Fp.Pink, spacing = 3.sp)
        if (line) Box(Modifier.weight(1f).height(1.dp).background(Fp.Line)) else Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** Nota con raya violeta a la izquierda («YouTube normal: …», «Toca la mitad…»). */
@Composable
fun HintNote(text: String, modifier: Modifier = Modifier, barColor: Color = Fp.Violet) {
    Nunito(
        text,
        12.sp,
        modifier
            .drawBehind { drawRect(barColor, size = Size(2.dp.toPx(), this.size.height)) }
            .padding(start = 12.dp),
        color = Fp.Mist,
        lineHeight = 18.sp,
    )
}

// ---------- Carátulas ----------

/** Carátula pequeña: color liso con esquinas un poco redondeadas (3 px). */
@Composable
fun Cover(color: Long, size: Dp, modifier: Modifier = Modifier) {
    val c by animateColorAsState(Color(color), fpTween(Motion.SLOW), label = "cover")
    Box(modifier.size(size).clip(RoundedCornerShape(3.dp)).background(c))
}

/**
 * Dos carátulas apiladas (playlists y álbumes): la de atrás, más pequeña y transparente,
 * asoma arriba a la derecha.
 */
@Composable
fun StackedCover(color: Long, front: Dp, back: Dp, backX: Dp, frontY: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(maxOf(front, backX + back), frontY + front)) {
        Box(
            Modifier.offset(x = backX, y = 0.dp).size(back).clip(RoundedCornerShape(3.dp))
                .background(Color(color).copy(alpha = 0.45f)),
        )
        Box(
            Modifier.offset(x = 0.dp, y = frontY).size(front)
                .shadow(6.dp, RoundedCornerShape(3.dp), ambientColor = Color.Black, spotColor = Color.Black)
                .clip(RoundedCornerShape(3.dp)).background(Color(color)),
        )
    }
}

// ---------- Botones ----------

/**
 * Botón principal: relleno Neón, texto oscuro y esquinas cortadas. La zona táctil mide al menos
 * 44 dp aunque el botón se vea más bajo. Con [fill] el relleno ocupa todo el ancho que se le dé.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fill: Boolean = false,
    height: Dp = 46.dp,
    cutSize: Dp = 12.dp,
    fontSize: TextUnit = 13.sp,
    spacing: TextUnit = 2.sp,
    enabled: Boolean = true,
    horizontalPadding: Dp = 18.dp,
    icon: FpIcon? = null,
) {
    val bg by animateColorAsState(if (enabled) Fp.Neon else Fp.Neon.copy(alpha = 0.35f), fpTween(), label = "primaryBg")
    Box(
        modifier.heightIn(min = maxOf(height, 44.dp)).fpClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .then(if (fill) Modifier.fillMaxWidth() else Modifier)
                .height(height)
                .background(bg, cut(cutSize))
                .padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            if (icon != null) FpIconView(icon, 14.dp, Fp.Ink)
            Chakra(text, fontSize, color = Fp.Ink, weight = FontWeight.Bold, spacing = spacing)
        }
    }
}

/** Botón secundario: texto Jade entre dos rayas Jade. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 46.dp,
    fontSize: TextUnit = 13.sp,
    color: Color = Fp.JadeLight,
    lineColor: Color = Fp.Jade.copy(alpha = 0.6f),
    enabled: Boolean = true,
) {
    Box(
        modifier
            .height(maxOf(height, 44.dp))
            .fpClickable(enabled = enabled, onClick = onClick)
            .drawBehind {
                val px = 1.dp.toPx()
                drawRect(lineColor, size = Size(this.size.width, px))
                drawRect(lineColor, topLeft = Offset(0f, this.size.height - px), size = Size(this.size.width, px))
            }
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Chakra(text, fontSize, color = if (enabled) color else color.copy(alpha = 0.4f), spacing = 2.sp)
    }
}

/** Enlace de texto en Chakra (VER TODO →, CANCELAR…), con zona táctil de 44 dp. */
@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Fp.Neon,
    size: TextUnit = 12.sp,
    spacing: TextUnit = 1.sp,
) {
    Box(
        modifier.heightIn(min = 44.dp).fpClickable(onClick = onClick).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Chakra(text, size, color = color, spacing = spacing)
    }
}

/** Botón de icono de 44 dp (o más) sin fondo. */
@Composable
fun IconButton(
    icon: FpIcon,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 20.dp,
    color: Color = Fp.Idle,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val c by animateColorAsState(if (enabled) color else color.copy(alpha = 0.35f), fpTween(), label = "iconColor")
    Box(
        modifier
            .size(size)
            .fpClickable(onClickLabel = null, enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        FpIconView(icon, iconSize, c)
        content()
    }
}

/** Estado del botón «+» de una fila del buscador. */
sealed interface AddState {
    data object Idle : AddState
    data class Downloading(val fraction: Float) : AddState
    data object Done : AddState
}

/**
 * El «+» de 44 px con borde rosado. Mientras descarga se va llenando de abajo arriba
 * con el porcentaje; al terminar es un ✓ en jade.
 */
@Composable
fun AddButton(state: AddState, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val done = state is AddState.Done
    val edge by animateColorAsState(if (done) Fp.Jade else Fp.Pink.copy(alpha = 0.5f), fpTween(), label = "addEdge")
    val bg by animateColorAsState(if (done) Fp.JadeDeep else Fp.Panel, fpTween(), label = "addBg")
    val shape = cut(10.dp)
    Box(
        modifier
            .size(44.dp)
            .fpClickable(enabled = enabled && state is AddState.Idle, onClick = onClick)
            .semantics {
                contentDescription = when (state) {
                    is AddState.Idle -> label
                    is AddState.Downloading -> "Descargando, ${(state.fraction * 100).toInt()} %"
                    is AddState.Done -> "Añadida"
                }
            }
            .background(bg, shape)
            .border(1.dp, edge, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (state is AddState.Downloading) {
            val fill by androidx.compose.animation.core.animateFloatAsState(state.fraction, fpTween(Motion.QUICK), label = "addFill")
            Box(
                Modifier.matchParentSize().clip(shape).drawBehind {
                    val h = this.size.height * fill
                    drawRect(Fp.Jade.copy(alpha = 0.28f), topLeft = Offset(0f, this.size.height - h), size = Size(this.size.width, h))
                },
            )
        }
        AnimatedContent(
            targetState = when (state) {
                is AddState.Idle -> 0
                is AddState.Downloading -> 1
                is AddState.Done -> 2
            },
            transitionSpec = {
                (fadeIn(tween(Motion.STANDARD)) + scaleIn(tween(Motion.STANDARD), initialScale = 0.6f)) togetherWith
                    (fadeOut(tween(Motion.QUICK)) + scaleOut(tween(Motion.QUICK), targetScale = 0.6f))
            },
            label = "addIcon",
        ) { s ->
            when (s) {
                0 -> FpIconView(FpIcons.Plus, 18.dp, if (enabled) Fp.Pink else Fp.Pink.copy(alpha = 0.35f))
                1 -> Chakra(
                    "${((state as? AddState.Downloading)?.fraction?.times(100))?.toInt() ?: 0}%",
                    10.sp,
                    color = Fp.JadeLight,
                    weight = FontWeight.Bold,
                )
                else -> FpIconView(FpIcons.Check, 18.dp, Fp.JadeLight)
            }
        }
    }
}

/** Etiqueta «EN BIBLIO» (ya la tienes). */
@Composable
fun InLibraryBadge(modifier: Modifier = Modifier, text: String = "EN BIBLIO") {
    Box(
        modifier
            .drawBehind { drawRect(Fp.Jade, size = Size(2.dp.toPx(), this.size.height)) }
            .background(Fp.Jade.copy(alpha = 0.14f))
            .padding(start = 10.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Chakra(text, 10.sp, color = Fp.JadeLight, spacing = 1.5.sp)
    }
}

/** Insignia de sincronización («SYNC · PC» / «SYNC · MÓVIL») con el fantasma UwU. */
@Composable
fun SyncBadge(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val wide = LocalWide.current
    Box(modifier.heightIn(min = 44.dp).fpClickable(onClickLabel = "Sincronización", onClick = onClick), contentAlignment = Alignment.TopEnd) {
        Row(
            Modifier
                .drawBehind { drawRect(Fp.Jade, size = Size(2.dp.toPx(), this.size.height)) }
                .background(Fp.Jade.copy(alpha = 0.16f))
                .padding(start = if (wide) 6.dp else 4.dp, end = if (wide) 12.dp else 10.dp, top = if (wide) 5.dp else 4.dp, bottom = if (wide) 5.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (wide) 8.dp else 6.dp),
        ) {
            Ghost(Mood.Uwu, if (wide) 22.dp else 20.dp)
            Chakra(text, if (wide) 12.sp else 11.sp, color = Fp.JadeLight, spacing = 1.5.sp)
        }
    }
}

// ---------- Pestañas ----------

/**
 * Pestañas en Chakra con raya rosa debajo de la activa. La raya se desliza a la pestaña nueva.
 * [badges]: número rosa junto al nombre (Sin nota); se oculta en 0.
 */
@Composable
fun FpTabs(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    badges: List<Int> = emptyList(),
    tabPadding: Dp = 14.dp,
    underlineInset: Dp = 10.dp,
    spacing: TextUnit = 2.sp,
) {
    val density = LocalDensity.current
    val positions = remember { mutableStateMapOf<Int, Pair<Float, Float>>() }
    Box(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val px = 1.dp.toPx()
                drawRect(Fp.Pink.copy(alpha = 0.2f), topLeft = Offset(0f, this.size.height - px), size = Size(this.size.width, px))
            },
    ) {
        Row {
            labels.forEachIndexed { index, label ->
                val on = index == selected
                val color by animateColorAsState(if (on) Fp.Neon else Fp.Idle, fpTween(), label = "tabColor")
                Row(
                    Modifier
                        .height(44.dp)
                        .onPlaced { coords ->
                            val x = coords.parentLayoutCoordinates?.let { p -> p.localPositionOf(coords, Offset.Zero).x } ?: 0f
                            positions[index] = x to coords.size.width.toFloat()
                        }
                        .fpClickable(role = Role.Tab, pressedScale = 0.97f) { onSelect(index) }
                        .semantics { this.selected = on }
                        .padding(horizontal = tabPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Chakra(label, 12.sp, color = color, spacing = spacing)
                    val badge = badges.getOrNull(index) ?: 0
                    AnimatedVisibility(visible = badge > 0, enter = fadeIn(tween(Motion.STANDARD)) + scaleIn(tween(Motion.STANDARD)), exit = fadeOut(tween(Motion.QUICK)) + scaleOut(tween(Motion.QUICK))) {
                        CountBadge(badge)
                    }
                }
            }
        }
        val pos = positions[selected]
        if (pos != null) {
            val inset = with(density) { underlineInset.toPx() }
            val x by androidx.compose.animation.core.animateFloatAsState(pos.first + inset, fpTween(Motion.SLOW), label = "tabX")
            val w by androidx.compose.animation.core.animateFloatAsState(pos.second - inset * 2, fpTween(Motion.SLOW), label = "tabW")
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .offset { androidx.compose.ui.unit.IntOffset(x.toInt(), 0) }
                    .width(with(density) { w.coerceAtLeast(0f).toDp() })
                    .height(2.dp)
                    .background(Fp.Neon),
            )
        }
    }
}

/** Insignia rosa con un número (pendientes de Sin nota). */
@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier.heightIn(min = 18.dp).widthIn(min = 18.dp).background(Fp.Neon, cut(4.dp)).padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Chakra(count.toString(), 11.sp, color = Fp.Ink, weight = FontWeight.Bold)
    }
}

// ---------- Campos de texto ----------

/**
 * Campo de texto sin adornos de Material: [leading] a la izquierda, el texto y, si hay algo
 * escrito, un botón para borrarlo.
 */
@Composable
fun FpTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    label: String,
    modifier: Modifier = Modifier,
    textSize: TextUnit = 14.sp,
    bold: Boolean = false,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Done,
    onSubmit: () -> Unit = {},
    leading: (@Composable () -> Unit)? = null,
    clearable: Boolean = true,
    placeholderColor: Color = Fp.Mist,
) {
    val fonts = LocalFpFonts.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.semantics { contentDescription = label },
        enabled = enabled,
        singleLine = true,
        textStyle = TextStyle(
            color = Fp.Bone,
            fontSize = textSize,
            fontFamily = fonts.nunito,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        ),
        cursorBrush = SolidColor(Fp.Neon),
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }, onSearch = { onSubmit() }, onGo = { onSubmit() }),
        decorationBox = { inner ->
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                leading?.invoke()
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Nunito(placeholder, textSize, color = placeholderColor, maxLines = 1, modifier = Modifier.clearAndSetSemantics { })
                    }
                    inner()
                }
                AnimatedVisibility(clearable && value.isNotEmpty(), enter = fadeIn(tween(Motion.QUICK)), exit = fadeOut(tween(Motion.QUICK))) {
                    IconButton(FpIcons.Close, "Borrar", onClick = { onValueChange("") }, size = 44.dp, iconSize = 14.dp, color = Fp.Mist)
                }
            }
        },
    )
}

/** Buscador de 48 px con borde Neón y esquinas cortadas. */
@Composable
fun SearchBox(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Canciones, álbumes o vídeos",
    label: String = "Buscar canciones, álbumes o vídeos",
    enabled: Boolean = true,
    icon: FpIcon = FpIcons.SearchField,
    iconColor: Color = Fp.Pink,
    borderColor: Color = Fp.Neon,
    imeAction: ImeAction = ImeAction.Search,
    onSubmit: () -> Unit = {},
) {
    val shape = cut(12.dp)
    val border by animateColorAsState(if (enabled) borderColor else borderColor.copy(alpha = 0.4f), fpTween(), label = "searchBorder")
    FpTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        label = label,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(Fp.Panel, shape)
            .border(1.dp, border, shape)
            .padding(start = 14.dp, end = 4.dp),
        textSize = 16.sp,
        bold = true,
        enabled = enabled,
        imeAction = imeAction,
        onSubmit = onSubmit,
        leading = { FpIconView(icon, 18.dp, iconColor) },
    )
}

/** Campo con solo una línea abajo (filtro de los paneles, nombres). */
@Composable
fun UnderlineField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    label: String,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp,
    leadingIcon: FpIcon? = FpIcons.Filter,
    imeAction: ImeAction = ImeAction.Done,
    onSubmit: () -> Unit = {},
    lineColor: Color = Fp.Pink.copy(alpha = 0.35f),
) {
    FpTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        label = label,
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .drawBehind {
                val px = 1.dp.toPx()
                drawRect(lineColor, topLeft = Offset(0f, this.size.height - px), size = Size(this.size.width, px))
            },
        imeAction = imeAction,
        onSubmit = onSubmit,
        leading = leadingIcon?.let { icon -> { FpIconView(icon, 16.dp, Fp.Mist) } },
    )
}

// ---------- Interruptor ----------

/** Interruptor con esquinas cortadas; la fila entera es la zona táctil. */
@Composable
fun FpSwitch(on: Boolean, modifier: Modifier = Modifier) {
    val knobX by animateDpAsState(if (on) 20.dp else 2.dp, fpTween(Motion.QUICK), label = "switchKnob")
    val track by animateColorAsState(if (on) Fp.Neon.copy(alpha = 0.28f) else Color.Transparent, fpTween(Motion.QUICK), label = "switchTrack")
    val edge by animateColorAsState(if (on) Fp.Neon else Fp.Pink.copy(alpha = 0.35f), fpTween(Motion.QUICK), label = "switchEdge")
    val knob by animateColorAsState(if (on) Fp.Neon else Fp.Mist, fpTween(Motion.QUICK), label = "switchKnobColor")
    val shape = cut(6.dp)
    Box(modifier.size(40.dp, 22.dp).background(track, shape).border(1.dp, edge, shape)) {
        Box(Modifier.offset(x = knobX, y = 3.dp).size(16.dp).background(knob, cut(4.dp)))
    }
}

/** Fila de ajuste con interruptor: título, explicación y el interruptor a la derecha. */
@Composable
fun SwitchRow(title: String, description: String?, on: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .pointerHoverIcon(PointerIcon.Hand)
            .toggleable(value = on, interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Nunito(title, 14.sp, weight = FontWeight.ExtraBold)
            if (description != null) Nunito(description, 12.sp, color = Fp.Mist, lineHeight = 17.sp)
        }
        FpSwitch(on)
    }
}

// ---------- Barras de segmentos (estáticas) ----------

/** Barra de [count] segmentos con [filled] encendidos (volumen, sincronización…). */
@Composable
fun SegmentBar(
    count: Int,
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = Fp.Neon,
    offColor: Color = Fp.Track,
    height: Dp = 4.dp,
    gap: Dp = 2.dp,
) {
    val f by androidx.compose.animation.core.animateFloatAsState(fraction.coerceIn(0f, 1f), fpTween(Motion.SLOW), label = "segments")
    androidx.compose.foundation.Canvas(modifier.fillMaxWidth().height(height)) {
        val g = gap.toPx()
        val w = (this.size.width - g * (count - 1)) / count
        for (i in 0 until count) {
            val on = (i + 0.5f) / count <= f
            drawRect(if (on) color else offColor, topLeft = Offset(i * (w + g), 0f), size = Size(w, this.size.height))
        }
    }
}

/** Recuadro de línea discontinua (Biblioteca vacía y demás estados que llenan la pantalla). */
fun Modifier.dashedBorder(color: Color = Fp.Pink.copy(alpha = 0.3f)): Modifier = this.drawBehind {
    val px = 1.dp.toPx()
    drawRect(
        color,
        topLeft = Offset(px / 2, px / 2),
        size = Size(this.size.width - px, this.size.height - px),
        style = Stroke(width = px, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
    )
}

/** Fila con raya a la izquierda y fondo suave (la que suena, el n.º 1, la recién puntuada). */
fun Modifier.highlight(edge: Color, background: Color): Modifier = this
    .background(background)
    .drawBehind { drawRect(edge, size = Size(2.dp.toPx(), this.size.height)) }

/** Contenido centrado con un ancho máximo (pantallas del PC). */
@Composable
fun Centered(maxWidth: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = maxWidth).fillMaxWidth().fillMaxHeight()) { content() }
    }
}

/**
 * Relleno de una pantalla: arriba y abajo el que da el armazón y a los lados el del diseño
 * ([mobile] en el móvil; en el PC, el de [ScreenInsets.wideSide]).
 */
@Composable
fun screenPadding(mobile: Dp = 34.dp): PaddingValues {
    val wide = LocalWide.current
    val insets = LocalScreenInsets.current
    val side = if (wide) insets.wideSide else mobile
    return PaddingValues(start = side, end = side, top = insets.top, bottom = insets.bottom)
}
