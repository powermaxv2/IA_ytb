package com.powermaxv2.ytdownloader.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.powermaxv2.ytdownloader.App
import com.powermaxv2.ytdownloader.ui.hub.Favorites
import com.powermaxv2.ytdownloader.ui.hub.ToolRegistry
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Where the hub is: the home screen (list of tools), a tool, or the settings opened from the home screen. */
enum class Place { HOME, TOOL, SETTINGS }

data class Toast(val message: String, val success: Boolean = true, val id: Long = System.nanoTime())

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as App
    val prefs = app.prefs

    /** Every launch starts on the home screen. */
    var place by mutableStateOf(Place.HOME)
        private set

    /** Tool shown when [place] is [Place.TOOL]. */
    var toolId by mutableStateOf(ToolRegistry.CONVERTER)
        private set

    /** Last tool opened, remembered across launches: the home screen shows it big ([ToolRegistry.featured]). */
    var lastToolId by mutableStateOf(prefs.lastToolId)
        private set

    /** Tools starred on the home screen, in the order they were starred; they open the home screen. */
    var favorites by mutableStateOf(Favorites.decode(prefs.favoriteTools))
        private set

    private val _toasts = MutableSharedFlow<Toast>(extraBufferCapacity = 8)
    val toasts: SharedFlow<Toast> = _toasts.asSharedFlow()

    /** Opens a tool of the hub; ignored for an unknown tool or one that is not out yet. */
    fun openTool(id: String) {
        if (ToolRegistry.find(id) == null) return
        toolId = id
        place = Place.TOOL
        if (lastToolId != id) {
            lastToolId = id
            prefs.lastToolId = id
        }
    }

    /** Stars or unstars a tool of the home screen. */
    fun toggleFavorite(id: String) {
        val starring = id !in favorites
        favorites = Favorites.toggle(favorites, id)
        prefs.favoriteTools = Favorites.encode(favorites)
        // The tool leaves its category for the top of the screen: say where it went.
        if (starring) ToolRegistry.find(id)?.let { toast("${it.name} épinglé dans les favoris") }
    }

    fun goHome() {
        place = Place.HOME
    }

    /**
     * File handed to a tool from outside (Partager → "Convertir avec Gulp" / "Aperçu avec Gulp", Ouvrir avec),
     * waiting for that tool's screen to take it with [consumePendingFile]. Never persisted.
     */
    var pendingFile by mutableStateOf<Uri?>(null)
        private set

    /** Opens the tool [id] with [uri] (a content:// URI) waiting for it in [pendingFile]. */
    fun openToolWithFile(id: String, uri: Uri) {
        if (ToolRegistry.find(id) == null) return
        pendingFile = uri
        openTool(id)
    }

    /** The waiting file, once: the tool screen calls it when it appears (and on each new [pendingFile]). */
    fun consumePendingFile(): Uri? = pendingFile.also { pendingFile = null }

    /** Settings open over the home screen. */
    fun openSettings() {
        place = Place.SETTINGS
    }

    fun closeSettings() {
        place = Place.HOME
    }

    fun toast(message: String, success: Boolean = true) {
        _toasts.tryEmit(Toast(message, success))
    }
}
