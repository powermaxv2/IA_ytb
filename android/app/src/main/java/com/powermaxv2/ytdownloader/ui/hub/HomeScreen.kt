package com.powermaxv2.ytdownloader.ui.hub

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.UpdateBanner
import com.powermaxv2.ytdownloader.ui.skin.SkinComingSoon
import com.powermaxv2.ytdownloader.ui.skin.SkinHomeHeader
import com.powermaxv2.ytdownloader.ui.skin.SkinToolCard
import kotlin.math.PI
import kotlin.math.sin

/** Home screen of the hub: every tool of [ToolRegistry], the ones that are out first, then the "À venir" ones. */
@Composable
fun HomeScreen(vm: MainViewModel, insets: PaddingValues) {
    HomeContent(
        tools = ToolRegistry.tools,
        onOpen = { vm.openTool(it.id) },
        onSettings = vm::openSettings,
        contentPadding = PaddingValues(
            top = insets.calculateTopPadding() + 16.dp,
            bottom = insets.calculateBottomPadding() + 32.dp,
        ),
        // The update card lives here too: the hub is where the app opens.
        banner = { UpdateBanner(vm) },
    )
}

/** Stateless form of [HomeScreen] (also used by the screenshot tests). */
@Composable
fun HomeContent(
    tools: List<GulpTool>,
    onOpen: (GulpTool) -> Unit,
    onSettings: () -> Unit,
    contentPadding: PaddingValues,
    banner: @Composable () -> Unit = {},
) {
    val ready = tools.filter { it.available }
    val upcoming = tools.filterNot { it.available }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SkinHomeHeader(ready.size, upcoming.size, onSettings)
        banner()
        ready.forEachIndexed { index, tool -> SkinToolCard(tool, index) { onOpen(tool) } }
        if (upcoming.isNotEmpty()) SkinComingSoon(upcoming, firstNumber = ready.size + 1)
    }
}

/** "1 outil prêt", "3 à venir". */
internal fun readyText(count: Int): String = if (count > 1) "$count outils prêts" else "$count outil prêt"

internal fun upcomingText(count: Int): String = "$count à venir"

/** "01", "02"… */
internal fun toolNumber(n: Int): String = n.toString().padStart(2, '0')

/**
 * Slow floating motion (platform logos on the cards): each [index] is out of phase with the previous one,
 * so a row of them ripples like a wave.
 */
@Composable
fun Modifier.bob(index: Int, amplitude: Dp = 3.dp, periodMs: Int = 2400): Modifier {
    val transition = rememberInfiniteTransition(label = "bob")
    val t by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(periodMs, easing = LinearEasing)), label = "bobPhase")
    return graphicsLayer {
        val angle = (t * 2f * PI.toFloat()) - index * 0.9f
        translationY = sin(angle) * amplitude.toPx()
        rotationZ = sin(angle + 0.6f) * 3f
    }
}

/** Periodic nudge to the right (arrows of the "Ouvrir" buttons): rests, then a quick double push. */
@Composable
fun Modifier.nudgeRight(distance: Dp = 4.dp, periodMs: Int = 2200): Modifier {
    val transition = rememberInfiniteTransition(label = "nudge")
    val t by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(periodMs, easing = LinearEasing)), label = "nudgeX")
    return graphicsLayer {
        val pull = if (t < 0.6f) 0f else sin((t - 0.6f) / 0.4f * 2f * PI.toFloat()).coerceAtLeast(0f)
        translationX = pull * distance.toPx()
    }
}
