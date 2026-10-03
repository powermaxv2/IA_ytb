package com.powermaxv2.ytdownloader.ui.tools.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.tools.FilePickButton
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold

/**
 * The Aperçu tool (ToolRegistry.PREVIEW). Placeholder screen of the shared foundation: the frame and the file
 * picker only; the viewers replace this body.
 */
@Composable
fun PreviewTool(vm: MainViewModel) {
    // A file shared to "Aperçu avec Gulp" or opened with Gulp waits in vm.pendingFile.
    LaunchedEffect(vm.pendingFile) {
        if (vm.consumePendingFile() != null) vm.toast("Fichier reçu : l'aperçu arrive bientôt.")
    }
    ToolScaffold(
        vm,
        title = "Aperçu",
        command = "preview",
        subtitle = "Regarde ce qu'il y a dans une image, un PDF, un texte ou une archive ZIP.",
    ) {
        FilePickButton(
            onPicked = { vm.toast("Fichier reçu : l'aperçu arrive bientôt.") },
            hint = "Image, PDF, texte, ZIP…",
        )
    }
}
