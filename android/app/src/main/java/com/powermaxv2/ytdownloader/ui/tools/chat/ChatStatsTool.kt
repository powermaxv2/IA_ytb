package com.powermaxv2.ytdownloader.ui.tools.chat

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.chat.ChatExport
import com.powermaxv2.ytdownloader.core.chat.ChatPhase
import com.powermaxv2.ytdownloader.core.chat.Gap
import com.powermaxv2.ytdownloader.core.chat.ReplyOptions
import com.powermaxv2.ytdownloader.core.chat.weight
import com.powermaxv2.ytdownloader.core.chat.WeekdayNames
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.shakeOnAppear
import com.powermaxv2.ytdownloader.ui.shimmer
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tools.FileDropZone
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.convert.BlockLabel
import com.powermaxv2.ytdownloader.ui.tools.convert.SideButton
import com.powermaxv2.ytdownloader.ui.tools.convert.toolCard

const val CHAT_TITLE = "Vu, répondu"
const val CHAT_COMMAND = "vu-repondu"
const val CHAT_SUBTITLE = "Qui répond le plus vite dans une discussion WhatsApp ? Partage son export : temps de réponse, podium, duos, records, top stickers, vocaux, calendrier."

/** Types offered by the file picker: WhatsApp exports are a .txt, or a .zip when shared. */
private val ExportTypes = arrayOf("text/plain", "application/zip", "application/x-zip-compressed", "application/octet-stream")

/** What the screen can ask for; every callback defaults to nothing (screenshot tests). */
data class ChatActions(
    val onChooseFile: () -> Unit = {},
    val onSample: () -> Unit = {},
    val onGap: (Gap) -> Unit = {},
    val onNights: (Boolean) -> Unit = {},
    val onRealWait: (Boolean) -> Unit = {},
)

/** One opening of a file: the same file picked twice is read twice. */
private data class ChatRequest(val uri: Uri, val serial: Int)

/**
 * "Vu, répondu" (ToolRegistry.CHAT_STATS), the app Maxime made, as a Gulp tool: a WhatsApp export picked here or
 * shared to Gulp is read and analysed on the phone. Nothing is sent or saved.
 */
@Composable
fun ChatStatsTool(vm: MainViewModel) {
    val context = LocalContext.current
    var request by remember { mutableStateOf<ChatRequest?>(null) }
    var phase by remember { mutableStateOf<ChatPhase>(ChatPhase.Idle) }
    var gap by rememberSaveable { mutableStateOf(Gap.DEFAULT) }
    var nights by rememberSaveable { mutableStateOf(false) }
    var realWait by rememberSaveable { mutableStateOf(false) }

    fun open(uri: Uri) {
        request = ChatRequest(uri, (request?.serial ?: 0) + 1)
    }

    // A file can arrive while the tool is already open (Partager → Gulp): keyed on the URI.
    LaunchedEffect(vm.pendingFile) {
        vm.consumePendingFile()?.let { open(it) }
    }
    LaunchedEffect(request) {
        val r = request ?: return@LaunchedEffect
        phase = ChatPhase.Loading
        // Cancelled when another file comes in: a stale read never writes over the new one.
        phase = ChatExport.load(context, r.uri)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) open(uri)
    }
    val actions = ChatActions(
        onChooseFile = {
            try {
                picker.launch(ExportTypes)
            } catch (e: ActivityNotFoundException) {
                vm.toast("Aucun sélecteur de fichiers sur ce téléphone.", success = false)
            }
        },
        onSample = {
            request = null
            phase = ChatExport.sample()
        },
        onGap = { gap = it },
        onNights = { nights = it },
        onRealWait = { realWait = it },
    )

    val scroll = rememberScrollState()
    LaunchedEffect(phase::class) { if (phase !is ChatPhase.Idle) scroll.animateScrollTo(0) }

    ToolScaffold(vm, title = CHAT_TITLE, command = CHAT_COMMAND, subtitle = CHAT_SUBTITLE, scrollState = scroll) {
        ChatContent(phase, ReplyOptions(gap, nights, realWait), actions)
    }
}

private enum class Step { IDLE, LOADING, READY, FAILED }

/** Body of the tool for a [phase] (stateless: also drawn by the screenshot tests). */
@Composable
fun ColumnScope.ChatContent(phase: ChatPhase, options: ReplyOptions, actions: ChatActions) {
    val step = when (phase) {
        ChatPhase.Idle -> Step.IDLE
        ChatPhase.Loading -> Step.LOADING
        is ChatPhase.Ready -> Step.READY
        is ChatPhase.Failed -> Step.FAILED
    }
    AnimatedContent(
        targetState = step,
        transitionSpec = {
            (fadeIn(tween(260, delayMillis = 60)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 300f)) { it / 10 }) togetherWith
                fadeOut(tween(160))
        },
        label = "chatStep",
        modifier = Modifier.fillMaxWidth(),
    ) { s ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (s) {
                Step.IDLE -> IdleStep(actions)
                Step.LOADING -> LoadingStep()
                Step.READY -> (phase as? ChatPhase.Ready)?.let { ReadyStep(it, options, actions) }
                Step.FAILED -> FailedStep((phase as? ChatPhase.Failed)?.message.orEmpty(), actions)
            }
        }
    }
}

// MARK: - Steps

@Composable
private fun IdleStep(actions: ChatActions) {
    FileDropZone(
        title = "Choisir un export",
        hint = "Fichier .txt ou .zip d'une discussion WhatsApp, ou Partager → Gulp",
        icon = Icons.Rounded.UploadFile,
        onClick = actions.onChooseFile,
    )
    ChatDemo(Modifier.staggeredEntrance(3, baseDelay = 160))
    SideButton("Voir un exemple", Icons.Rounded.AutoAwesome, Modifier.fillMaxWidth().staggeredEntrance(4, baseDelay = 160), onClick = actions.onSample)
    HowToCard()
}

/** How to export a WhatsApp discussion, then the privacy line. */
@Composable
private fun HowToCard() {
    val terminal = LocalSkin.current == Skin.TERMINAL
    val steps = listOf(
        "Ouvre la discussion dans WhatsApp.",
        "Android : ⋮ › Plus › Exporter la discussion. iPhone : nom du groupe › Exporter la discussion. « Sans médias » suffit pour les temps de réponse ; « Avec médias » ajoute le top stickers, la durée des vocaux, le poids envoyé par chacun et les dernières photos.",
        "Dans la liste de partage, choisis « Analyser avec Gulp » pour un .zip, « Télécharger avec Gulp » pour un .txt : l'analyse s'ouvre directement. Ou enregistre le fichier puis choisis-le ici.",
    )
    val accent = LocalAccent.current
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(5, baseDelay = 160)
            .toolCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BlockLabel("Comment exporter une discussion")
        steps.forEachIndexed { i, text ->
            Row(Modifier.staggeredEntrance(i, baseDelay = 300), verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(if (terminal) 3.dp else 12.dp))
                        .background(accent.tint(0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${i + 1}", color = accent.start, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.width(12.dp))
                Text(text, color = Palette.TextPrimary, fontSize = 13.5.sp, lineHeight = 19.sp, modifier = Modifier.weight(1f))
            }
        }
        PrivacyLine()
    }
}

@Composable
private fun PrivacyLine(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Lock, contentDescription = null, tint = Palette.TextTertiary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            "Analyse 100 % sur le téléphone : la discussion n'est envoyée nulle part ni enregistrée.",
            color = Palette.TextTertiary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
    }
}

@Composable
private fun LoadingStep() {
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
            Text(
                if (LocalSkin.current == Skin.TERMINAL) "LECTURE DE LA DISCUSSION…" else "Lecture de la discussion…",
                color = Palette.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        listOf(0.9f, 0.6f, 0.75f, 0.4f, 0.85f).forEach { w ->
            Box(
                Modifier
                    .fillMaxWidth(w)
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Palette.Surface2),
            )
        }
    }
}

@Composable
private fun ReadyStep(ready: ChatPhase.Ready, options: ReplyOptions, actions: ChatActions) {
    val stats = ready.stats
    val gap = options.gap
    val replies = remember(stats, options) { stats.replies(options) }
    val media = stats.media
    // Cards replay their entrance for a new discussion; bars and numbers re-run when the options change.
    val key = stats
    val again = key to options
    var index = 0
    fun next() = index++
    SourceCard(stats, ready.name, ready.sample, options, actions, key)
    KpiGrid(stats, replies, key = again)
    StatCard("Podium", "Les plus rapides", next(), key, sub = "Classés par temps de réponse moyen (au moins 3 réponses).") {
        Podium(replies, key = again)
    }
    StatCard("Course", "Temps de réponse par personne", next(), key, sub = "Moyenne de chaque réponse ; barre courte = réponse rapide.") {
        Race(replies, key = again)
    }
    if (replies.pairs.isNotEmpty()) {
        StatCard("Duos", "Qui répond à qui", next(), key, sub = "Les paires les plus rapides : le temps moyen que met l'un à répondre à l'autre.") {
            Duos(replies, key = again)
        }
    }
    StatCard("Fiches", "Chacun en détail", next(), key, sub = "Les chiffres de chaque personne, du plus rapide au plus lent.") {
        Profiles(stats, replies, key = again)
    }
    StatCard(
        "Volume",
        "Messages envoyés",
        next(),
        key,
        sub = "${count(stats.messages.size)} messages, dont ${count(stats.mediaCount)} médias et stickers.",
    ) {
        Volume(stats, key)
    }
    if (media != null && media.stickers.isNotEmpty()) {
        StatCard(
            "Top stickers",
            "Les stickers fétiches",
            next(),
            key,
            sub = "Reconnus par leur image, même envoyés sous des noms différents : ${count(media.stickerSends)} envois, ${count(media.stickers.size)} stickers différents.",
        ) {
            TopStickers(stats, media, key)
        }
    }
    StatCard(
        "Médias",
        if (stats.stickersKnown) "Stickers, photos, vocaux" else "Médias et stickers",
        next(),
        key,
        sub = if (stats.stickersKnown) "${count(stats.stickers)} stickers envoyés." else null,
    ) {
        MediaCard(stats, key)
    }
    if (media != null && media.voiceCount > 0) {
        StatCard("Vocaux", "Qui parle le plus", next(), key, sub = "Durée lue dans chaque vocal du .zip.") {
            Vocals(stats, media, key)
        }
    }
    if (media != null && media.totalBytes > 0) {
        StatCard("Poids", "Qui remplit le téléphone", next(), key, sub = "${weight(media.totalBytes)} envoyés en tout : photos, vidéos, vocaux, stickers, documents.") {
            Weight(stats, media, key)
        }
    }
    if (media != null && media.photos.isNotEmpty()) {
        StatCard("Galerie", "Les dernières photos", next(), key, sub = "Vignettes gardées en mémoire le temps de l'analyse, rien n'est enregistré.") {
            Gallery(media, key)
        }
    }
    StatCard("Délais", "Répartition des temps de réponse", next(), key) {
        Histogram(replies)
    }
    StatCard("Conversations", "Qui lance, qui conclut", next(), key, sub = "Une conversation commence après ${gap.label} sans message.".takeIf { gap != Gap.NEVER } ?: "Toute la discussion compte comme une seule conversation.") {
        Conversations(stats, replies, key = again)
    }
    StatCard("Records", "Les records de la discussion", next(), key) {
        Records(stats, replies, key = again)
    }
    StatCard(
        "Horloge",
        "Heures et jours d'activité",
        next(),
        key,
        sub = "Pic vers ${stats.peakHour} h (${count(stats.hours[stats.peakHour])} messages), surtout le ${WeekdayNames[stats.peakWeekday]}.",
    ) {
        Hours(stats)
        Weekdays(stats)
    }
    StatCard("Calendrier", "Chaque jour de la discussion", next(), key, sub = "Un carré par jour, plus foncé quand ça parle beaucoup.") {
        Calendar(stats, key)
    }
    if (stats.periods.size >= 2) {
        StatCard("Évolution", if (stats.weekly) "Messages et réponses par semaine" else "Messages et réponses par mois", next(), key) {
            Evolution(stats, replies, key = again)
        }
    }
    StatCard("Refrain", "Messages les plus utilisés", next(), key, sub = "Majuscules et ponctuation finale ignorées. Médias et messages supprimés exclus.") {
        Refrains(stats, key)
    }
    StatCard("Mots", "Mots favoris et liens", next(), key, sub = "Les mots les plus écrits par chacun, mots courants (le, de, et…) retirés.") {
        Words(stats, key)
    }
    StatCard("Bonus", "Emojis favoris", next(), key) {
        Emojis(stats, key)
    }
    SideButton(
        if (ready.sample) "Choisir ma discussion" else "Choisir une autre discussion",
        Icons.Rounded.UploadFile,
        Modifier.fillMaxWidth(),
        onClick = actions.onChooseFile,
    )
    PrivacyLine(Modifier.padding(horizontal = 4.dp))
}

@Composable
private fun FailedStep(message: String, actions: ChatActions) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Column(
        Modifier
            .fillMaxWidth()
            .shakeOnAppear(message)
            .toolCard()
            .background(Palette.Danger.copy(alpha = 0.08f))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Palette.Danger, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                if (terminal) "ERREUR" else "Analyse impossible",
                color = Palette.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(message, color = Palette.TextPrimary, fontSize = 14.sp, lineHeight = 20.sp)
    }
    SideButton("Choisir un autre fichier", Icons.Rounded.UploadFile, Modifier.fillMaxWidth(), onClick = actions.onChooseFile)
    SideButton("Voir un exemple", Icons.Rounded.AutoAwesome, Modifier.fillMaxWidth(), onClick = actions.onSample)
}
