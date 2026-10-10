package com.powermaxv2.ytdownloader.ui.tools.scan

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Rotate90DegreesCw
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.powermaxv2.ytdownloader.App
import com.powermaxv2.ytdownloader.core.SavedFile
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.scan.PageSize
import com.powermaxv2.ytdownloader.core.scan.Quad
import com.powermaxv2.ytdownloader.core.scan.ScanFilter
import com.powermaxv2.ytdownloader.core.scan.ScanPage
import com.powermaxv2.ytdownloader.core.scan.ScanPhase
import com.powermaxv2.ytdownloader.core.scan.ScanPlan
import com.powermaxv2.ytdownloader.core.scan.ScanQuality
import com.powermaxv2.ytdownloader.core.scan.ScanUiState
import com.powermaxv2.ytdownloader.ui.AnimatedCheck
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.SuccessBurst
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.pulsing
import com.powermaxv2.ytdownloader.ui.shakeOnAppear
import com.powermaxv2.ytdownloader.ui.shimmer
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold
import com.powermaxv2.ytdownloader.ui.tools.compress.Hint
import com.powermaxv2.ytdownloader.ui.tools.compress.bytes
import com.powermaxv2.ytdownloader.ui.tools.convert.BlockLabel
import com.powermaxv2.ytdownloader.ui.tools.convert.MainButton
import com.powermaxv2.ytdownloader.ui.tools.convert.OptionChips
import com.powermaxv2.ytdownloader.ui.tools.convert.ProgressRing
import com.powermaxv2.ytdownloader.ui.tools.convert.SideButton
import com.powermaxv2.ytdownloader.ui.tools.convert.TerminalProgress
import com.powermaxv2.ytdownloader.ui.tools.convert.toolCard
import java.io.File

const val SCANNER_TITLE = "Scanner"
const val SCANNER_COMMAND = "scan"
const val SCANNER_SUBTITLE = "Photographie des pages, redresse-les et range-les dans un seul PDF."

/** What the screen can ask for; every callback defaults to nothing (screenshot tests). */
data class ScannerActions(
    val onShoot: () -> Unit = {},
    val onPick: () -> Unit = {},
    val onEdit: (Long) -> Unit = {},
    val onQuad: (Long, Quad) -> Unit = { _, _ -> },
    val onAuto: (Long) -> Unit = {},
    val onFull: (Long) -> Unit = {},
    val onRotate: (Long) -> Unit = {},
    val onFilter: (Long, ScanFilter) -> Unit = { _, _ -> },
    val onRemove: (Long) -> Unit = {},
    val onMove: (Long, Int) -> Unit = { _, _ -> },
    val onCloseEditor: () -> Unit = {},
    val onPageSize: (PageSize) -> Unit = {},
    val onQuality: (ScanQuality) -> Unit = {},
    val onName: (String) -> Unit = {},
    val onExport: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onOpen: (SavedFile) -> Unit = {},
    val onShare: (SavedFile) -> Unit = {},
    val onBackToPages: () -> Unit = {},
    val onReset: () -> Unit = {},
)

/**
 * The Scanner (ToolRegistry.SCANNER). Photos come from the phone's camera app (written straight into Gulp's
 * private work folder through its FileProvider, never into the Gallery) or from the file picker. Its state lives
 * in [App.scanner], so the pages stay when the user leaves the screen.
 */
@Composable
fun ScannerTool(vm: MainViewModel) {
    val controller = remember { App.instance.scanner }
    val state by controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var shot by remember { mutableStateOf<File?>(null) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        shot?.let { controller.onShot(it, ok) }
        shot = null
    }

    fun shoot() {
        if (state.pages.size >= ScanPlan.MAX_PAGES) {
            vm.toast("${ScanPlan.MAX_PAGES} pages au plus dans un PDF.", success = false)
            return
        }
        val file = try {
            controller.newShotFile()
        } catch (e: Exception) {
            vm.toast("Impossible de préparer la photo.", success = false)
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        shot = file
        try {
            camera.launch(uri)
        } catch (e: ActivityNotFoundException) {
            shot = null
            vm.toast("Aucune app photo sur ce téléphone : choisissez des images.", success = false)
        } catch (e: SecurityException) {
            shot = null
            vm.toast("Android a refusé l'appareil photo.", success = false)
        }
    }

    // When Gulp declares the camera permission (live QR scanner), Android only lets the camera app be opened
    // once it is granted.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) shoot() else vm.toast("Gulp a besoin de l'appareil photo pour photographier les pages.", success = false)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        controller.pick(uris)
    }
    // Android 9 and older: saving to Download needs the storage permission.
    val storage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) controller.export() else vm.toast("Gulp a besoin de l'accès au stockage pour enregistrer le PDF.", success = false)
    }

    val actions = remember(controller) {
        ScannerActions(
            onShoot = {
                if (needsCameraPermission(context)) permission.launch(Manifest.permission.CAMERA) else shoot()
            },
            onPick = {
                try {
                    picker.launch(arrayOf("image/*"))
                } catch (e: ActivityNotFoundException) {
                    vm.toast("Aucun sélecteur de fichiers sur ce téléphone.", success = false)
                }
            },
            onEdit = controller::edit,
            onQuad = controller::setQuad,
            onAuto = controller::autoQuad,
            onFull = controller::fullQuad,
            onRotate = controller::rotate,
            onFilter = controller::setFilter,
            onRemove = controller::remove,
            onMove = controller::move,
            onCloseEditor = controller::closeEditor,
            onPageSize = controller::setPageSize,
            onQuality = controller::setQuality,
            onName = controller::setName,
            onExport = {
                val needsStorage = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                if (needsStorage) storage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else controller.export()
            },
            onCancel = controller::cancel,
            onOpen = { openFile(context, it, vm) },
            onShare = { shareFile(context, it, vm) },
            onBackToPages = controller::backToPages,
            onReset = controller::reset,
        )
    }

    val scroll = rememberScrollState()
    LaunchedEffect(state.phase::class) { scroll.animateScrollTo(0) }

    ToolScaffold(vm, title = SCANNER_TITLE, command = SCANNER_COMMAND, subtitle = SCANNER_SUBTITLE, scrollState = scroll) {
        ScannerContent(state, actions)
    }
}

/** Whether the manifest asks for the camera permission and it is not granted yet. */
private fun needsCameraPermission(context: Context): Boolean {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) return false
    val requested = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions
    }.getOrNull()
    return requested?.contains(Manifest.permission.CAMERA) == true
}

/** Body of the tool for a [state] (stateless: also drawn by the screenshot tests). */
@Composable
fun ColumnScope.ScannerContent(state: ScanUiState, actions: ScannerActions) {
    AnimatedContent(
        targetState = state,
        contentKey = { s -> s.phase::class to (s.phase as? ScanPhase.Editing)?.pageId },
        transitionSpec = {
            val toEditor = targetState.phase is ScanPhase.Editing
            val enter = if (toEditor) {
                fadeIn(tween(240, delayMillis = 60)) + slideInHorizontally(spring(dampingRatio = 0.82f, stiffness = 300f)) { it / 5 }
            } else {
                fadeIn(tween(260, delayMillis = 60)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 300f)) { it / 10 }
            }
            enter togetherWith fadeOut(tween(160))
        },
        label = "scannerPhase",
        modifier = Modifier.fillMaxWidth(),
    ) { current ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (val phase = current.phase) {
                ScanPhase.Idle -> IdleStep(current, actions)
                ScanPhase.Pages -> PagesStep(current, actions)
                is ScanPhase.Editing -> current.page(phase.pageId)?.let { EditStep(current, it, actions) } ?: PagesStep(current, actions)
                is ScanPhase.Working -> WorkingStep(current, phase, actions)
                is ScanPhase.Done -> DoneStep(current, phase, actions)
                is ScanPhase.Failed -> FailedStep(current, phase, actions)
            }
        }
    }
}

// MARK: - Idle

@Composable
private fun IdleStep(state: ScanUiState, actions: ScannerActions) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    val accent = LocalAccent.current
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(0)
            .toolCard()
            .background(accent.start.copy(alpha = if (LocalTokens.current.isLight) 0.05f else 0.10f))
            .bounceClick(pressedScale = 0.97f, onClick = actions.onShoot)
            .padding(horizontal = 18.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CaptureLens()
        Spacer(Modifier.height(4.dp))
        Text(
            if (terminal) "PHOTOGRAPHIER UNE PAGE" else "Photographier une page",
            color = Palette.TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = LocalTokens.current.display,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (terminal) "# une photo par page, autant que vous voulez" else "Une photo par page, autant que vous voulez",
            color = Palette.TextSecondary,
            fontSize = 13.5.sp,
            textAlign = TextAlign.Center,
        )
    }
    SideButton("Choisir des images", Icons.Rounded.PhotoLibrary, Modifier.fillMaxWidth().staggeredEntrance(1, baseDelay = 60), onClick = actions.onPick)
    if (state.importing > 0) Importing()
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(2, baseDelay = 120)
            .toolCard()
            .padding(16.dp),
    ) {
        BlockLabel("Comment ça marche")
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            HowStep(0, Icons.Rounded.AddAPhoto, "Photo")
            HowStep(1, Icons.Rounded.CropFree, "Recadrer")
            HowStep(2, Icons.Rounded.AutoFixHigh, "Nettoyer")
            HowStep(3, Icons.Rounded.SwapHoriz, "Ordre")
            HowStep(4, Icons.Rounded.PictureAsPdf, "PDF")
        }
    }
    Hint(
        "Tout se passe sur le téléphone, hors ligne. Les photos ne vont pas dans la Galerie : seul le PDF est enregistré, dans Download/Gulp.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
}

@Composable
private fun HowStep(index: Int, icon: ImageVector, label: String) {
    val accent = LocalAccent.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.staggeredEntrance(index, baseDelay = 260)) {
        Box(
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(LocalTokens.current.fieldRadius))
                .background(Palette.Surface2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent.start, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (LocalSkin.current == Skin.TERMINAL) "${index + 1}.${label.uppercase()}" else "${index + 1}. $label",
            color = Palette.TextSecondary,
            fontSize = 11.5.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun Importing() {
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
                .size(40.dp, 52.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Palette.TextPrimary.copy(alpha = 0.08f)),
        )
        Spacer(Modifier.width(14.dp))
        Text("Lecture de la photo, recherche de la page…", color = Palette.TextSecondary, fontSize = 13.sp)
    }
}

// MARK: - Editor

@Composable
private fun EditStep(state: ScanUiState, page: ScanPage, actions: ScannerActions) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    val accent = LocalAccent.current
    val number = state.pages.indexOfFirst { it.id == page.id } + 1
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BlockLabel(if (terminal) "page $number / ${state.pages.size}" else "Page $number sur ${state.pages.size}", Modifier.weight(1f))
        if (page.detected != null) {
            Row(
                Modifier
                    .bounceClick(pressedScale = 0.9f) { actions.onAuto(page.id) }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, tint = accent.start, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (terminal) "[AUTO]" else "Cadre auto", color = accent.start, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    CornerEditor(
        photo = state.editorPhoto,
        aspect = page.aspect,
        quad = page.quad,
        rotation = page.rotation,
        onQuad = { actions.onQuad(page.id, it) },
        modifier = Modifier.staggeredEntrance(0, baseDelay = 40),
    )
    Hint(
        if (page.detected != null) "Gulp a trouvé la page. Déplacez un coin si le cadre n'est pas juste." else "Placez les quatre coins sur ceux de la feuille.",
        modifier = Modifier.padding(horizontal = 6.dp),
    )
    Row(Modifier.fillMaxWidth().staggeredEntrance(1, baseDelay = 80), horizontalArrangement = Arrangement.SpaceEvenly) {
        RoundTool(Icons.Rounded.Rotate90DegreesCw, "Tourner") { actions.onRotate(page.id) }
        RoundTool(Icons.Rounded.CropFree, "Toute la photo") { actions.onFull(page.id) }
        RoundTool(Icons.Rounded.DeleteOutline, "Retirer", danger = true) { actions.onRemove(page.id) }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(2, baseDelay = 100)
            .toolCard()
            .padding(16.dp),
    ) {
        BlockLabel("Nettoyage")
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScanFilter.entries.forEachIndexed { index, filter ->
                FilterTile(
                    filter,
                    state.filterPreviews[filter],
                    selected = page.filter == filter,
                    index = index,
                    modifier = Modifier.weight(1f),
                ) { actions.onFilter(page.id, filter) }
            }
        }
    }
    MainButton(
        "Valider la page",
        subtitle = "${if (page.quad == Quad.FULL) "Photo entière" else "Recadrée"} · ${page.filter.label.lowercase()}",
        icon = Icons.Rounded.Check,
        modifier = Modifier.staggeredEntrance(3, baseDelay = 120),
        onClick = actions.onCloseEditor,
    )
    SideButton(
        "Valider et photographier la suivante",
        Icons.Rounded.AddAPhoto,
        Modifier.fillMaxWidth().staggeredEntrance(4, baseDelay = 120),
    ) {
        actions.onCloseEditor()
        actions.onShoot()
    }
}

@Composable
private fun RoundTool(icon: ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    val shape = if (terminal) RoundedCornerShape(3.dp) else CircleShape
    Column(
        Modifier.bounceClick(pressedScale = 0.88f, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(50.dp)
                .clip(shape)
                .background(Palette.Surface2)
                .border(1.dp, Palette.Stroke, shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (danger) Palette.Danger else Palette.TextPrimary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(if (terminal) label.uppercase() else label, color = Palette.TextSecondary, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun FilterTile(
    filter: ScanFilter,
    preview: android.graphics.Bitmap?,
    selected: Boolean,
    index: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val accent = LocalAccent.current
    val terminal = LocalSkin.current == Skin.TERMINAL
    val shape = RoundedCornerShape(LocalTokens.current.fieldRadius)
    val image = rememberImage(preview)
    Column(
        modifier
            .staggeredEntrance(index, baseDelay = 160)
            .bounceClick(pressedScale = 0.92f, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.8f)
                .then(if (selected) Modifier.pulsing(0.98f, 1.03f, 1600) else Modifier)
                .clip(shape)
                .background(androidx.compose.ui.graphics.Color.White)
                .border(if (selected) 2.5.dp else 1.dp, if (selected) accent.start else Palette.Stroke, shape),
        ) {
            if (image != null) {
                androidx.compose.foundation.Image(
                    image,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize().shimmer())
            }
            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(18.dp)
                        .clip(if (terminal) RoundedCornerShape(2.dp) else CircleShape)
                        .background(accent.start),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = Palette.OnAccent, modifier = Modifier.size(13.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (terminal) filter.label.uppercase() else filter.label,
            color = if (selected) Palette.TextPrimary else Palette.TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

// MARK: - Pages

/** One cell of the pages grid: a page, or one of the two "add" tiles. */
private sealed interface Cell {
    val key: Any

    data class Page(val page: ScanPage, val number: Int) : Cell {
        override val key: Any get() = page.id
    }

    data object Shoot : Cell {
        override val key: Any get() = "shoot"
    }

    data object Pick : Cell {
        override val key: Any get() = "pick"
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PagesStep(state: ScanUiState, actions: ScannerActions) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    val count = state.pages.size
    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(0)
            .toolCard()
            .padding(16.dp),
    ) {
        BlockLabel(if (count > 1) "$count pages" else "1 page")
        Spacer(Modifier.height(4.dp))
        Hint(if (count > 1) "Touchez une page pour la retoucher, ‹ › pour changer l'ordre." else "Touchez la page pour la retoucher.")
        Spacer(Modifier.height(14.dp))
        val cells = state.pages.mapIndexed { i, p -> Cell.Page(p, i + 1) } +
            (if (count < ScanPlan.MAX_PAGES) listOf(Cell.Shoot, Cell.Pick) else emptyList())
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = 3
            val gap = 12.dp
            val cellW = (maxWidth - gap * (columns - 1)) / columns
            val cellH = cellW * 1.414f + 34.dp
            val rows = (cells.size + columns - 1) / columns
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalArrangement = Arrangement.spacedBy(gap),
                userScrollEnabled = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cellH * rows + gap * (rows - 1).coerceAtLeast(0)),
            ) {
                items(cells, key = { it.key }) { cell ->
                    val itemModifier = Modifier.animateItem(
                        fadeInSpec = tween(220),
                        placementSpec = spring(dampingRatio = 0.7f, stiffness = 380f),
                        fadeOutSpec = tween(160),
                    )
                    when (cell) {
                        is Cell.Page -> PageCell(cell, state.thumbs[cell.page.id], count, cellW, actions, itemModifier)
                        Cell.Shoot -> AddCell(Icons.Rounded.AddAPhoto, if (terminal) "PHOTO" else "Photo", cellW, itemModifier, actions.onShoot)
                        Cell.Pick -> AddCell(Icons.Rounded.PhotoLibrary, if (terminal) "IMAGE" else "Image", cellW, itemModifier, actions.onPick)
                    }
                }
            }
        }
        if (state.importing > 0) {
            Spacer(Modifier.height(12.dp))
            Importing()
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(1, baseDelay = 60)
            .toolCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BlockLabel("Format des pages")
        OptionChips(PageSize.entries, state.pageSize, { it.label }, actions.onPageSize)
        BlockLabel("Qualité")
        OptionChips(ScanQuality.entries, state.quality, { it.label }, actions.onQuality)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(2, baseDelay = 80)
            .toolCard()
            .padding(16.dp),
    ) {
        BlockLabel("Nom du fichier")
        Spacer(Modifier.height(10.dp))
        NameField(state.name, state.defaultName, actions.onName)
    }

    MainButton(
        "Créer le PDF",
        subtitle = "${if (count > 1) "$count pages" else "1 page"} · ${state.pageSize.label} · ≈ ${bytes(state.estimate)}",
        icon = Icons.Rounded.PictureAsPdf,
        enabled = count > 0 && state.importing == 0,
        modifier = Modifier.staggeredEntrance(3, baseDelay = 100),
        onClick = actions.onExport,
    )
    SideButton("Tout recommencer", Icons.Rounded.Close, Modifier.fillMaxWidth().staggeredEntrance(4, baseDelay = 100), onClick = actions.onReset)
}

@Composable
private fun PageCell(cell: Cell.Page, thumb: android.graphics.Bitmap?, count: Int, width: androidx.compose.ui.unit.Dp, actions: ScannerActions, modifier: Modifier) {
    val id = cell.page.id
    Column(modifier.width(width), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f / 1.414f)
                .bounceClick(pressedScale = 0.94f) { actions.onEdit(id) },
            contentAlignment = Alignment.Center,
        ) {
            PageSheet(thumb, Modifier.fillMaxSize().padding(2.dp))
            PageNumber(cell.number, Modifier.align(Alignment.TopStart))
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            MiniIcon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, enabled = cell.number > 1) { actions.onMove(id, -1) }
            MiniIcon(Icons.Rounded.Edit, enabled = true) { actions.onEdit(id) }
            MiniIcon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, enabled = cell.number < count) { actions.onMove(id, 1) }
        }
    }
}

@Composable
private fun MiniIcon(icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(28.dp)
            .bounceClick(enabled = enabled, pressedScale = 0.8f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) Palette.TextSecondary else Palette.TextTertiary.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun AddCell(icon: ImageVector, label: String, width: androidx.compose.ui.unit.Dp, modifier: Modifier, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Column(modifier.width(width)) {
        Dashed(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f / 1.414f)
                .bounceClick(pressedScale = 0.92f, onClick = onClick),
        ) {
            Icon(icon, contentDescription = null, tint = accent.start, modifier = Modifier.size(26.dp))
            Spacer(Modifier.height(6.dp))
            Text(label, color = accent.start, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun NameField(value: String, placeholder: String, onChange: (String) -> Unit) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(LocalTokens.current.fieldRadius)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = Palette.TextPrimary, fontSize = 16.sp, fontFamily = LocalTokens.current.body),
        cursorBrush = SolidColor(accent.start),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(Palette.Surface2)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f, fill = false)) {
                    if (value.isEmpty()) Text(placeholder, color = Palette.TextPrimary, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    inner()
                }
                Text(".pdf", color = Palette.TextTertiary, fontSize = 16.sp)
            }
        },
    )
}

// MARK: - Working, done, failed

@Composable
private fun WorkingStep(state: ScanUiState, phase: ScanPhase.Working, actions: ScannerActions) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (terminal) TerminalProgress(phase.progress) else ProgressRing(phase.progress, size = 176.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            if (terminal) "> ${phase.step}" else phase.step,
            color = Palette.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(state.fileName, color = Palette.TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    SideButton("Annuler", null, Modifier.fillMaxWidth(), onClick = actions.onCancel)
}

@Composable
private fun DoneStep(state: ScanUiState, phase: ScanPhase.Done, actions: ScannerActions) {
    val accent = LocalAccent.current
    val terminal = LocalSkin.current == Skin.TERMINAL
    val saved = phase.saved
    Column(
        Modifier
            .fillMaxWidth()
            .toolCard()
            .padding(horizontal = 16.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
            SuccessBurst(play = true, color = accent.start, modifier = Modifier.size(96.dp))
            AnimatedCheck(Modifier.size(56.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (terminal) "PDF CRÉÉ" else "PDF créé",
            color = Palette.TextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = LocalTokens.current.display,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "${if (phase.pages > 1) "${phase.pages} pages" else "1 page"} · ${bytes(saved.size)} · enregistré dans ${saved.location}",
            color = Palette.TextSecondary,
            fontSize = 13.5.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        SheetFan(state.pages.take(3).map { state.thumbs[it.id] })
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .staggeredEntrance(0, baseDelay = 400)
                .clip(RoundedCornerShape(LocalTokens.current.fieldRadius))
                .background(Palette.Surface2)
                .bounceClick(pressedScale = 0.97f) { actions.onOpen(saved) }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(34.dp, 40.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Palette.Danger),
                contentAlignment = Alignment.Center,
            ) {
                Text("PDF", color = androidx.compose.ui.graphics.Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(saved.name, color = Palette.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${state.pageSize.label} · ${state.quality.label.lowercase()}", color = Palette.TextSecondary, fontSize = 12.sp)
            }
            Text(if (terminal) "[OUVRIR]" else "Ouvrir", color = accent.start, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
        }
    }
    MainButton("Ouvrir le PDF", icon = Icons.Rounded.OpenInNew, modifier = Modifier.staggeredEntrance(1, baseDelay = 200)) { actions.onOpen(saved) }
    Row(Modifier.fillMaxWidth().staggeredEntrance(2, baseDelay = 200), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SideButton("Partager", Icons.Rounded.Share, Modifier.weight(1f)) { actions.onShare(saved) }
        SideButton("Modifier", Icons.Rounded.Edit, Modifier.weight(1f), onClick = actions.onBackToPages)
    }
    SideButton("Nouveau scan", Icons.Rounded.DocumentScanner, Modifier.fillMaxWidth().staggeredEntrance(3, baseDelay = 200), onClick = actions.onReset)
}

@Composable
private fun FailedStep(state: ScanUiState, phase: ScanPhase.Failed, actions: ScannerActions) {
    var details by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .shakeOnAppear(phase.message)
            .toolCard()
            .background(Palette.Danger.copy(alpha = 0.08f))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Palette.Danger, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    if (LocalSkin.current == Skin.TERMINAL) "ERREUR" else "Le scan n'a pas abouti",
                    color = Palette.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(phase.message, color = Palette.TextPrimary, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
        phase.debug?.takeIf { it.isNotBlank() }?.let { log ->
            Spacer(Modifier.height(10.dp))
            Text(
                if (details) "Masquer les détails" else "Détails",
                color = LocalAccent.current.start,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.bounceClick { details = !details },
            )
            AnimatedVisibility(details, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Text(log.take(600), color = Palette.TextSecondary, fontSize = 10.5.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
    if (state.pages.isNotEmpty()) {
        MainButton("Réessayer", icon = Icons.Rounded.PictureAsPdf, onClick = actions.onExport)
        SideButton("Revenir aux pages", Icons.Rounded.Edit, Modifier.fillMaxWidth(), onClick = actions.onBackToPages)
    } else {
        SideButton("Recommencer", Icons.Rounded.DocumentScanner, Modifier.fillMaxWidth(), onClick = actions.onReset)
    }
}

// MARK: - Intents

private fun openFile(context: Context, file: SavedFile, vm: MainViewModel) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(file.uri, file.mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(Intent.createChooser(intent, "Ouvrir avec")) }
        .onFailure { vm.toast("Aucune app ne peut ouvrir ce PDF", success = false) }
}

private fun shareFile(context: Context, file: SavedFile, vm: MainViewModel) {
    val intent = Intent(Intent.ACTION_SEND)
        .setType(file.mime)
        .putExtra(Intent.EXTRA_STREAM, file.uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(Intent.createChooser(intent, "Partager")) }
        .onFailure { vm.toast("Partage impossible", success = false) }
}
