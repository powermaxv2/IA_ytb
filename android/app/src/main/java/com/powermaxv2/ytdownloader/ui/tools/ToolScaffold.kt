package com.powermaxv2.ytdownloader.ui.tools

import android.content.ActivityNotFoundException
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.brand.GulpLockup
import com.powermaxv2.ytdownloader.ui.brand.GulpMark
import com.powermaxv2.ytdownloader.ui.brand.GulpStyle
import com.powermaxv2.ytdownloader.ui.hub.HomeLink
import com.powermaxv2.ytdownloader.ui.hub.bob
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.skin.paper.paperCard
import com.powermaxv2.ytdownloader.ui.skin.terminal.blinking
import com.powermaxv2.ytdownloader.ui.skin.terminal.rememberTyped
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Shared frame of the file tools (Convertisseur, Aperçu…): the top bar that leads home, a scrolling column inside
// the system bars, and the big "Choisir un fichier" drop zone. Each part is drawn natively in the three skins.

/**
 * A tool screen: [title] in the skin's top bar (the Gulp logo goes back home through [MainViewModel.goHome]),
 * then [content] in a scrolling column, 16dp apart. [command] is the Terminal prompt word: `$ gulp <command>`.
 */
@Composable
fun ToolScaffold(
    vm: MainViewModel,
    title: String,
    command: String,
    subtitle: String? = null,
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val insets = WindowInsets.systemBars.asPaddingValues()
    ToolScaffold(
        title = title,
        command = command,
        subtitle = subtitle,
        onHome = vm::goHome,
        contentPadding = PaddingValues(
            top = insets.calculateTopPadding() + 16.dp,
            bottom = insets.calculateBottomPadding() + 32.dp,
        ),
        scrollState = scrollState,
        content = content,
    )
}

/** Stateless form of [ToolScaffold] (also used by the screenshot tests); [onHome] null = logo without the back chevron. */
@Composable
fun ToolScaffold(
    title: String,
    command: String,
    subtitle: String?,
    onHome: (() -> Unit)?,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(contentPadding)
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ToolTopBar(title, command, subtitle, onHome)
        content()
    }
}

/** Top of a tool screen in the current skin. */
@Composable
fun ToolTopBar(title: String, command: String, subtitle: String?, onHome: (() -> Unit)?) {
    when (LocalSkin.current) {
        Skin.CLASSIC -> ClassicToolTopBar(title, subtitle, onHome)
        Skin.PAPER -> PaperToolTopBar(title, subtitle, onHome)
        Skin.TERMINAL -> TerminalToolTopBar(title, command, subtitle, onHome)
    }
}

/** Gulp: lockup, then the title in the brand gradient springing up from below the line. */
@Composable
private fun ClassicToolTopBar(title: String, subtitle: String?, onHome: (() -> Unit)?) {
    val rise = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(60)
        rise.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 260f))
    }
    Column(Modifier.padding(top = 4.dp)) {
        HomeLink(onHome, Palette.TextSecondary) { GulpLockup(markSize = 30.dp, textColor = Palette.TextPrimary) }
        Spacer(Modifier.height(18.dp))
        Text(
            title,
            color = Color.Unspecified,
            style = TextStyle(
                fontSize = 38.sp,
                lineHeight = 42.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = LocalTokens.current.display,
                letterSpacing = (-1).sp,
                brush = LocalAccent.current.horizontal(),
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.graphicsLayer {
                val p = rise.value
                alpha = p.coerceIn(0f, 1f)
                translationY = (1f - p) * 26.dp.toPx()
                rotationX = (1f - p) * 50f
            },
        )
        subtitle?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                it,
                color = Palette.TextSecondary,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                modifier = Modifier.staggeredEntrance(1, baseDelay = 180),
            )
        }
    }
}

/** Papier: lockup, a thick ink rule drawn across, the magazine headline, a hairline, the standfirst. */
@Composable
private fun PaperToolTopBar(title: String, subtitle: String?, onHome: (() -> Unit)?) {
    val ink = Palette.TextPrimary
    val accent = LocalAccent.current.start
    val draw = remember { Animatable(0f) }
    LaunchedEffect(Unit) { draw.animateTo(1f, tween(700, delayMillis = 80, easing = FastOutSlowInEasing)) }
    val rise = remember { Animatable(0f) }
    LaunchedEffect(Unit) { rise.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 220f)) }
    Column(Modifier.padding(top = 4.dp)) {
        HomeLink(onHome, ink, chevron = 20.dp) { GulpLockup(markSize = 26.dp, textColor = ink, fontSize = 21.sp) }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .drawBehind { drawRect(ink, size = Size(size.width * draw.value, size.height)) },
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "OUTIL",
                color = accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
                modifier = Modifier.graphicsLayer { alpha = draw.value },
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            title,
            color = ink,
            fontSize = 44.sp,
            lineHeight = 48.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = LocalTokens.current.display,
            letterSpacing = (-1).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.graphicsLayer {
                alpha = 0.3f + 0.7f * rise.value.coerceIn(0f, 1f)
                translationY = (1f - rise.value) * 8.dp.toPx()
            },
        )
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .drawBehind { drawRect(ink.copy(alpha = 0.25f), size = Size(size.width * draw.value, size.height)) },
        )
        subtitle?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                it,
                color = Palette.TextSecondary,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                modifier = Modifier.staggeredEntrance(1, baseDelay = 220),
            )
        }
    }
}

/** Terminal: outlined mark, the `$ gulp <command>` prompt typed in with a blinking block, the title in Archivo Black. */
@Composable
private fun TerminalToolTopBar(title: String, command: String, subtitle: String?, onHome: (() -> Unit)?) {
    val accent = LocalAccent.current.start
    val tokens = LocalTokens.current
    val line = "gulp $command"
    val typed by rememberTyped(line, key = line, charMs = 40, delayMs = 150L)
    val markStyle = remember(accent) { GulpStyle.outline(accent) }
    Column(Modifier.padding(top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HomeLink(onHome, accent, chevron = 18.dp) { GulpMark(18.dp, style = markStyle) }
            Spacer(Modifier.width(8.dp))
            Text("$ ", color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(line.take(typed), color = Palette.TextPrimary, fontSize = 13.sp, maxLines = 1, softWrap = false)
            Spacer(Modifier.width(3.dp))
            Box(
                Modifier
                    .size(width = 7.dp, height = 14.dp)
                    .blinking()
                    .background(accent),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            title.uppercase(),
            color = Palette.TextPrimary,
            fontSize = 34.sp,
            lineHeight = 40.sp,
            fontFamily = tokens.display,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.staggeredEntrance(0, baseDelay = 120),
        )
        subtitle?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                "# $it",
                color = Palette.TextSecondary,
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
                modifier = Modifier.staggeredEntrance(1, baseDelay = 200),
            )
        }
    }
}

// MARK: - File picker

/**
 * Big drop zone that opens the system file picker ([ActivityResultContracts.OpenDocument], or
 * [ActivityResultContracts.OpenMultipleDocuments] with [multiple]) filtered on [mimeTypes]. [onPicked] gets the
 * chosen content:// URIs (one with a single pick); nothing is called when the picker is cancelled.
 * The URIs are only readable for now: never persist them.
 */
@Composable
fun FilePickButton(
    onPicked: (List<Uri>) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Choisir un fichier",
    hint: String? = null,
    mimeTypes: Array<String> = arrayOf("*/*"),
    multiple: Boolean = false,
    icon: ImageVector = Icons.Rounded.UploadFile,
    enabled: Boolean = true,
) {
    val single = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onPicked(listOf(uri))
    }
    val many = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onPicked(uris)
    }
    val context = LocalContext.current
    FileDropZone(title = title, hint = hint, icon = icon, enabled = enabled, modifier = modifier) {
        try {
            if (multiple) many.launch(mimeTypes) else single.launch(mimeTypes)
        } catch (e: ActivityNotFoundException) {
            // No document picker on the phone (very rare, stripped-down Android builds).
            Toast.makeText(context, "Aucun sélecteur de fichiers sur ce téléphone.", Toast.LENGTH_LONG).show()
        }
    }
}

/**
 * The look of [FilePickButton] without the picker (screenshot tests, or a custom action): a card with a dashed
 * outline that keeps marching around, an icon that bobs, and a springy press.
 */
@Composable
fun FileDropZone(
    title: String,
    hint: String?,
    icon: ImageVector,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "dropZone")
    val march by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "dropMarch")
    val spin by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(7000, easing = LinearEasing)), label = "dropSpin")
    val glow by transition.animateFloat(0.10f, 0.26f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "dropGlow")
    val base = modifier
        .fillMaxWidth()
        .staggeredEntrance(2, baseDelay = 160)
        .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
        .semantics(mergeDescendants = true) {
            role = Role.Button
            contentDescription = title
        }
        .bounceClick(enabled = enabled, pressedScale = 0.96f, onClick = onClick)
    when (LocalSkin.current) {
        Skin.CLASSIC -> ClassicDropZone(title, hint, icon, march, spin, glow, base)
        Skin.PAPER -> PaperDropZone(title, hint, icon, march, base)
        Skin.TERMINAL -> TerminalDropZone(title, hint, icon, march, base)
    }
}

/** Dash length + gap of the drop zone outlines, in px. */
private const val DASH = 14f
private const val GAP = 10f

@Composable
private fun ClassicDropZone(title: String, hint: String?, icon: ImageVector, march: Float, spin: Float, glow: Float, modifier: Modifier) {
    val accent = LocalAccent.current
    val radius = LocalTokens.current.cardRadius
    Column(
        modifier
            .clip(RoundedCornerShape(radius))
            .background(Palette.Surface)
            .drawBehind {
                // Accent haze from the top, breathing.
                drawRect(Brush.verticalGradient(listOf(accent.start.copy(alpha = glow), Color.Transparent)))
                // Dashed outline: the dashes march, and the red → orange gradient turns slowly around the card.
                val w = 2.dp.toPx()
                val angle = spin * 2f * PI.toFloat()
                val r = size.maxDimension / 2
                val c = center
                val brush = Brush.linearGradient(
                    listOf(accent.start, accent.end, accent.start.copy(alpha = 0.35f)),
                    start = Offset(c.x + cos(angle) * r, c.y + sin(angle) * r),
                    end = Offset(c.x - cos(angle) * r, c.y - sin(angle) * r),
                )
                drawRoundRect(
                    brush,
                    topLeft = Offset(w / 2, w / 2),
                    size = Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(radius.toPx()),
                    style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH, GAP), -march * (DASH + GAP))),
                )
            }
            .heightIn(min = 200.dp)
            .padding(horizontal = 22.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .bob(0, amplitude = 5.dp, periodMs = 2600)
                .size(68.dp)
                .clip(CircleShape)
                .background(accent.brush),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Palette.OnAccent, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            title,
            color = Palette.TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = LocalTokens.current.display,
            textAlign = TextAlign.Center,
        )
        hint?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = Palette.TextSecondary, fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PaperDropZone(title: String, hint: String?, icon: ImageVector, march: Float, modifier: Modifier) {
    val accent = LocalAccent.current.start
    val ink = Palette.TextPrimary
    val radius = LocalTokens.current.cardRadius
    Column(
        modifier
            .paperCard()
            .drawBehind {
                // A "cut here" coupon line inside the white card, marching.
                val inset = 10.dp.toPx()
                val w = 1.5.dp.toPx()
                drawRoundRect(
                    ink.copy(alpha = 0.4f),
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    cornerRadius = CornerRadius((radius - 8.dp).toPx().coerceAtLeast(0f)),
                    style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH, GAP), -march * (DASH + GAP))),
                )
            }
            .heightIn(min = 200.dp)
            .padding(horizontal = 26.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .bob(0, amplitude = 4.dp, periodMs = 2600)
                .size(62.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            title,
            color = ink,
            fontSize = 24.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = LocalTokens.current.display,
            letterSpacing = (-0.5).sp,
            textAlign = TextAlign.Center,
        )
        hint?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = Palette.TextSecondary, fontSize = 14.sp, lineHeight = 19.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun TerminalDropZone(title: String, hint: String?, icon: ImageVector, march: Float, modifier: Modifier) {
    val accent = LocalAccent.current.start
    val radius: Dp = LocalTokens.current.cardRadius
    Column(
        modifier
            .clip(RoundedCornerShape(radius))
            .background(Palette.Surface)
            .drawBehind {
                val w = 1.dp.toPx()
                drawRoundRect(
                    accent,
                    topLeft = Offset(w / 2, w / 2),
                    size = Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(radius.toPx()),
                    style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), -march * 16f)),
                )
            }
            .heightIn(min = 180.dp)
            .padding(horizontal = 18.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .bob(0, amplitude = 3.dp, periodMs = 2200)
                .size(52.dp)
                .clip(RoundedCornerShape(radius))
                .background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "[ ${title.uppercase()} ]",
                color = accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text("▌", color = accent, fontSize = 15.sp, modifier = Modifier.blinking())
        }
        hint?.let {
            Spacer(Modifier.height(6.dp))
            Text("# $it", color = Palette.TextSecondary, fontSize = 12.sp, lineHeight = 17.sp, textAlign = TextAlign.Center)
        }
    }
}
