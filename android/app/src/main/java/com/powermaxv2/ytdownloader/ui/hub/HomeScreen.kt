package com.powermaxv2.ytdownloader.ui.hub

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.UpdateBanner
import com.powermaxv2.ytdownloader.ui.skin.SkinComingSoon
import com.powermaxv2.ytdownloader.ui.skin.SkinHomeHeader
import com.powermaxv2.ytdownloader.ui.skin.SkinHomeSection
import com.powermaxv2.ytdownloader.ui.skin.SkinToolBubble
import com.powermaxv2.ytdownloader.ui.skin.SkinToolCard
import kotlin.math.PI
import kotlin.math.sin

/**
 * Home screen of the hub: the last tool used in a big card, the other tools that are out as small bubbles
 * two per row, then the "À venir" ones.
 */
@Composable
fun HomeScreen(vm: MainViewModel, insets: PaddingValues) {
    HomeContent(
        tools = ToolRegistry.tools,
        lastUsedId = vm.lastToolId,
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
    lastUsedId: String?,
    onOpen: (GulpTool) -> Unit,
    onSettings: () -> Unit,
    contentPadding: PaddingValues,
    banner: @Composable () -> Unit = {},
) {
    val ready = tools.filter { it.available }
    val upcoming = tools.filterNot { it.available }
    val featured = featuredTool(ready, lastUsedId)
    val others = ready - featured
    // Numbers follow the registry, so a tool keeps its number wherever it sits on the screen.
    fun number(tool: GulpTool) = ready.indexOf(tool)
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
        SkinHomeSection(if (featured.id == lastUsedId) "Dernier utilisé" else "Pour commencer", order = 0)
        // Keyed by the tool, so a new featured tool plays its entrance again instead of morphing the old card.
        key(featured.id) { SkinToolCard(featured, number(featured)) { onOpen(featured) } }
        if (others.isNotEmpty()) {
            SkinHomeSection("Tes autres outils", order = 1)
            others.chunked(2).forEachIndexed { row, pair ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEachIndexed { col, tool ->
                        SkinToolBubble(tool, number(tool), order = 2 + row * 2 + col, Modifier.weight(1f).fillMaxHeight()) { onOpen(tool) }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (upcoming.isNotEmpty()) SkinComingSoon(upcoming, firstNumber = ready.size + 1)
    }
}

/**
 * The tool shown big at the top of the home screen: the last one used, or the video downloader as long as
 * nothing has been used (or when the last one is gone from this version).
 */
internal fun featuredTool(ready: List<GulpTool>, lastUsedId: String?): GulpTool =
    ready.firstOrNull { it.id == lastUsedId } ?: ready.firstOrNull { it.id == ToolRegistry.DOWNLOADER } ?: ready.first()

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
