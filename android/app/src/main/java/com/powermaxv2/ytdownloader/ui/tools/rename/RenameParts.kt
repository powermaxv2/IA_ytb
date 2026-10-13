package com.powermaxv2.ytdownloader.ui.tools.rename

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.files.FileKind
import com.powermaxv2.ytdownloader.core.rename.NameBlock
import com.powermaxv2.ytdownloader.core.rename.RenamePlan
import com.powermaxv2.ytdownloader.core.rename.RenameRow
import com.powermaxv2.ytdownloader.core.rename.RowStatus
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tools.convert.KindTile
import com.powermaxv2.ytdownloader.ui.tools.convert.kindIcon

// Building blocks of the Renommeur: the pieces of the name as chips, and the before / after lines.

internal fun skinText(text: String, terminal: Boolean): String = if (terminal) text.uppercase() else text

internal fun blockIcon(block: NameBlock): ImageVector = when (block) {
    is NameBlock.Text -> Icons.Rounded.TextFields
    is NameBlock.Counter -> Icons.Rounded.Numbers
    is NameBlock.Date -> Icons.Rounded.CalendarMonth
    NameBlock.Original -> Icons.AutoMirrored.Rounded.InsertDriveFile
}

/** What the chip of a block says: its text, "001", the date format, "Nom". */
internal fun blockLabel(block: NameBlock): String = when (block) {
    is NameBlock.Text -> block.text.ifEmpty { "Texte" }.replace(' ', '·')
    is NameBlock.Counter -> RenamePlan.counter(block, 0)
    is NameBlock.Date -> block.format.label
    NameBlock.Original -> "Nom d'origine"
}

internal fun fileIcon(name: String): ImageVector = kindIcon(FileKind.fromExtension(RenamePlan.extension(name)))

/** One piece of the name, tappable to edit, with a × when it can go. Pops when it gets selected. */
@Composable
internal fun BlockChip(block: NameBlock, selected: Boolean, index: Int, onClick: () -> Unit, onRemove: (() -> Unit)?) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(if (skin == Skin.TERMINAL) 4.dp else 12.dp)
    val pop = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected) {
            pop.snapTo(0.86f)
            pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 520f))
        }
    }
    val fill by animateColorAsState(
        when {
            !selected -> if (skin == Skin.TERMINAL) Color.Transparent else Palette.Surface2
            skin == Skin.PAPER -> Palette.TextPrimary
            skin == Skin.TERMINAL -> accent.start
            else -> accent.start.copy(alpha = 0.24f)
        },
        tween(200),
        label = "blockFill",
    )
    val ink = when {
        !selected -> Palette.TextPrimary
        skin == Skin.PAPER -> Color.White
        skin == Skin.TERMINAL -> Palette.Background
        else -> Palette.TextPrimary
    }
    Row(
        Modifier
            .staggeredEntrance(index, key = block, baseDelay = 30)
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
            .clip(shape)
            .background(fill)
            .border(1.dp, if (selected) accent.start.copy(alpha = 0.8f) else Palette.Stroke, shape)
            .bounceClick(pressedScale = 0.92f, onClick = onClick)
            .height(36.dp)
            .padding(start = 10.dp, end = if (onRemove != null) 2.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(blockIcon(block), contentDescription = null, tint = if (selected) ink else accent.start, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            skinText(blockLabel(block), skin == Skin.TERMINAL),
            color = ink,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 150.dp),
        )
        onRemove?.let {
            Box(
                Modifier
                    .size(30.dp)
                    .bounceClick(pressedScale = 0.8f, onClick = it),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Retirer ce morceau", tint = ink.copy(alpha = 0.6f), modifier = Modifier.size(15.dp))
            }
        }
    }
}

/** "+ Texte", "+ Numéro"… : adds a piece at the end of the name. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AddBlockRow(onAdd: (NameBlock) -> Unit) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    val accent = LocalAccent.current.start
    val shape = RoundedCornerShape(if (terminal) 4.dp else 50.dp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            "Texte" to NameBlock.Text("_"),
            "Numéro" to NameBlock.Counter(),
            "Date" to NameBlock.Date(),
            "Nom" to NameBlock.Original,
        ).forEach { (label, block) ->
            Row(
                Modifier
                    .clip(shape)
                    .border(1.dp, accent.copy(alpha = 0.5f), shape)
                    .bounceClick(pressedScale = 0.9f) { onAdd(block) }
                    .height(32.dp)
                    .padding(horizontal = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text(skinText(label, terminal), color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

/**
 * One file of the preview: its current name crossed out, and the new one under it, which slides in from below each
 * time the rule changes it. A clash, an unchanged name or a copy is said on the right.
 */
@Composable
internal fun PreviewRow(row: RenameRow, index: Int) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    val accent = LocalAccent.current.start
    val changes = row.status == RowStatus.READY
    Row(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(index.coerceAtMost(12), key = row.source.key, baseDelay = 40)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KindTile(fileIcon(row.source.name), size = 34.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                row.source.name,
                color = Palette.TextTertiary,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (changes) TextDecoration.LineThrough else null,
            )
            AnimatedContent(
                targetState = row.newName to row.status,
                transitionSpec = {
                    (slideInVertically(spring(dampingRatio = 0.75f, stiffness = 420f)) { it } + fadeIn(tween(160))) togetherWith
                        (slideOutVertically(tween(140)) { -it } + fadeOut(tween(120)))
                },
                label = "newName",
            ) { (name, status) ->
                Text(
                    (if (terminal) "→ " else "") + name,
                    color = when (status) {
                        RowStatus.READY -> if (terminal) accent else Palette.TextPrimary
                        RowStatus.CLASH, RowStatus.EMPTY -> Palette.Danger
                        RowStatus.SAME -> Palette.TextSecondary
                    },
                    fontSize = 15.sp,
                    fontWeight = if (status == RowStatus.READY) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        StatusBadge(row)
    }
}

@Composable
private fun StatusBadge(row: RenameRow) {
    val (text, icon, color) = when {
        row.status == RowStatus.CLASH -> Triple("Doublon", Icons.Rounded.Warning, Palette.Danger)
        row.status == RowStatus.EMPTY -> Triple("Vide", Icons.Rounded.Warning, Palette.Danger)
        row.status == RowStatus.SAME -> Triple("Inchangé", null, Palette.TextTertiary)
        !row.source.renamable -> Triple("Copie", Icons.Rounded.ContentCopy, LocalAccent.current.start)
        else -> return
    }
    val terminal = LocalSkin.current == Skin.TERMINAL
    Spacer(Modifier.width(8.dp))
    Row(
        Modifier
            .clip(RoundedCornerShape(if (terminal) 3.dp else 50.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = color, modifier = Modifier.size(12.dp)) }
        if (icon != null) Spacer(Modifier.width(3.dp))
        Text(skinText(text, terminal), color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** The new name of a sample file, big, built piece by piece in the block colours: the rule at a glance. */
@Composable
internal fun SampleName(name: String, modifier: Modifier = Modifier) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (terminal) 4.dp else LocalTokens.current.fieldRadius))
            .background(LocalAccent.current.start.copy(alpha = 0.08f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        AnimatedContent(
            targetState = name,
            transitionSpec = {
                (slideInVertically(spring(dampingRatio = 0.7f, stiffness = 380f)) { it / 2 } + fadeIn(tween(180))) togetherWith fadeOut(tween(120))
            },
            label = "sampleName",
        ) { shown ->
            Text(
                (if (terminal) "$ mv … " else "") + shown,
                color = Palette.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = if (terminal) LocalTokens.current.body else LocalTokens.current.display,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A line of the result card ("✓ 12 fichiers renommés"). */
@Composable
internal fun ResultLine(text: String, index: Int, warning: Boolean = false) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Text(
        (if (terminal) "> " else if (warning) "⚠ " else "✓ ") + text,
        color = if (warning) Palette.Danger else Palette.TextPrimary,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        modifier = Modifier
            .fillMaxWidth()
            .staggeredEntrance(index, baseDelay = 300)
            .padding(vertical = 3.dp),
    )
}
