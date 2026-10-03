package com.powermaxv2.ytdownloader.ui.tools.convert

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.tools.FilePickButton
import com.powermaxv2.ytdownloader.ui.tools.ToolScaffold

/**
 * The Convertisseur tool (ToolRegistry.CONVERTER). Placeholder screen of the shared foundation: the frame and the
 * file picker only; the conversion itself replaces this body.
 */
@Composable
fun ConverterTool(vm: MainViewModel) {
    // A file shared to "Convertir avec Gulp" waits in vm.pendingFile.
    LaunchedEffect(vm.pendingFile) {
        if (vm.consumePendingFile() != null) vm.toast("Fichier reçu : la conversion arrive bientôt.")
    }
    ToolScaffold(
        vm,
        title = "Convertisseur",
        command = "convert",
        subtitle = "Change le format d'une photo, d'un son, d'une vidéo ou d'un document.",
    ) {
        FilePickButton(
            onPicked = { vm.toast("Fichier reçu : la conversion arrive bientôt.") },
            hint = "Photo, son, vidéo ou document",
        )
    }
}
