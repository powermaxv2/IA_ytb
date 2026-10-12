package com.powermaxv2.ytdownloader.ui.tools.sticker

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powermaxv2.ytdownloader.App
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.sticker.StickerClip
import com.powermaxv2.ytdownloader.core.sticker.StickerOutline
import com.powermaxv2.ytdownloader.core.sticker.StickerPack
import com.powermaxv2.ytdownloader.core.sticker.StickerPhase
import com.powermaxv2.ytdownloader.core.sticker.StickerRules
import com.powermaxv2.ytdownloader.core.sticker.StickerShape
import com.powermaxv2.ytdownloader.core.sticker.StickerStyle
import com.powermaxv2.ytdownloader.core.sticker.StickerUiState
import com.powermaxv2.ytdownloader.core.sticker.StickerVariant
import com.powermaxv2.ytdownloader.core.sticker.WhatsAppStickers
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.shimmer
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tools.FileDropZone
import com.powermaxv2.ytdownloader.ui.tools.ToolErrorCard
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.compress.Hint
import com.powermaxv2.ytdownloader.ui.tools.compress.SwitchRow
import com.powermaxv2.ytdownloader.ui.tools.convert.BlockLabel
import com.powermaxv2.ytdownloader.ui.tools.convert.MainButton
import com.powermaxv2.ytdownloader.ui.tools.convert.OptionChips
import com.powermaxv2.ytdownloader.ui.tools.convert.SideButton
import com.powermaxv2.ytdownloader.ui.tools.convert.TerminalProgress
import com.powermaxv2.ytdownloader.ui.tools.convert.toolCard
import com.powermaxv2.ytdownloader.ui.tools.qr.QrField
import java.util.Locale

const val STICKER_TITLE = "Stickers"
const val STICKER_COMMAND = "sticker"
const val STICKER_SUBTITLE = "Une photo, une vidéo ou un GIF devient un sticker WhatsApp, ajouté direct dans WhatsApp."

/** What the screen can ask for; every callback defaults to nothing (screenshot tests). */
data class StickerActions(
    val onPhotos: () -> Unit = {},
    val onVideo: () -> Unit = {},
    val onAdd: () -> Unit = {},
    val onSelect: (Int) -> Unit = {},
    val onRemove: () -> Unit = {},
    val onStyle: (StickerStyle) -> Unit = {},
    val onTouch: (Float, Float) -> Unit = { _, _ -> },
    val onClearTouches: () -> Unit = {},
    val onClip: (StickerClip) -> Unit = {},
    val onClipSettled: () -> Unit = {},
    val onName: (String) -> Unit = {},
    val onEmoji: (String) -> Unit = {},
    val onStart: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onReset: () -> Unit = {},
    val onBack: () -> Unit = {},
    val onAddToWhatsApp: (StickerPack) -> Unit = {},
    val onSave: () -> Unit = {},
    val onOpenPack: (StickerPack) -> Unit = {},
    val onDeletePack: (StickerPack) -> Unit = {},
)

/**
 * The Stickers tool (ToolRegistry.STICKERS): photos, a video or a GIF become a WhatsApp sticker pack. Its state lives
 * in [App.stickers], so a pack being made keeps going when the user leaves this screen.
 */
@Composable
fun StickerTool(vm: MainViewModel) {
    val controller = remember { App.instance.stickers }
    val state by controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { controller.refreshPacks() }

    val photos = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty() && !controller.pick(uris)) vm.toast("Un pack est en cours de création.", success = false)
    }
    val more = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty() && !controller.pick(uris, add = true)) vm.toast("Un pack est en cours de création.", success = false)
    }
    val video = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !controller.pick(listOf(uri))) vm.toast("Un pack est en cours de création.", success = false)
    }
    val whatsApp = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val error = result.data?.getStringExtra(WhatsAppStickers.VALIDATION_ERROR)
        when {
            result.resultCode == Activity.RESULT_OK -> vm.toast("Pack ajouté à WhatsApp", success = true)
            error != null -> vm.toast("WhatsApp a refusé le pack : $error", success = false)
        }
    }
    // Android 9 and older: saving to Pictures needs the storage permission.
    val storage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) controller.saveToGallery() else vm.toast("Gulp a besoin de l'accès au stockage pour enregistrer les stickers.", success = false)
    }
    fun launch(block: () -> Unit) {
        try {
            block()
        } catch (e: ActivityNotFoundException) {
            vm.toast("Aucun sélecteur de fichiers sur ce téléphone.", success = false)
        }
    }

    val actions = StickerActions(
        onPhotos = { launch { photos.launch(PHOTO_TYPES) } },
        onVideo = { launch { video.launch(MOTION_TYPES) } },
        onAdd = { launch { more.launch(PHOTO_TYPES) } },
        onSelect = controller::select,
        onRemove = controller::removeSelected,
        onStyle = controller::setStyle,
        onTouch = controller::touch,
        onClearTouches = controller::clearTouches,
        onClip = controller::setClip,
        onClipSettled = controller::settleClip,
        onName = controller::setName,
        onEmoji = controller::setEmoji,
        onStart = controller::start,
        onCancel = controller::cancel,
        onReset = controller::reset,
        onBack = controller::backToEdit,
        onAddToWhatsApp = { pack ->
            val intent = WhatsAppStickers.addIntent(context, pack)
            if (intent == null) {
                vm.toast("WhatsApp n'est pas installé sur ce téléphone.", success = false)
            } else {
                try {
                    whatsApp.launch(intent)
                } catch (e: ActivityNotFoundException) {
                    vm.toast("Cette version de WhatsApp n'accepte pas les packs de stickers.", success = false)
                }
            }
        },
        onSave = {
            val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
            if (needsPermission) storage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else controller.saveToGallery()
        },
        onOpenPack = controller::openPack,
        onDeletePack = { controller.deletePack(it.id) },
    )

    val scroll = rememberScrollState()
    LaunchedEffect(state.phase::class) { if (state.phase !is StickerPhase.Ready) scroll.animateScrollTo(0) }
    LaunchedEffect((state.phase as? StickerPhase.Done)?.saveFailed) {
        if ((state.phase as? StickerPhase.Done)?.saveFailed == true) vm.toast("Les stickers n'ont pas pu être enregistrés.", success = false)
    }

    ToolScaffold(vm, title = STICKER_TITLE, command = STICKER_COMMAND, subtitle = STICKER_SUBTITLE, scrollState = scroll) {
        StickerContent(state, actions)
    }
}

private val PHOTO_TYPES = arrayOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif")
private val MOTION_TYPES = arrayOf("video/*", "image/gif")

/** Body of the tool for a [state] (stateless: also drawn by the screenshot tests). */
@Composable
fun ColumnScope.StickerContent(state: StickerUiState, actions: StickerActions) {
    AnimatedContent(
        targetState = state,
        contentKey = { it.phase::class },
        transitionSpec = {
            (fadeIn(tween(260, delayMillis = 60)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 300f)) { it / 10 }) togetherWith
                fadeOut(tween(160))
        },
        label = "stickerPhase",
        modifier = Modifier.fillMaxWidth(),
    ) { current ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (val phase = current.phase) {
                StickerPhase.Idle -> IdleStep(current, actions)
                StickerPhase.Reading -> ReadingStep()
                is StickerPhase.Unsupported -> UnsupportedStep(phase, actions)
                StickerPhase.Ready -> ReadyStep(current, actions)
                is StickerPhase.Working -> WorkingStep(current, phase, actions)
                is StickerPhase.Done -> DoneStep(current, phase, actions)
                is StickerPhase.Failed -> FailedStep(current, phase, actions)
            }
        }
    }
}

// MARK: - Steps

@Composable
private fun IdleStep(state: StickerUiState, actions: StickerActions) {
    FileDropZone(
        title = "Choisir des photos",
        hint = "Une ou plusieurs (jusqu'à 30) · chacune devient un sticker",
        icon = StickerIcon,
        onClick = actions.onPhotos,
    )
    SideButton("Une vidéo ou un GIF (sticker animé)", Icons.Rounded.Movie, Modifier.fillMaxWidth().staggeredEntrance(3, baseDelay = 160), onClick = actions.onVideo)
    StepCard(4) {
        BlockLabel("Comment ça marche")
        Gap(8.dp)
        HowLine("1", "Gulp recadre en carré, ajoute un contour blanc, une forme, du texte. La baguette magique retire un fond uni.")
        HowLine("2", "Il range tout dans un pack WhatsApp : au moins 3 stickers, 512 px, assez légers pour WhatsApp.")
        HowLine("3", "« Ajouter à WhatsApp » : WhatsApp s'ouvre et te demande d'ajouter le pack.")
    }
    if (state.packs.isNotEmpty()) {
        StepCard(5) {
            BlockLabel(if (state.packs.size > 1) "Mes ${state.packs.size} packs" else "Mon pack")
            Gap(4.dp)
            state.packs.forEachIndexed { i, saved ->
                PackRow(saved, i, onOpen = { actions.onOpenPack(saved.pack) }, onDelete = { actions.onDeletePack(saved.pack) })
            }
        }
    }
    Hint(
        if (state.whatsApp) {
            "Tout se fait sur le téléphone, hors ligne. Les packs restent dans Gulp : si tu désinstalles Gulp, ils disparaissent de WhatsApp."
        } else {
            "WhatsApp n'est pas installé : tu pourras quand même enregistrer les stickers en .webp dans la Galerie."
        },
        warning = !state.whatsApp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
}

@Composable
private fun HowLine(number: String, text: String) {
    val accent = LocalAccent.current
    val terminal = LocalSkin.current == Skin.TERMINAL
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(if (terminal) 3.dp else 12.dp))
                .background(accent.start.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(number, color = accent.start, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, color = Palette.TextSecondary, fontSize = 13.5.sp, lineHeight = 19.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ReadingStep() {
    Row(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .shimmer()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Palette.TextPrimary.copy(alpha = 0.08f)),
        )
        Spacer(Modifier.width(14.dp))
        Text("Lecture des images…", color = Palette.TextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun UnsupportedStep(phase: StickerPhase.Unsupported, actions: StickerActions) {
    ToolErrorCard(phase.message)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SideButton("Photos", StickerIcon, Modifier.weight(1f), onClick = actions.onPhotos)
        SideButton("Vidéo ou GIF", Icons.Rounded.Movie, Modifier.weight(1f), onClick = actions.onVideo)
    }
}

@Composable
private fun ReadyStep(state: StickerUiState, actions: StickerActions) {
    val style = state.style
    val plan = state.plan
    val selected = state.selected.coerceIn(0, (plan.size - 1).coerceAtLeast(0))
    val variant = plan.getOrNull(selected)?.second ?: StickerVariant.NORMAL
    val motion = state.motion
    val wand = motion == null && variant != StickerVariant.TILT
    val touches = plan.getOrNull(selected)?.first?.let { state.seeds[it] }.orEmpty()

    // The sticker, and the pack under it.
    StepCard(0) {
        StickerStage(
            state.preview,
            key = Triple(style.shape, style.outline, selected),
            caption = when {
                motion != null -> "Animé · ${seconds(state.clip.lengthMs)}${if (state.clip.boomerang) " aller-retour" else ""}"
                variant == StickerVariant.MIRROR -> "Variante en miroir, pour compléter le pack"
                variant == StickerVariant.TILT -> "Variante penchée, pour compléter le pack"
                variant == StickerVariant.NO_TEXT -> "Variante sans texte, pour compléter le pack"
                variant == StickerVariant.REVERSE -> "Variante à l'envers, pour compléter le pack"
                else -> "Touche le fond pour l'effacer"
            },
            onTouch = if (wand) actions.onTouch else null,
        )
        Gap()
        StickerStrip(
            state.thumbs,
            plan.size,
            selected,
            onSelect = actions.onSelect,
            onAdd = actions.onAdd.takeIf { motion == null && state.photos.size < StickerRules.MAX_STICKERS },
        )
        Gap(10.dp)
        val extra = plan.size - state.count
        Hint(
            StickerRules.countText(plan.size, state.animated) +
                if (extra > 0) " : WhatsApp en veut 3 au moins, Gulp ajoute ${if (extra > 1) "$extra variantes" else "1 variante"}." else ".",
        )
        if (touches.isNotEmpty() || (motion == null && state.photos.size > 1 && variant == StickerVariant.NORMAL)) {
            Gap(10.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (touches.isNotEmpty()) SideButton("Annuler les touches", Icons.Rounded.Undo, Modifier.weight(1f), onClick = actions.onClearTouches)
                if (motion == null && state.photos.size > 1 && variant == StickerVariant.NORMAL) {
                    SideButton("Retirer", null, Modifier.weight(1f), onClick = actions.onRemove)
                }
            }
        }
    }

    if (motion != null) {
        StepCard(1) {
            BlockLabel(if (motion.gif) "Passage du GIF" else "Passage de la vidéo")
            Gap(8.dp)
            OptionChips(
                StickerRules.CLIP_CHOICES.filter { it <= motion.durationMs.coerceAtLeast(StickerRules.CLIP_CHOICES.first()) },
                state.clip.lengthMs.let { l -> StickerRules.CLIP_CHOICES.minByOrNull { kotlin.math.abs(it - l) } ?: l },
                label = { seconds(it) },
                onSelect = { actions.onClip(state.clip.copy(lengthMs = it)) },
            )
            if (motion.durationMs > state.clip.lengthMs + 100) {
                Gap(10.dp)
                val accent = LocalAccent.current
                Text(
                    "Début : ${seconds(state.clip.startMs)} sur ${seconds(motion.durationMs)}",
                    color = Palette.TextSecondary,
                    fontSize = 13.sp,
                )
                Slider(
                    value = state.clip.startMs.toFloat(),
                    onValueChange = { actions.onClip(state.clip.copy(startMs = it.toLong())) },
                    onValueChangeFinished = actions.onClipSettled,
                    valueRange = 0f..(motion.durationMs - state.clip.lengthMs).coerceAtLeast(1).toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = accent.start,
                        activeTrackColor = accent.start,
                        inactiveTrackColor = Palette.TextPrimary.copy(alpha = 0.12f),
                    ),
                )
            }
            Gap(4.dp)
            SwitchRow("Aller-retour", "Le passage se joue puis se rembobine, en boucle.", state.clip.boomerang) {
                actions.onClip(state.clip.copy(boomerang = it))
            }
        }
    }

    StepCard(2) {
        BlockLabel("Forme")
        Gap(8.dp)
        OptionChips(StickerShape.entries, style.shape, label = { it.label }, onSelect = { actions.onStyle(style.copy(shape = it)) })
        Gap(14.dp)
        BlockLabel("Contour blanc")
        Gap(8.dp)
        OptionChips(StickerOutline.entries, style.outline, label = { it.label }, onSelect = { actions.onStyle(style.copy(outline = it)) })
        Gap(14.dp)
        SwitchRow("En miroir", "Retourne tous les stickers de gauche à droite.", style.mirror) { actions.onStyle(style.copy(mirror = it)) }
    }

    StepCard(3) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BlockLabel("Baguette magique", Modifier.weight(1f))
            androidx.compose.material3.Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, tint = LocalAccent.current.start, modifier = Modifier.size(18.dp))
        }
        Gap(8.dp)
        SwitchRow("Retirer le fond uni", "Efface la couleur qui touche les bords (mur, ciel, table…).", style.cutout) {
            actions.onStyle(style.copy(cutout = it))
        }
        AnimatedVisibility(style.cutout || touches.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column {
                Gap(10.dp)
                Text("Tolérance", color = Palette.TextSecondary, fontSize = 13.sp)
                Gap(6.dp)
                OptionChips(TOLERANCES, TOLERANCES.minByOrNull { kotlin.math.abs(it.second - style.tolerance) }!!, label = { it.first }, onSelect = {
                    actions.onStyle(style.copy(tolerance = it.second))
                })
            }
        }
        if (motion == null) {
            Gap(8.dp)
            Hint("Touche aussi le fond sur l'aperçu : chaque touche efface la zone de cette couleur.")
        }
    }

    StepCard(4) {
        BlockLabel("Texte")
        Gap(8.dp)
        QrField("Texte sur le sticker", style.text, onChange = { actions.onStyle(style.copy(text = it)) }, placeholder = "Ex. : MDR, Trop fort…", maxLength = 40)
        AnimatedVisibility(style.hasText, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column {
                Gap(10.dp)
                OptionChips(listOf(false, true), style.textOnTop, label = { if (it) "En haut" else "En bas" }, onSelect = { actions.onStyle(style.copy(textOnTop = it)) })
            }
        }
    }

    StepCard(5) {
        BlockLabel("Pack")
        Gap(8.dp)
        QrField("Nom du pack", state.name, onChange = actions.onName, placeholder = StickerRules.DEFAULT_NAME, maxLength = StickerRules.NAME_MAX)
        Gap(12.dp)
        Text("Emoji des stickers", color = Palette.TextSecondary, fontSize = 13.sp)
        Gap(6.dp)
        EmojiRow(state.emoji, actions.onEmoji)
    }

    MainButton(
        "Créer le pack",
        subtitle = StickerRules.countText(plan.size, state.animated) + if (state.whatsApp) " · puis ajout à WhatsApp" else "",
        icon = StickerIcon,
        modifier = Modifier.staggeredEntrance(6, baseDelay = 60),
        onClick = actions.onStart,
    )
    SideButton("Recommencer", Icons.Rounded.Refresh, Modifier.fillMaxWidth().staggeredEntrance(7, baseDelay = 60), onClick = actions.onReset)
}

private val TOLERANCES = listOf("Précise" to 24, "Normale" to 42, "Large" to 72)

private fun seconds(ms: Long): String {
    val s = ms / 1000.0
    return if (ms % 1000 == 0L) "${ms / 1000} s" else String.format(Locale.FRANCE, "%.1f s", s)
}

@Composable
private fun WorkingStep(state: StickerUiState, phase: StickerPhase.Working, actions: StickerActions) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    val progress by animateFloatAsState(phase.progress.coerceAtLeast(0f), spring(dampingRatio = 0.9f, stiffness = 120f), label = "stickerProgress")
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PeelingSticker(state.preview)
        Spacer(Modifier.height(18.dp))
        Text(
            if (terminal) "> ${phase.step}" else phase.step,
            color = Palette.TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            StickerRules.countText(state.plan.size, state.animated) + " · 512 px · WebP",
            color = Palette.TextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        if (terminal) TerminalProgress(progress) else ProgressBar(if (phase.progress < 0) -1f else progress)
    }
    Hint(
        "Tu peux revenir à l'accueil : le pack continue de se faire tant que Gulp reste ouvert.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
    SideButton("Annuler", null, Modifier.fillMaxWidth(), onClick = actions.onCancel)
}

@Composable
private fun ProgressBar(progress: Float) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(shape)
            .background(Palette.TextPrimary.copy(alpha = 0.08f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(if (progress < 0) 1f else progress.coerceIn(0.04f, 1f))
                .fillMaxHeight()
                .clip(shape)
                .background(if (LocalSkin.current == Skin.CLASSIC) accent.horizontal() else Brush.horizontalGradient(listOf(accent.start, accent.start)))
                .shimmer(),
        )
    }
}

@Composable
private fun DoneStep(state: StickerUiState, phase: StickerPhase.Done, actions: StickerActions) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    val pack = phase.pack
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (terminal) pack.name.uppercase() else pack.name,
            color = Palette.TextPrimary,
            fontSize = 26.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = LocalTokens.current.display,
            textAlign = TextAlign.Center,
            modifier = Modifier.staggeredEntrance(0, baseDelay = 120),
        )
        Spacer(Modifier.height(4.dp))
        val line = "${pack.emoji}  ${StickerRules.countText(pack.stickers.size, pack.animated)} · ${StickerRules.kiloText(pack.bytes.toInt())} · prêt pour WhatsApp"
        Text(
            if (terminal) "# $line" else line,
            color = Palette.TextSecondary,
            fontSize = 13.5.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.staggeredEntrance(1, baseDelay = 120),
        )
        Spacer(Modifier.height(18.dp))
        PackGrid(phase.stickers, phase.sizes)
    }
    MainButton(
        "Ajouter à WhatsApp",
        subtitle = if (state.whatsApp) "WhatsApp s'ouvre et demande d'ajouter le pack" else "WhatsApp n'est pas installé",
        icon = StickerIcon,
        enabled = state.whatsApp,
        modifier = Modifier.staggeredEntrance(2, baseDelay = 200),
    ) { actions.onAddToWhatsApp(pack) }
    Row(Modifier.fillMaxWidth().staggeredEntrance(3, baseDelay = 200), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SideButton(if (phase.saved.isNotEmpty()) "Dans la Galerie" else "Galerie (.webp)", Icons.Rounded.Image, Modifier.weight(1f), onClick = actions.onSave)
        SideButton("Nouveau pack", StickerIcon, Modifier.weight(1f), onClick = actions.onReset)
    }
    if (phase.saved.isNotEmpty()) {
        Hint(
            "${phase.saved.size} stickers enregistrés dans Galerie › Gulp.",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun FailedStep(state: StickerUiState, phase: StickerPhase.Failed, actions: StickerActions) {
    ToolErrorCard(phase.message, phase.debug, title = "Le pack n'a pas pu être fait")
    if (state.count > 0) {
        MainButton("Réessayer", icon = Icons.Rounded.Refresh, onClick = actions.onStart)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SideButton("Réglages", Icons.Rounded.Tune, Modifier.weight(1f), onClick = actions.onBack)
            SideButton("Autres images", StickerIcon, Modifier.weight(1f), onClick = actions.onReset)
        }
    } else {
        SideButton("Choisir d'autres images", StickerIcon, Modifier.fillMaxWidth(), onClick = actions.onReset)
    }
}
