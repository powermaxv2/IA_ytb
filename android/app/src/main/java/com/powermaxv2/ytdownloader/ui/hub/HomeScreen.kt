package com.powermaxv2.ytdownloader.ui.hub

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.UpdateBanner
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import com.powermaxv2.ytdownloader.ui.skin.SkinCategoryChips
import com.powermaxv2.ytdownloader.ui.skin.SkinComingSoon
import com.powermaxv2.ytdownloader.ui.skin.SkinHomeHeader
import com.powermaxv2.ytdownloader.ui.skin.SkinHomeSection
import com.powermaxv2.ytdownloader.ui.skin.SkinSearchBar
import com.powermaxv2.ytdownloader.ui.skin.SkinToolBubble
import com.powermaxv2.ytdownloader.ui.skin.SkinToolCard
import com.powermaxv2.ytdownloader.ui.staggeredEntrance
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Home screen of the hub: search bar and category chips, the last tool used in a big card, the other tools that
 * are out as small bubbles grouped by category, then the "À venir" ones.
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

/**
 * Stateless form of [HomeScreen] (also used by the screenshot tests): the search bar and the category chips
 * filter the tools; with neither, the last tool used is big and the others are grouped by category.
 */
@Composable
fun HomeContent(
    tools: List<GulpTool>,
    lastUsedId: String?,
    onOpen: (GulpTool) -> Unit,
    onSettings: () -> Unit,
    contentPadding: PaddingValues,
    banner: @Composable () -> Unit = {},
    initialQuery: String = "",
    initialCategory: ToolCategory? = null,
) {
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var categoryName by rememberSaveable { mutableStateOf(initialCategory?.name) }
    val category = categoryName?.let { name -> ToolCategory.entries.firstOrNull { it.name == name } }
    val ready = tools.filter { it.available }
    val upcoming = tools.filterNot { it.available }
    val categories = ToolCategories.present(ready)
    val filtering = query.isNotBlank() || category != null
    // Back first clears the search, then leaves the app as before.
    BackHandler(enabled = filtering) {
        query = ""
        categoryName = null
    }
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
        SkinSearchBar(query, onQueryChange = { query = it }, suggestions = ready.map { it.name })
        SkinCategoryChips(
            categories = categories,
            counts = categories.associateWith { c -> ready.count { ToolCategories.of(it) == c } },
            total = ready.size,
            selected = category,
            onSelect = { categoryName = it?.name },
        )
        if (filtering) {
            val found = ready.filter { (category == null || ToolCategories.of(it) == category) && ToolCategories.matches(it, query) }
            val title = when {
                query.isNotBlank() -> resultsText(found.size)
                else -> category!!.label
            }
            // Keyed by the filter: every new search deals its bubbles again, in cascade.
            key(category, ToolCategories.fold(listOf(query))) {
                SkinHomeSection(title, order = 0)
                if (found.isEmpty()) HomeEmpty(query, category) else BubbleGrid(found, ::number, firstOrder = 1, onOpen = onOpen)
            }
        } else {
            val featured = featuredTool(ready, lastUsedId)
            SkinHomeSection(if (featured.id == lastUsedId) "Dernier utilisé" else "Pour commencer", order = 0)
            // Keyed by the tool, so a new featured tool plays its entrance again instead of morphing the old card.
            key(featured.id) { SkinToolCard(featured, number(featured)) { onOpen(featured) } }
            var order = 1
            categories.forEach { c ->
                val inCategory = ready.filter { it != featured && ToolCategories.of(it) == c }
                if (inCategory.isNotEmpty()) {
                    SkinHomeSection(c.label, order = order++)
                    BubbleGrid(inCategory, ::number, firstOrder = order, onOpen = onOpen)
                    order += inCategory.size
                }
            }
            if (upcoming.isNotEmpty()) SkinComingSoon(upcoming, firstNumber = ready.size + 1)
        }
    }
}

/** Tools as small bubbles, two per row of equal height. */
@Composable
private fun BubbleGrid(tools: List<GulpTool>, number: (GulpTool) -> Int, firstOrder: Int, onOpen: (GulpTool) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tools.chunked(2).forEachIndexed { row, pair ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEachIndexed { col, tool ->
                    SkinToolBubble(tool, number(tool), order = firstOrder + row * 2 + col, Modifier.weight(1f).fillMaxHeight()) { onOpen(tool) }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Nothing matches: a magnifier that searches left and right, and what to try instead. */
@Composable
private fun HomeEmpty(query: String, category: ToolCategory?) {
    val transition = rememberInfiniteTransition(label = "emptySearch")
    val t by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "emptySearchT")
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp)
            .staggeredEntrance(1),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Rounded.Search,
            contentDescription = null,
            tint = LocalAccent.current.start,
            modifier = Modifier
                .size(46.dp)
                .graphicsLayer {
                    val a = t * 2f * PI.toFloat()
                    translationX = sin(a) * 14.dp.toPx()
                    translationY = -kotlin.math.abs(cos(a)) * 6.dp.toPx()
                    rotationZ = sin(a) * 12f
                },
        )
        Spacer(Modifier.height(14.dp))
        Text(
            if (query.isBlank()) "Aucun outil ici pour l'instant" else "Aucun outil pour « ${query.trim()} »",
            color = Palette.TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = LocalTokens.current.display,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (category != null) "Essaie un autre mot, ou la catégorie « Tout »." else "Essaie un autre mot : vidéo, PDF, WhatsApp…",
            color = Palette.TextSecondary,
            fontSize = 13.5.sp,
            textAlign = TextAlign.Center,
        )
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

/** "1 outil trouvé", "3 outils trouvés", "Aucun outil trouvé". */
internal fun resultsText(count: Int): String = when (count) {
    0 -> "Aucun outil trouvé"
    1 -> "1 outil trouvé"
    else -> "$count outils trouvés"
}

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
