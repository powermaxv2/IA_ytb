package com.powermaxv2.ytdownloader.ui.tools.photodup

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoDelete
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powermaxv2.ytdownloader.App
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.photodup.DupKind
import com.powermaxv2.ytdownloader.core.photodup.DupPhase
import com.powermaxv2.ytdownloader.core.photodup.DupUiState
import com.powermaxv2.ytdownloader.core.photodup.GalleryAccess
import com.powermaxv2.ytdownloader.core.photodup.DupGroup
import com.powermaxv2.ytdownloader.core.photodup.Photo
import com.powermaxv2.ytdownloader.core.photodup.PhotoDupFacts
import com.powermaxv2.ytdownloader.ui.AnimatedCheck
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.SuccessBurst
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.shakeOnAppear
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tools.FileDropZone
import com.powermaxv2.ytdownloader.ui.tools.ToolErrorCard
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.cleaner.CountingBytes
import com.powermaxv2.ytdownloader.ui.tools.compress.Hint
import com.powermaxv2.ytdownloader.ui.tools.convert.BlockLabel
import com.powermaxv2.ytdownloader.ui.tools.convert.MainButton
import com.powermaxv2.ytdownloader.ui.tools.convert.OptionChips
import com.powermaxv2.ytdownloader.ui.tools.convert.SideButton
import com.powermaxv2.ytdownloader.ui.tools.convert.TerminalProgress
import com.powermaxv2.ytdownloader.ui.tools.convert.toolCard

// The top bar has room for one short title.
const val PHOTO_DUP_TITLE = "Doublons photos"
const val PHOTO_DUP_COMMAND = "dedup"
const val PHOTO_DUP_SUBTITLE = "Trouve les photos en double ou presque pareilles, garde la meilleure et libère de la place."

/** What the screen can ask for; every callback defaults to nothing (screenshot tests). */
data class PhotoDupActions(
    val onAllow: () -> Unit = {},
    val onSettings: () -> Unit = {},
    val onAlbum: (String?) -> Unit = {},
    val onScan: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onKind: (DupKind) -> Unit = {},
    val onToggle: (Photo) -> Unit = {},
    val onKeepBest: (DupGroup) -> Unit = {},
    val onKeepBestEverywhere: (DupKind) -> Unit = {},
    val onTrash: (Boolean) -> Unit = {},
    val onAskConfirm: () -> Unit = {},
    val onDismissConfirm: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onBackToResults: () -> Unit = {},
    /** Picture of a photo; null = coloured tiles only. */
    val thumbnail: (suspend (Photo) -> Bitmap?)? = null,
    /** False on Android 10 and older: no bin, the photos are deleted for good. */
    val canTrash: Boolean = true,
)

/**
 * Doublons photos (ToolRegistry.PHOTO_DUPLICATES): finds the photos of the gallery that are copies of each other
 * (byte for byte) or look the same (resized, recompressed, bursts), suggests the one to keep, and deletes the others
 * the user checked, through Android's own confirmation. Its state lives in [App.photoDuplicates].
 */
@Composable
fun PhotoDupTool(vm: MainViewModel) {
    val controller = remember { App.instance.photoDuplicates }
    val state by controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // The permissions are checked on open and each time Gulp comes back (the user may have changed them in Settings).
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) controller.setAccess(galleryAccess(context)) }
        lifecycle.addObserver(observer)
        controller.setAccess(galleryAccess(context))
        onDispose { lifecycle.removeObserver(observer) }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val access = galleryAccess(context)
        controller.setAccess(access)
        if (access == GalleryAccess.NONE) vm.toast("Sans accès aux photos, Gulp ne peut pas chercher les doublons.", success = false)
    }
    val system = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        controller.onSystemAnswer(result.resultCode == Activity.RESULT_OK)
    }

    val actions = PhotoDupActions(
        onAllow = { permission.launch(galleryPermissions()) },
        onSettings = {
            try {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
            } catch (e: ActivityNotFoundException) {
                vm.toast("Réglages introuvables sur ce téléphone.", success = false)
            }
        },
        onAlbum = controller::setAlbum,
        onScan = controller::scan,
        onCancel = controller::cancel,
        onKind = controller::setKind,
        onToggle = controller::toggle,
        onKeepBest = controller::keepBest,
        onKeepBestEverywhere = controller::keepBestEverywhere,
        onTrash = controller::setTrash,
        onAskConfirm = controller::askConfirm,
        onDismissConfirm = controller::dismissConfirm,
        onDelete = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val request: IntentSender? = controller.deleteRequest()
                if (request == null) {
                    vm.toast("Android a refusé la suppression.", success = false)
                } else {
                    try {
                        system.launch(IntentSenderRequest.Builder(request).build())
                    } catch (e: ActivityNotFoundException) {
                        controller.onSystemAnswer(false)
                    }
                }
            } else {
                controller.deleteDirectly()
            }
        },
        onBackToResults = controller::backToResults,
        thumbnail = controller::thumbnail,
        canTrash = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
    )

    val scroll = rememberScrollState()
    LaunchedEffect(state.phase::class) { if (state.phase !is DupPhase.Ready) scroll.animateScrollTo(0) }

    ToolScaffold(vm, title = PHOTO_DUP_TITLE, command = PHOTO_DUP_COMMAND, subtitle = PHOTO_DUP_SUBTITLE, scrollState = scroll) {
        PhotoDupContent(state, actions)
    }
}

/** The permissions to ask for reading (and, on Android 9 and older, deleting) the photos. */
private fun galleryPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
}

private fun galleryAccess(context: Context): GalleryAccess {
    fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && granted(Manifest.permission.READ_MEDIA_IMAGES) -> GalleryAccess.FULL
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> GalleryAccess.PARTIAL
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && granted(Manifest.permission.READ_EXTERNAL_STORAGE) &&
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || granted(Manifest.permission.WRITE_EXTERNAL_STORAGE)) -> GalleryAccess.FULL
        else -> GalleryAccess.NONE
    }
}

/** Body of the tool for a [state] (stateless: also drawn by the screenshot tests). */
@Composable
fun ColumnScope.PhotoDupContent(state: DupUiState, actions: PhotoDupActions) {
    AnimatedContent(
        targetState = state,
        contentKey = { it.phase::class },
        transitionSpec = {
            (fadeIn(tween(260, delayMillis = 60)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 300f)) { it / 10 }) togetherWith
                fadeOut(tween(160))
        },
        label = "photoDupPhase",
        modifier = Modifier.fillMaxWidth(),
    ) { current ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (val phase = current.phase) {
                DupPhase.Idle -> IdleStep(current, actions)
                is DupPhase.Scanning -> ScanningStep(phase, actions)
                DupPhase.Ready -> ReadyStep(current, actions)
                is DupPhase.Deleting -> DeletingStep(phase)
                is DupPhase.Done -> DoneStep(phase, actions)
                is DupPhase.Failed -> FailedStep(phase, actions)
            }
        }
    }
}

// MARK: - Steps

@Composable
private fun IdleStep(state: DupUiState, actions: PhotoDupActions) {
    if (state.access == GalleryAccess.NONE) {
        FileDropZone(
            title = "Autoriser l'accès aux photos",
            hint = "Gulp les lit sur le téléphone, rien n'est envoyé",
            icon = PhotoDupIcon,
            onClick = actions.onAllow,
        )
    } else {
        Column(
            Modifier
                .fillMaxWidth()
                .staggeredEntrance(0, baseDelay = 40)
                .toolCard()
                .padding(16.dp),
        ) {
            BlockLabel("Où chercher")
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MatchScan(progress = 0f, size = 64.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    val where = state.album ?: "Toute la galerie"
                    Text(where, color = Palette.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                    Spacer(Modifier.height(2.dp))
                    val count = state.album?.let { a -> state.albums.firstOrNull { it.name == a }?.count } ?: state.galleryCount
                    val sub = if (state.albums.isEmpty()) "Lecture des albums…" else "${PhotoDupFacts.photos(count)} à comparer"
                    Text(sub, color = Palette.TextSecondary, fontSize = 13.sp)
                }
            }
            if (state.albums.size > 1) {
                Spacer(Modifier.height(14.dp))
                val options = listOf<String?>(null) + state.albums.take(ALBUMS_SHOWN).map { it.name }
                OptionChips(
                    options = options,
                    selected = state.album,
                    label = { name -> name ?: "Toute la galerie" },
                    onSelect = actions.onAlbum,
                )
            }
            if (state.access == GalleryAccess.PARTIAL) {
                Spacer(Modifier.height(12.dp))
                Hint("Android ne montre à Gulp que les photos que tu as choisies. Pour toute la galerie, autorise « Toutes les photos ».", warning = true)
            }
        }
        MainButton("Chercher les doublons", icon = PhotoDupIcon, modifier = Modifier.staggeredEntrance(1, baseDelay = 80), onClick = actions.onScan)
        if (state.access == GalleryAccess.PARTIAL) {
            SideButton("Autoriser toutes les photos", Icons.Rounded.Settings, Modifier.fillMaxWidth().staggeredEntrance(2, baseDelay = 80), onClick = actions.onAllow)
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(3, baseDelay = 160)
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        BlockLabel("Ce que Gulp repère")
        Spacer(Modifier.height(6.dp))
        InfoRow(Icons.Rounded.ContentCopy, "Les photos identiques", "La même photo enregistrée deux fois, octet pour octet", 0)
        InfoRow(Icons.Rounded.Compare, "Les photos ressemblantes", "Une copie réduite par WhatsApp, une capture refaite, une rafale", 1)
        InfoRow(Icons.Rounded.Star, "La meilleure à garder", "La plus nette (le plus de pixels), ou la première enregistrée", 2)
    }
    Hint(
        "Tout se passe sur le téléphone, hors ligne. Rien n'est supprimé sans que tu l'aies coché, et Android te demande de confirmer.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, detail: String, index: Int) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    val accent = LocalAccent.current
    Row(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(index, baseDelay = 260)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(if (terminal) 3.dp else 11.dp))
                .background(accent.tint(if (terminal) 0.12f else 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent.start, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(if (terminal) title.uppercase() else title, color = Palette.TextPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
            Text(if (terminal) detail.lowercase() else detail, color = Palette.TextSecondary, fontSize = 12.5.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun ScanningStep(phase: DupPhase.Scanning, actions: PhotoDupActions) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    val progress = if (phase.total > 0) phase.done.toFloat() / phase.total else null
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MatchScan(if (phase.step == 0) null else progress)
        Spacer(Modifier.height(18.dp))
        val step = when (phase.step) {
            0 -> "Lecture de la galerie…"
            1 -> "Empreinte de chaque photo…"
            else -> "Comparaison octet par octet…"
        }
        Text(if (terminal) "> $step" else step, color = Palette.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        val found = if (phase.step == 0) PhotoDupFacts.photos(phase.photos) else "${phase.done} / ${phase.total}"
        Text(if (terminal) found.lowercase() else found, color = Palette.TextSecondary, fontSize = 13.5.sp, textAlign = TextAlign.Center)
        if (terminal && progress != null) {
            Spacer(Modifier.height(14.dp))
            TerminalProgress(progress)
        }
        Spacer(Modifier.height(8.dp))
        val hint = "Étape ${phase.step + 1} sur 3 · la prochaine analyse ira plus vite"
        Text(if (terminal) "# ${hint.lowercase()}" else hint, color = Palette.TextTertiary, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
    SideButton("Annuler", null, Modifier.fillMaxWidth(), onClick = actions.onCancel)
}

@Composable
private fun ReadyStep(state: DupUiState, actions: PhotoDupActions) {
    val report = state.report
    val terminal = LocalSkin.current == Skin.TERMINAL
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(0, baseDelay = 40)
            .toolCard()
            .padding(16.dp),
    ) {
        BlockLabel("Récupérable")
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            CountingBytes(report.wastedBytes, if (report.wastedBytes > 0) Palette.Danger else Palette.TextPrimary, 40.sp)
            Spacer(Modifier.width(10.dp))
            val of = "sur ${PhotoDupFacts.bytes(report.totalBytes)}"
            Text(if (terminal) of.lowercase() else of, color = Palette.TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
        }
        Spacer(Modifier.height(4.dp))
        val line = "${PhotoDupFacts.photos(report.photos.size)} comparées · ${report.exact.size} groupes identiques · ${report.similar.size} ressemblants"
        Text(if (terminal) line.lowercase() else line, color = Palette.TextTertiary, fontSize = 12.5.sp)
    }

    OptionChips(
        options = DupKind.entries.toList(),
        selected = state.kind,
        label = { kind -> "${kind.label} (${if (kind == DupKind.EXACT) report.exact.size else report.similar.size})" },
        onSelect = actions.onKind,
        modifier = Modifier.staggeredEntrance(1, baseDelay = 80),
    )

    AnimatedContent(
        targetState = state.kind,
        transitionSpec = { fadeIn(tween(220, delayMillis = 40)) togetherWith fadeOut(tween(120)) },
        label = "photoDupKind",
        modifier = Modifier.fillMaxWidth(),
    ) { kind ->
        Column(
            Modifier
                .fillMaxWidth()
                .staggeredEntrance(2, baseDelay = 120)
                .toolCard()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            GroupList(state, kind, actions)
        }
    }

    if (PhotoDupFacts.wholeGroupChecked(state.report.groups, state.selection)) {
        Hint("Toutes les photos d'un groupe sont cochées : il n'en restera aucune.", warning = true, modifier = Modifier.padding(horizontal = 8.dp))
    }
    val count = state.selection.size
    AnimatedVisibility(state.confirming, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        ConfirmCard(state, actions)
    }
    AnimatedVisibility(!state.confirming, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        MainButton(
            PhotoDupFacts.deleteLabel(state.selectedBytes, count),
            icon = Icons.Rounded.DeleteForever,
            enabled = count > 0,
            modifier = Modifier.staggeredEntrance(3, baseDelay = 160),
            onClick = actions.onAskConfirm,
        )
    }
    SideButton("Chercher à nouveau", Icons.Rounded.Refresh, Modifier.fillMaxWidth().staggeredEntrance(4, baseDelay = 160), onClick = actions.onScan)
}

@Composable
private fun GroupList(state: DupUiState, kind: DupKind, actions: PhotoDupActions) {
    val groups = state.report.groups.filter { it.kind == kind }
    if (groups.isEmpty()) {
        EmptyLine(if (kind == DupKind.EXACT) "Aucune photo en double : chacune n'est là qu'une fois." else "Aucune photo qui se ressemble.")
        return
    }
    if (kind == DupKind.SIMILAR) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Hint("Vérifie avant de cocher : deux photos d'une rafale se ressemblent sans être pareilles.", modifier = Modifier.weight(1f))
        }
        val all = groups.all { g -> state.selection.containsAll(g.others.map { it.id }) && g.keep.id !in state.selection }
        Box(Modifier.padding(horizontal = 6.dp)) {
            KeepChip(if (all) "Partout, seule la meilleure reste" else "Garder la meilleure partout", all) { actions.onKeepBestEverywhere(kind) }
        }
    }
    var shown by rememberSaveable(kind) { mutableIntStateOf(GROUPS_STEP) }
    groups.take(shown).forEachIndexed { i, group ->
        if (i > 0) Divider()
        GroupCard(group, state.selection, i, actions.thumbnail, actions.onToggle, actions.onKeepBest)
    }
    if (groups.size > shown) {
        MoreLine("Voir ${minOf(GROUPS_STEP, groups.size - shown)} groupes de plus (${groups.size - shown} restants)") { shown += GROUPS_STEP }
    }
}

@Composable
private fun ConfirmCard(state: DupUiState, actions: PhotoDupActions) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    val kept = state.report.groups.count { g -> g.photos.any { it.id in state.selection } && g.photos.any { it.id !in state.selection } }
    val toTrash = state.toTrash && actions.canTrash
    Column(
        Modifier
            .fillMaxWidth()
            .shakeOnAppear(state.selection.size)
            .toolCard()
            .background(Palette.Danger.copy(alpha = 0.08f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Warning, contentDescription = null, tint = Palette.Danger, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            val title = if (toTrash) "Mettre à la corbeille ?" else "Supprimer pour de bon ?"
            Text(if (terminal) title.uppercase() else title, color = Palette.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        if (actions.canTrash) {
            OptionChips(
                options = listOf(false, true),
                selected = toTrash,
                label = { if (it) "Corbeille (30 jours)" else "Pour de bon" },
                onSelect = actions.onTrash,
            )
        }
        Text(
            PhotoDupFacts.confirmText(state.selection.size, state.selectedBytes, toTrash, kept),
            color = Palette.TextPrimary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        MainButton(
            "Oui, supprimer ${PhotoDupFacts.photos(state.selection.size)}",
            icon = if (toTrash) Icons.Rounded.AutoDelete else Icons.Rounded.DeleteForever,
            onClick = actions.onDelete,
        )
        SideButton("Non, revenir", null, Modifier.fillMaxWidth(), onClick = actions.onDismissConfirm)
    }
}

@Composable
private fun DeletingStep(phase: DupPhase.Deleting) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FlyingCopies()
        Spacer(Modifier.height(14.dp))
        val step = "Suppression de ${PhotoDupFacts.photos(phase.count)}…"
        Text(if (terminal) "> $step" else step, color = Palette.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        val sub = "Confirme dans la fenêtre d'Android."
        Text(if (terminal) "# ${sub.lowercase()}" else sub, color = Palette.TextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun DoneStep(phase: DupPhase.Done, actions: PhotoDupActions) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    val accent = LocalAccent.current
    val freed = phase.deleted.sumOf { it.size }
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
            SuccessBurst(play = true, color = accent.start, modifier = Modifier.size(104.dp))
            AnimatedCheck(Modifier.size(60.dp))
        }
        Spacer(Modifier.height(6.dp))
        CountingBytes(freed, Palette.TextPrimary, 40.sp, durationMillis = 1400)
        val count = phase.deleted.size
        val sub = when {
            phase.toTrash -> "à libérer · ${PhotoDupFacts.photos(count)} à la corbeille"
            count > 1 -> "libérés · $count photos supprimées"
            else -> "libérés · $count photo supprimée"
        }
        Text(
            if (terminal) "# $sub" else sub,
            color = Palette.TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            fontFamily = LocalTokens.current.body,
            modifier = Modifier.staggeredEntrance(1, baseDelay = 300),
        )
    }
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(1, baseDelay = 160)
            .toolCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PhotoDupFacts.doneLines(phase.deleted, phase.failed, phase.toTrash, phase.before).forEachIndexed { i, line ->
            Row(Modifier.staggeredEntrance(i, baseDelay = 360), verticalAlignment = Alignment.Top) {
                Text(if (terminal) "+" else "•", color = accent.start, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(if (terminal) line.lowercase() else line, color = Palette.TextPrimary, fontSize = 14.sp, lineHeight = 19.sp)
            }
        }
    }
    MainButton("Voir ce qui reste", icon = PhotoDupIcon, modifier = Modifier.staggeredEntrance(2, baseDelay = 200), onClick = actions.onBackToResults)
    SideButton("Chercher à nouveau", Icons.Rounded.Refresh, Modifier.fillMaxWidth().staggeredEntrance(3, baseDelay = 200), onClick = actions.onScan)
}

@Composable
private fun FailedStep(phase: DupPhase.Failed, actions: PhotoDupActions) {
    ToolErrorCard(phase.message, title = "La recherche n'a pas abouti")
    MainButton("Autoriser l'accès aux photos", icon = PhotoDupIcon, onClick = actions.onAllow)
    SideButton("Réessayer", Icons.Rounded.Refresh, Modifier.fillMaxWidth(), onClick = actions.onScan)
}

// MARK: - Small pieces

@Composable
private fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .height(1.dp)
            .background(Palette.TextPrimary.copy(alpha = if (LocalSkin.current == Skin.TERMINAL) 0.14f else 0.08f)),
    )
}

@Composable
private fun EmptyLine(text: String) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Text(
        if (terminal) "# $text" else text,
        color = Palette.TextSecondary,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp, horizontal = 8.dp),
    )
}

@Composable
private fun MoreLine(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = LocalAccent.current.start,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(onClick = onClick)
            .padding(vertical = 12.dp),
    )
}

private const val GROUPS_STEP = 15
private const val ALBUMS_SHOWN = 8
