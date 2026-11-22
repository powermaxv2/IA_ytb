package com.powermaxv2.ytdownloader.ui.tools.preview

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.TextSnippet
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.files.FileKind
import com.powermaxv2.ytdownloader.core.preview.HexDump
import com.powermaxv2.ytdownloader.core.preview.HumanSize
import com.powermaxv2.ytdownloader.core.preview.KindNames
import com.powermaxv2.ytdownloader.core.preview.PreviewBody
import com.powermaxv2.ytdownloader.core.preview.PreviewFile
import com.powermaxv2.ytdownloader.core.preview.TokenType
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.ProgressBar
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.pulsing
import com.powermaxv2.ytdownloader.ui.shimmer
import com.powermaxv2.ytdownloader.ui.skin.JetBrainsMonoFamily
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.tools.convert.BlockLabel
import com.powermaxv2.ytdownloader.ui.tools.convert.KindTile
import com.powermaxv2.ytdownloader.ui.tools.convert.toolCard

// Pieces of the Aperçu shared by every viewer: the file card that unfolds into the info sheet, the kind badge,
// the banners, and the syntax colours of each skin.

/** State of the on-demand SHA-256 of the info sheet. */
@Immutable
data class HashState(
    /** null = not started; 0..1 while computing (-1 = unknown size); 1 with [value] when done. */
    val progress: Float? = null,
    val value: String? = null,
    val error: String? = null,
)

/** Icon of a kind in the Aperçu. */
internal fun previewIcon(kind: FileKind?): ImageVector = when (kind) {
    FileKind.IMAGE -> Icons.Rounded.Image
    FileKind.VIDEO -> Icons.Rounded.Movie
    FileKind.AUDIO -> Icons.Rounded.MusicNote
    FileKind.PDF -> Icons.Rounded.PictureAsPdf
    FileKind.TEXT -> Icons.Rounded.TextSnippet
    FileKind.MARKDOWN -> Icons.Rounded.Article
    FileKind.CSV -> Icons.Rounded.TableChart
    FileKind.JSON -> Icons.Rounded.Code
    FileKind.CODE -> Icons.Rounded.Code
    FileKind.ZIP -> Icons.Rounded.Archive
    else -> Icons.Rounded.InsertDriveFile
}

/** Monospace text of the viewers (code, hex dump), the same in every skin. */
internal val MonoFamily = JetBrainsMonoFamily

// MARK: - Kind badge

/** "CODE", "PDF"…: the badge flips (rises and pops) when the kind changes, "…" while unknown. */
@Composable
internal fun KindBadge(kind: FileKind?, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val tokens = LocalTokens.current
    AnimatedContent(
        targetState = kind,
        transitionSpec = {
            (slideInVertically(spring(dampingRatio = 0.55f, stiffness = 420f)) { it } + fadeIn(tween(140)) + scaleIn(initialScale = 0.6f)) togetherWith
                (slideOutVertically(tween(160)) { -it } + fadeOut(tween(120)) + scaleOut(targetScale = 0.6f))
        },
        label = "kindBadge",
        modifier = modifier,
    ) { k ->
        val label = k?.let { KindNames.label(it).substringAfter("Archive ").uppercase() } ?: "…"
        val shape = RoundedCornerShape(if (skin == Skin.TERMINAL) tokens.chipRadius else 10.dp)
        val text = if (skin == Skin.TERMINAL) "[$label]" else label
        val base = Modifier
            .then(if (k == null) Modifier.pulsing(0.9f, 1.08f, 700) else Modifier)
            .clip(shape)
        val styled = when (skin) {
            Skin.CLASSIC -> base.background(if (k == null) Palette.Surface2 else accent.start).padding(horizontal = 9.dp, vertical = 4.dp)
            Skin.PAPER -> base.background(if (k == null) Palette.Surface2 else Palette.TextPrimary).padding(horizontal = 9.dp, vertical = 4.dp)
            Skin.TERMINAL -> base.border(1.dp, accent.start, shape).padding(horizontal = 6.dp, vertical = 3.dp)
        }
        Text(
            text,
            color = when {
                skin == Skin.TERMINAL -> accent.start
                k == null -> Palette.TextSecondary
                else -> Color.White
            },
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.8.sp,
            maxLines = 1,
            modifier = styled,
        )
    }
}

// MARK: - File card / info sheet

/**
 * The opened file: icon, name, size and type, the kind badge; a tap unfolds the info sheet (MIME, kind,
 * extension check, hex dump of the first bytes, SHA-256 on demand). [file] null = still reading (skeleton).
 */
@Composable
internal fun InfoCard(
    file: PreviewFile?,
    body: PreviewBody?,
    hash: HashState,
    onHash: () -> Unit,
    onCopyHash: (String) -> Unit = {},
    initiallyOpen: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val skin = LocalSkin.current
    val terminal = skin == Skin.TERMINAL
    var open by rememberSaveable(file?.uri) { mutableStateOf(initiallyOpen) }
    val rotation by animateFloatAsState(if (open) 180f else 0f, spring(dampingRatio = 0.6f, stiffness = 380f), label = "infoChevron")
    // The card springs in from a smaller size each time a new file arrives.
    val appear = remember(file?.uri) { Animatable(if (file == null) 1f else 0f) }
    LaunchedEffect(file?.uri) { appear.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 300f)) }
    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                val p = appear.value
                alpha = (0.4f + 0.6f * p).coerceIn(0f, 1f)
                scaleX = 0.94f + 0.06f * p
                scaleY = 0.94f + 0.06f * p
            }
            .toolCard()
            .then(if (file == null) Modifier.shimmer() else Modifier)
            .bounceClick(enabled = file != null, pressedScale = 0.98f) { open = !open }
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.pulsingIf(file == null)) { KindTile(previewIcon(file?.kind)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                if (file == null) {
                    SkeletonLine(0.75f, 15.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(if (terminal) "# lecture du fichier…" else "Lecture du fichier…", color = Palette.TextSecondary, fontSize = 13.sp)
                } else {
                    Text(
                        file.name,
                        color = Palette.TextPrimary,
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    val meta = listOfNotNull(file.size?.let { HumanSize.format(it) }, file.mime).joinToString(" · ")
                    Text(
                        if (terminal) "# $meta" else meta,
                        color = Palette.TextSecondary,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                KindBadge(file?.kind)
                if (file != null) {
                    Spacer(Modifier.height(6.dp))
                    Icon(
                        Icons.Rounded.ExpandMore,
                        contentDescription = if (open) "Masquer les détails" else "Détails du fichier",
                        tint = Palette.TextSecondary,
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer { rotationZ = rotation },
                    )
                }
            }
        }
        file?.expectedExtension?.let { expected ->
            Spacer(Modifier.height(10.dp))
            WarningChip("Extension trompeuse : le contenu est un fichier ${expected.uppercase()}")
        }
        if (file != null) {
            AnimatedVisibility(
                visible = open,
                enter = expandVertically(spring(dampingRatio = 0.85f, stiffness = 380f)) + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                InfoDetails(file, body, hash, onHash, onCopyHash)
            }
        }
    }
}

@Composable
private fun Modifier.pulsingIf(on: Boolean): Modifier = if (on) pulsing(0.94f, 1.04f, 900) else this

@Composable
private fun InfoDetails(file: PreviewFile, body: PreviewBody?, hash: HashState, onHash: () -> Unit, onCopyHash: (String) -> Unit) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Divider()
        InfoRow("Nom", file.name)
        InfoRow("Taille", file.size?.let { "${HumanSize.format(it)} · ${HumanSize.exact(it)}" } ?: "Inconnue")
        InfoRow("Type MIME", file.mime)
        val detail = when (body) {
            is PreviewBody.Code -> "${KindNames.label(file.kind)} · ${body.lang.label}"
            is PreviewBody.Text -> "${KindNames.label(file.kind)} · ${body.charset}"
            else -> KindNames.label(file.kind)
        }
        InfoRow("Nature détectée", detail)
        file.expectedExtension?.let {
            InfoRow(
                "Extension",
                "« .${file.name.substringAfterLast('.', "")} » ne correspond pas au contenu, qui est un fichier ${it.uppercase()}. " +
                    "Renommez-le en .$it pour que les autres apps l'ouvrent.",
                danger = true,
            )
        }
        BlockLabel("Premiers octets")
        val lines = remember(file.head) { HexDump.lines(file.head) }
        if (lines.isEmpty()) {
            Text("Fichier vide.", color = Palette.TextSecondary, fontSize = 13.sp)
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(LocalTokens.current.fieldRadius))
                    .background(Palette.Surface2)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    lines.joinToString("\n"),
                    color = Palette.TextPrimary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontFamily = MonoFamily,
                    softWrap = false,
                )
            }
        }
        BlockLabel("Empreinte SHA-256")
        HashBlock(hash, onHash, onCopyHash, terminal)
    }
}

@Composable
private fun HashBlock(hash: HashState, onHash: () -> Unit, onCopyHash: (String) -> Unit, terminal: Boolean) {
    val accent = LocalAccent.current.start
    AnimatedContent(
        targetState = when {
            hash.value != null -> 2
            hash.progress != null -> 1
            else -> 0
        },
        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
        label = "hashStep",
    ) { step ->
        when (step) {
            0 -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    Modifier
                        .bounceClick(onClick = onHash)
                        .clip(RoundedCornerShape(LocalTokens.current.chipRadius))
                        .border(1.dp, accent.copy(alpha = 0.7f), RoundedCornerShape(LocalTokens.current.chipRadius))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Fingerprint, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (terminal) "CALCULER" else "Calculer l'empreinte",
                        color = accent,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                hash.error?.let { Text(it, color = Palette.Danger, fontSize = 12.5.sp) }
            }
            1 -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val p = hash.progress ?: 0f
                ProgressBar(if (p < 0f) 0.5f else p, Modifier.fillMaxWidth())
                Text(
                    if (p < 0f) "Lecture du fichier…" else "Lecture du fichier… ${(p * 100).toInt()} %",
                    color = Palette.TextSecondary,
                    fontSize = 12.5.sp,
                )
            }
            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val digest = hash.value.orEmpty()
                SelectionContainer {
                    Text(
                        digest,
                        color = Palette.TextPrimary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        fontFamily = MonoFamily,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(LocalTokens.current.fieldRadius))
                            .background(Palette.Surface2)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
                Row(
                    Modifier
                        .bounceClick { onCopyHash(digest) }
                        .clip(RoundedCornerShape(LocalTokens.current.chipRadius))
                        .border(1.dp, accent.copy(alpha = 0.7f), RoundedCornerShape(LocalTokens.current.chipRadius))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (terminal) "COPIER" else "Copier l'empreinte",
                        color = accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, danger: Boolean = false) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Row(Modifier.fillMaxWidth()) {
        Text(
            if (terminal) label.uppercase() else label,
            color = Palette.TextTertiary,
            fontSize = if (terminal) 11.sp else 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .alignByBaseline()
                .width(if (terminal) 132.dp else 118.dp)
                .padding(end = 8.dp),
        )
        Text(
            value,
            color = if (danger) Palette.Danger else Palette.TextPrimary,
            fontSize = 13.5.sp,
            lineHeight = 18.sp,
            modifier = Modifier
                .alignByBaseline()
                .weight(1f),
        )
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Palette.Stroke),
    )
}

@Composable
internal fun SkeletonLine(fraction: Float, height: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth(fraction)
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(Palette.TextPrimary.copy(alpha = 0.08f)),
    )
}

// MARK: - Banners

/** Danger-tinted chip with a warning sign. */
@Composable
internal fun WarningChip(text: String, modifier: Modifier = Modifier) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    val shape = RoundedCornerShape(LocalTokens.current.chipRadius.coerceAtMost(12.dp))
    Row(
        modifier
            .clip(shape)
            .background(Palette.Danger.copy(alpha = 0.12f))
            .border(1.dp, Palette.Danger.copy(alpha = 0.45f), shape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = Palette.Danger, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            if (terminal) text.uppercase() else text,
            color = Palette.Danger,
            fontSize = if (terminal) 10.5.sp else 12.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 15.sp,
        )
    }
}

/** A quiet notice above a viewer ("Aperçu limité au premier Mo", "JSON non valide…"). */
@Composable
internal fun NoteBanner(text: String, modifier: Modifier = Modifier) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    val accent = LocalAccent.current.start
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LocalTokens.current.fieldRadius))
            .background(accent.copy(alpha = if (LocalTokens.current.isLight) 0.10f else 0.14f))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (terminal) "!" else "ⓘ", color = accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text(
            if (terminal) text.uppercase() else text,
            color = Palette.TextPrimary,
            fontSize = if (terminal) 11.sp else 13.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

// MARK: - Syntax colours

/** Colours of the highlighter in the current skin. */
@Immutable
internal data class SyntaxColors(
    val plain: Color,
    val keyword: Color,
    val string: Color,
    val comment: Color,
    val number: Color,
    val type: Color,
    val annotation: Color,
    val tag: Color,
    val attr: Color,
    val gutter: Color,
) {
    fun of(type: TokenType): Color = when (type) {
        TokenType.KEYWORD -> keyword
        TokenType.STRING -> string
        TokenType.COMMENT -> comment
        TokenType.NUMBER -> number
        TokenType.TYPE -> this.type
        TokenType.ANNOTATION -> annotation
        TokenType.TAG -> tag
        TokenType.ATTR -> attr
    }
}

@Composable
internal fun syntaxColors(): SyntaxColors = when (LocalSkin.current) {
    Skin.CLASSIC -> SyntaxColors(
        plain = Palette.TextPrimary,
        keyword = Color(0xFFFF8A5B),
        string = Color(0xFFB5E48C),
        comment = Palette.TextTertiary,
        number = Color(0xFFFF7EB0),
        type = Color(0xFFFFD580),
        annotation = Color(0xFFC9A0FF),
        tag = Color(0xFFFF6B5B),
        attr = Color(0xFF8CB8FF),
        gutter = Palette.TextTertiary,
    )
    Skin.PAPER -> SyntaxColors(
        plain = Palette.TextPrimary,
        keyword = Color(0xFFC2410C),
        string = Color(0xFF15803D),
        comment = Color(0xFF9C8178),
        number = Color(0xFFB4235A),
        type = Color(0xFF8A5A00),
        annotation = Color(0xFF7C3AED),
        tag = Color(0xFFD93025),
        attr = Color(0xFF1F5FBF),
        gutter = Palette.TextTertiary,
    )
    Skin.TERMINAL -> SyntaxColors(
        plain = Palette.TextPrimary,
        keyword = LocalAccent.current.start,
        string = Color(0xFFFFD38A),
        comment = Color(0xFF7D6E63),
        number = Color(0xFFFFB86B),
        type = Color(0xFFF2E6DC),
        annotation = Color(0xFFFF5FD2),
        tag = LocalAccent.current.start,
        attr = Color(0xFFC9B8AA),
        gutter = Palette.TextTertiary,
    )
}
