package com.powermaxv2.ytdownloader.core

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface EngineState {
    data object Starting : EngineState
    data object Ready : EngineState
    data class Failed(val message: String) : EngineState
}

/** Version Play Store: only the FFmpeg bundled in the APK (conversion, compression, cutting…). */
class Engine(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<EngineState>(EngineState.Starting)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    fun start() {
        scope.launch(Dispatchers.IO) {
            try {
                FFmpeg.getInstance().init(context)
                _state.value = EngineState.Ready
            } catch (t: Throwable) {
                _state.value = EngineState.Failed(t.message ?: t.javaClass.simpleName)
            }
        }
    }

    suspend fun awaitReady() {
        val state = _state.first { it !is EngineState.Starting }
        if (state is EngineState.Failed) {
            throw IllegalStateException("Le moteur n'a pas pu démarrer : ${state.message}")
        }
    }
}
