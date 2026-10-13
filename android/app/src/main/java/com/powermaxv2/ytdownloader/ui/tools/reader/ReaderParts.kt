package com.powermaxv2.ytdownloader.ui.tools.reader

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.chat.Kind
import com.powermaxv2.ytdownloader.core.reader.ChatPdf
import com.powermaxv2.ytdownloader.core.reader.ChatReader
import com.powermaxv2.ytdownloader.core.reader.ReaderRow
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.PrimaryButton
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.shimmer
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tools.ToolTopBar
import com.powermaxv2.ytdownloader.ui.tools.compress.Hint
import com.powermaxv2.ytdownloader.ui.tools.convert.BlockLabel
import com.powermaxv2.ytdownloader.ui.tools.convert.toolCard
import com.powermaxv2.ytdownloader.ui.tools.qr.QrField
import com.powermaxv2.ytdownloader.ui.tools.styled.MiniAction
import kotlinx.coroutines.launch

// Pieces of the Lecteur de discussion: the open discussion (top bar, search, header card, then the bubbles in a
// lazy list), and the start screen's little animated chat and "how to export" card.

/** WhatsApp's own green, softened on every skin for "my" bubbles. */
private val WhatsAppGreen = Color(0xFF25D366)

object ReaderList {
    /** Items above the first row in the list (the header card). */
    const val HEAD_ITEMS = 1
}

@Composable
private fun skinText(text: String): String = if (LocalSkin.current == Skin.TERMINAL) text.uppercase() else text

/** The open discussion: top bar and search stay put, the header card and the bubbles scroll. */
@Composable
fun ReaderScreen(
    state: ReaderUiState,
    actions: ReaderActions,
    listState: LazyListState,
    onHome: (() -> Unit)?,
    contentPadding: PaddingValues,
) {
    val chat = state.chat
    val rows = remember(chat, state.me) { ChatReader.rows(chat.messages, state.me) }
    val matches = remember(chat, state.query) { ChatReader.search(chat.messages, state.query) }
    val current = if (matches.isEmpty()) null else matches[Math.floorMod(state.match, matches.size)]
    val scope = rememberCoroutineScope()

    // Each new match (typed or stepped to) scrolls into view.
    LaunchedEffect(current, rows) {
        val index = current ?: return@LaunchedEffect
        listState.animateScrollToItem(ReaderList.HEAD_ITEMS + ChatReader.rowOf(rows, index))
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding()),
        ) {
            Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ToolTopBar(READER_TITLE, READER_COMMAND, null, onHome)
                SearchBar(state.query, matches.size, current?.let { matches.indexOf(it) }, actions)
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = contentPadding.calculateBottomPadding() + 64.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item(key = "head") {
                    HeaderCard(state, actions, Modifier.padding(start = 4.dp, end = 4.dp, bottom = 12.dp))
                }
                items(rows, key = { it.key }, contentType = { if (it is ReaderRow.Day) 0 else 1 }) { row ->
                    when (row) {
                        is ReaderRow.Day -> DayPill(row.label, Modifier.animateItem())
                        is ReaderRow.Message -> Bubble(
                            row,
                            photo = row.message.file?.let { chat.photos[it] },
                            query = state.query,
                            current = row.index == current,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                item(key = "end") {
                    Text(
                        skinText("Fin de la discussion"),
                        color = Palette.TextTertiary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                    )
                }
            }
        }
        // "Derniers messages": jumps to the end, shown while there is more below.
        AnimatedVisibility(
            visible = listState.canScrollForward && listState.firstVisibleItemIndex > 2,
            enter = scaleIn(spring(dampingRatio = 0.5f, stiffness = 500f)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = contentPadding.calculateBottomPadding() + 8.dp),
        ) {
            JumpButton { scope.launch { listState.animateScrollToItem(ReaderList.HEAD_ITEMS + rows.size) } }
        }
    }
}

/** Search field, "3 / 12" and the two arrows. */
@Composable
private fun SearchBar(query: String, count: Int, position: Int?, actions: ReaderActions) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QrField(
            label = "Rechercher",
            value = query,
            onChange = actions.onQuery,
            placeholder = "Un mot, un prénom…",
            maxLength = 80,
            modifier = Modifier.weight(1f),
        )
        AnimatedVisibility(query.trim().length >= ChatReader.MIN_QUERY, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(
                    targetState = if (count == 0) "0" else "${(position ?: 0) + 1}/$count",
                    transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) },
                    label = "matchCount",
                ) { text ->
                    Text(text, color = if (count == 0) Palette.Danger else Palette.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                ArrowButton(Icons.Rounded.KeyboardArrowUp, "Précédent", enabled = count > 0) { actions.onStep(-1) }
                ArrowButton(Icons.Rounded.KeyboardArrowDown, "Suivant", enabled = count > 0) { actions.onStep(1) }
            }
        }
    }
}

@Composable
private fun ArrowButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.35f }
            .bounceClick(enabled = enabled, pressedScale = 0.8f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = LocalAccent.current.start, modifier = Modifier.size(26.dp))
    }
}

/** Name, figures, who is "moi", the months, and the PDF. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeaderCard(state: ReaderUiState, actions: ReaderActions, modifier: Modifier) {
    val chat = state.chat
    val authors = remember(chat) { ChatReader.authors(chat.messages) }
    val months = remember(chat) { ChatReader.months(chat.messages) }
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(LocalTokens.current.chipRadius)
    Column(
        modifier
            .fillMaxWidth()
            .staggeredEntrance(0, baseDelay = 40)
            .toolCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Forum, contentDescription = null, tint = accent.start, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(chat.name, color = Palette.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                Text(ChatReader.summary(chat.messages), color = Palette.TextSecondary, fontSize = 12.5.sp)
            }
        }
        if (chat.sample) Hint("Exemple inventé : choisis ton export pour lire ta discussion.")

        BlockLabel("Moi, c'est")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            authors.take(12).forEach { name ->
                val chosen = name == state.me
                val fill by animateColorAsState(if (chosen) WhatsAppGreen.copy(alpha = 0.22f) else Color.Transparent, label = "meChip")
                Text(
                    name,
                    color = if (chosen) Palette.TextPrimary else Palette.TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    modifier = Modifier
                        .bounceClick(pressedScale = 0.9f) { actions.onMe(name) }
                        .clip(shape)
                        .background(fill)
                        .border(1.dp, if (chosen) WhatsAppGreen else Palette.Stroke, shape)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }

        if (months.size > 1) {
            BlockLabel("Aller à")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                months.forEach { m ->
                    Text(
                        skinText(m.label),
                        color = Palette.TextPrimary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        modifier = Modifier
                            .bounceClick(pressedScale = 0.9f) { actions.onMonth(m.firstIndex) }
                            .clip(shape)
                            .border(1.dp, Palette.Stroke, shape)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }

        AnimatedContent(
            targetState = state.pdf,
            contentKey = { it::class },
            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.9f)) togetherWith fadeOut() },
            label = "pdfState",
        ) { pdf ->
            when (pdf) {
                is PdfState.Done -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MiniAction("Ouvrir le PDF", Icons.Rounded.OpenInNew, onClick = actions.onOpenPdf)
                    MiniAction("Partager", Icons.Rounded.Share, onClick = actions.onSharePdf)
                }
                else -> PrimaryButton(
                    text = if (pdf is PdfState.Working) "PDF en cours… ${(pdf.progress * 100).toInt()} %" else "Exporter en PDF",
                    icon = Icons.Rounded.PictureAsPdf,
                    subtitle = state.me?.let { "« $it » à droite, comme dans WhatsApp" },
                    loading = false,
                    enabled = pdf !is PdfState.Working,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = actions.onPdf,
                )
            }
        }
        MiniAction("Autre discussion", Icons.Rounded.UploadFile, onClick = actions.onChooseFile)
    }
}

/** "Mardi 15 septembre 2026", centred, like WhatsApp's date bubbles. */
@Composable
private fun DayPill(label: String, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    val shape = RoundedCornerShape(10.dp)
    Box(modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(
            skinText(label),
            color = Palette.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(shape)
                .background(if (skin == Skin.TERMINAL) Color.Transparent else Palette.Surface2)
                .border(1.dp, Palette.Stroke, shape)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

/** One message: slides in from its side, name in its colour in a group, media line, photo, text, time. */
@Composable
private fun Bubble(row: ReaderRow.Message, photo: ByteArray?, query: String, current: Boolean, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    val accent = LocalAccent.current
    val m = row.message
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 420f)) }
    val ring by animateColorAsState(if (current) accent.start else Color.Transparent, tween(250), label = "matchRing")
    val tail = 4.dp
    val round = 16.dp
    val shape = when {
        !row.last -> RoundedCornerShape(round)
        row.mine -> RoundedCornerShape(topStart = round, topEnd = round, bottomEnd = tail, bottomStart = round)
        else -> RoundedCornerShape(topStart = round, topEnd = round, bottomEnd = round, bottomStart = tail)
    }
    val fill = when {
        skin == Skin.TERMINAL -> Color.Transparent
        row.mine -> WhatsAppGreen.copy(alpha = 0.18f)
        else -> Palette.Surface2
    }
    val edge = if (skin == Skin.TERMINAL) (if (row.mine) accent.start.copy(alpha = 0.6f) else Palette.Stroke) else Color.Transparent
    val image = remember(photo) { photo?.let { decode(it) } }
    val media = ChatReader.mediaLabel(m.kind)
    val body = ChatReader.body(m)
    val highlight = accent.start.copy(alpha = 0.32f)

    BoxWithConstraints(modifier.fillMaxWidth().padding(top = if (row.showAuthor) 6.dp else 0.dp, bottom = if (row.last) 6.dp else 0.dp)) {
        val max = maxWidth * 0.8f
        Column(
            Modifier
                .align(if (row.mine) Alignment.CenterEnd else Alignment.CenterStart)
                .graphicsLayer {
                    val p = enter.value
                    alpha = p
                    translationX = (1f - p) * (if (row.mine) 32.dp.toPx() else -32.dp.toPx())
                    scaleX = 0.94f + 0.06f * p
                    scaleY = 0.94f + 0.06f * p
                }
                .widthIn(max = max)
                .clip(shape)
                .background(fill)
                .border(if (current) 2.dp else 1.dp, if (current) ring else edge, shape)
                .padding(horizontal = 11.dp, vertical = 7.dp),
        ) {
            if (row.showAuthor) {
                Text(m.author, color = Color(ChatPdf.nameColor(m.author)), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            if (media != null) {
                Text(
                    media,
                    color = if (m.kind == Kind.DELETED) Palette.TextTertiary else Palette.TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (image != null) {
                Spacer(Modifier.height(4.dp))
                Image(
                    image,
                    contentDescription = m.file,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .widthIn(max = 220.dp)
                        .heightIn(max = 260.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
            } else if (m.file != null && m.kind != Kind.STICKER) {
                Text(m.file, color = Palette.TextTertiary, fontSize = 11.sp, maxLines = 1)
            }
            if (body.isNotEmpty()) {
                Text(
                    highlighted(body, query, highlight),
                    color = Palette.TextPrimary,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                )
            }
            Text(
                row.time + if (m.edited) " · modifié" else "",
                color = Palette.TextTertiary,
                fontSize = 10.5.sp,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

private fun highlighted(text: String, query: String, color: Color): AnnotatedString {
    val ranges = ChatReader.matches(text, query)
    if (ranges.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        ranges.forEach { addStyle(SpanStyle(background = color, fontWeight = FontWeight.Bold), it.first, it.last + 1) }
    }
}

private fun decode(bytes: ByteArray): ImageBitmap? =
    runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()

/** Round accent button with a bouncing arrow: back to the newest messages. */
@Composable
private fun JumpButton(onClick: () -> Unit) {
    val accent = LocalAccent.current
    val bounce = rememberInfiniteTransition(label = "jump")
    val dy by bounce.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), androidx.compose.animation.core.RepeatMode.Reverse), label = "jumpDy")
    Box(
        Modifier
            .size(52.dp)
            .bounceClick(pressedScale = 0.85f, onClick = onClick)
            .clip(CircleShape)
            .background(accent.brush),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.KeyboardArrowDown,
            contentDescription = "Derniers messages",
            tint = Palette.OnAccent,
            modifier = Modifier
                .size(30.dp)
                .graphicsLayer { translationY = dy * 4.dp.toPx() },
        )
    }
}

// MARK: - Start screen

/** While the export is read. */
@Composable
internal fun ReaderLoading() {
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .shimmer()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Forum, contentDescription = null, tint = LocalAccent.current.start, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(skinText("Ouverture de la discussion…"), color = Palette.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Text("Les photos d'un export avec médias sont réduites au passage.", color = Palette.TextSecondary, fontSize = 13.sp)
    }
}

/** A little chat that writes itself in a loop: bubbles pop in from each side, then a PDF badge lands. */
@Composable
internal fun ReaderDemo(modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val skin = LocalSkin.current
    val loop = rememberInfiniteTransition(label = "readerDemo")
    val t by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(5_200, easing = LinearEasing)), label = "readerDemoT")
    val lines = listOf(
        Triple(false, 0.55f, "On se voit samedi ?"),
        Triple(true, 0.40f, "Oui ! 🎉"),
        Triple(false, 0.70f, "Je ramène le gâteau"),
        Triple(true, 0.50f, "Parfait, à samedi"),
    )
    Column(
        modifier
            .fillMaxWidth()
            .toolCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        lines.forEachIndexed { i, (mine, width, text) ->
            // Each bubble appears at its turn, stays, and everything fades before the loop starts again.
            val start = 0.06f + i * 0.14f
            val p = ((t - start) / 0.08f).coerceIn(0f, 1f)
            val out = ((t - 0.9f) / 0.1f).coerceIn(0f, 1f)
            val shown = p * (1f - out)
            Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
                Text(
                    text,
                    color = Palette.TextPrimary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth(width)
                        .graphicsLayer {
                            alpha = shown
                            translationX = (1f - p) * (if (mine) 40.dp.toPx() else -40.dp.toPx())
                            val s = 0.8f + 0.2f * p
                            scaleX = s
                            scaleY = s
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when {
                                skin == Skin.TERMINAL -> Color.Transparent
                                mine -> WhatsAppGreen.copy(alpha = 0.2f)
                                else -> Palette.Surface2
                            },
                        )
                        .border(1.dp, if (skin == Skin.TERMINAL) accent.start.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
        val badge = ((t - 0.66f) / 0.08f).coerceIn(0f, 1f) * (1f - ((t - 0.9f) / 0.1f).coerceIn(0f, 1f))
        Row(
            Modifier
                .align(Alignment.End)
                .graphicsLayer {
                    alpha = badge
                    val s = 0.5f + 0.5f * badge + 0.15f * kotlin.math.sin(badge * Math.PI.toFloat())
                    scaleX = s
                    scaleY = s
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, tint = Palette.Danger, modifier = Modifier.size(20.dp))
            Text(skinText("Discussion.pdf"), color = Palette.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** How to export a discussion from WhatsApp, and what stays private. */
@Composable
internal fun ReaderHowTo(modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val steps = listOf(
        "Dans WhatsApp, ouvre la discussion, puis ⋮ › Plus › Exporter la discussion (iPhone : nom du contact ou du groupe › Exporter).",
        "« Avec médias » ajoute les photos dans les bulles et dans le PDF ; « Sans médias » est plus rapide.",
        "Enregistre le fichier (ou envoie-le-toi), puis choisis-le ici.",
    )
    Column(
        modifier
            .fillMaxWidth()
            .toolCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BlockLabel("Exporter une discussion")
        steps.forEachIndexed { i, step ->
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(accent.tint(0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${i + 1}", color = accent.start, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                Text(step, color = Palette.TextSecondary, fontSize = 13.5.sp, lineHeight = 19.sp, modifier = Modifier.weight(1f))
            }
        }
        Hint("Tout reste sur le téléphone : rien n'est envoyé, et rien n'est gardé en quittant l'outil (sauf le PDF si tu l'exportes).")
    }
}
