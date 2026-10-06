package com.powermaxv2.ytdownloader.ui.tools.preview

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.preview.HumanSize
import com.powermaxv2.ytdownloader.core.preview.PreviewBody
import com.powermaxv2.ytdownloader.core.preview.ZipNames
import com.powermaxv2.ytdownloader.core.preview.ZipRow
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import kotlinx.coroutines.delay

// Contents of a ZIP (also docx, xlsx, apk, jar…): a folder tree with sizes. Nothing is extracted; names that
// would escape a folder when extracted are flagged.

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ZipViewer(body: PreviewBody.Zip, modifier: Modifier = Modifier) {
    val listing = body.listing
    val terminal = LocalSkin.current == Skin.TERMINAL
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChip(if (listing.totalEntries == 1) "1 élément" else "${listing.totalEntries} éléments")
            StatChip("${HumanSize.format(listing.totalSize)} une fois extrait")
            if (listing.totalSize > 0 && listing.totalCompressed > 0) {
                val ratio = (100 - listing.totalCompressed * 100 / listing.totalSize).coerceIn(0, 99)
                StatChip("compressé à $ratio %")
            }
            if (listing.suspicious > 0) {
                WarningChip(if (listing.suspicious == 1) "1 nom suspect" else "${listing.suspicious} noms suspects")
            }
        }
        if (listing.truncated) {
            NoteBanner("Liste limitée aux ${ZipNames.MAX_ENTRIES} premiers éléments sur ${listing.totalEntries}.")
        }
        ViewerFrame {
            Column(Modifier.fillMaxSize()) {
                ViewerStrip("Contenu de l'archive · rien n'est extrait")
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 6.dp)) {
                    items(listing.rows.size) { i -> ZipRowView(listing.rows[i], i, terminal) }
                }
            }
        }
    }
}

@Composable
private fun StatChip(text: String) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    val shape = RoundedCornerShape(LocalTokens.current.chipRadius.coerceAtMost(12.dp))
    Text(
        if (terminal) text.uppercase() else text,
        color = Palette.TextSecondary,
        fontSize = if (terminal) 10.5.sp else 12.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .clip(shape)
            .background(Palette.Surface2)
            .border(1.dp, Palette.Stroke, shape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun ZipRowView(row: ZipRow, index: Int, terminal: Boolean) {
    val accent = LocalAccent.current.start
    val guide = Palette.Stroke
    // Rows slide in one after the other (only the first screenful is staggered).
    val appear = remember(row.path) { Animatable(if (index < 16) 0f else 1f) }
    LaunchedEffect(row.path) {
        if (appear.value < 1f) {
            delay(index * 28L)
            appear.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
        }
    }
    val warning = row.warning
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = appear.value
                translationX = (1f - appear.value) * 18.dp.toPx()
            }
            .background(if (warning != null) Palette.Danger.copy(alpha = 0.08f) else Color.Transparent)
            .drawBehind {
                // Tree guides: one faint vertical line per level.
                for (d in 0 until row.depth.coerceAtMost(12)) {
                    val x = (14 + d * 16 + 8).dp.toPx()
                    drawLine(guide, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
                }
            }
            .padding(start = (14 + row.depth.coerceAtMost(12) * 16).dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                when {
                    warning != null -> Icons.Rounded.WarningAmber
                    row.isDirectory -> Icons.Rounded.Folder
                    else -> Icons.Rounded.InsertDriveFile
                },
                contentDescription = null,
                tint = when {
                    warning != null -> Palette.Danger
                    row.isDirectory -> accent
                    else -> Palette.TextSecondary
                },
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (row.isDirectory && terminal) "${row.label}/" else row.label,
                color = if (warning != null) Palette.Danger else Palette.TextPrimary,
                fontSize = 13.5.sp,
                fontWeight = if (row.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                fontFamily = if (warning != null) MonoFamily else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (!row.isDirectory && row.size >= 0) {
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(HumanSize.format(row.size), color = Palette.TextSecondary, fontSize = 12.sp, maxLines = 1)
                    if (row.compressedSize in 0 until row.size) {
                        Text("→ ${HumanSize.format(row.compressedSize)}", color = Palette.TextTertiary, fontSize = 10.5.sp, maxLines = 1)
                    }
                }
            }
        }
        if (warning != null) {
            Spacer(Modifier.height(5.dp))
            Box(Modifier.padding(start = 26.dp)) {
                WarningChip("Nom suspect · $warning")
            }
        }
    }
}
