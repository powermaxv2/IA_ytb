package com.powermaxv2.ytdownloader.ui.hub

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.MainViewModel
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.UpdateBanner
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
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
 * Home screen of the hub: search bar and category chips, the starred tools ("Favoris"), the last tool used in a
 * big card, the other tools that are out as small bubbles grouped by category, then the "À venir" ones.
 */
@Composable
fun HomeScreen(vm: MainViewModel, insets: PaddingValues) {
    HomeContent(
        tools = ToolRegistry.tools,
        lastUsedId = vm.lastToolId,
        favoriteIds = vm.favorites,
        onStar = { vm.toggleFavorite(it.id) },
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
 * filter the tools; with neither, the starred tools come first, then the last tool used big (unless it is
 * starred), and the others grouped by category. Every tool wears its star when [onStar] is given.
 */
@Composable
fun HomeContent(
    tools: List<GulpTool>,
    lastUsedId: String?,
    onOpen: (GulpTool) -> Unit,
    favoriteIds: List<String> = emptyList(),
    onStar: ((GulpTool) -> Unit)? = null,
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
    val favorites = Favorites.of(favoriteIds, ready)
    var justStarred by remember { mutableStateOf<String?>(null) }
    val stars = StarRef(
        favoriteIds,
        onStar?.let { star ->
            { tool: GulpTool ->
                justStarred = if (tool.id in favoriteIds) null else tool.name
                star(tool)
            }
        },
    )
    val bobClock = rememberInfiniteTransition(label = "homeBob")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(BOB_PERIOD_MS, easing = LinearEasing)), label = "homeBobPhase")
    CompositionLocalProvider(LocalJustStarred provides justStarred, LocalBobClock provides bobClock) {
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
            val found = if (filtering) {
                ready.filter { (category == null || ToolCategories.of(it) == category) && ToolCategories.matches(it, query) }
            } else {
                emptyList()
            }
            SkinSearchBar(
                query = query,
                onQueryChange = { query = it },
                suggestions = ready.map { it.name },
                onSubmit = { if (found.size == 1) onOpen(found.first()) },
            )
            SkinCategoryChips(
                categories = categories,
                counts = categories.associateWith { c -> ready.count { ToolCategories.of(it) == c } },
                total = ready.size,
                selected = category,
                onSelect = { categoryName = it?.name },
            )
            if (filtering) {
                val title = when {
                    query.isNotBlank() -> resultsText(found.size)
                    else -> category!!.label
                }
                // Keyed by the filter: every new search deals its bubbles again, in cascade.
                key(category, ToolCategories.fold(listOf(query))) {
                    SkinHomeSection(title, order = 0)
                    if (found.isEmpty()) {
                        HomeEmpty(query, category) {
                            query = ""
                            categoryName = null
                        }
                    } else {
                        BubbleGrid(found, ::number, firstOrder = 1, stars, onOpen = onOpen)
                    }
                }
            } else {
                var order = 0
                if (favorites.isNotEmpty()) {
                    // Keyed by the list: a new star deals the favourites again, in cascade, with the new one in place.
                    key(favoriteIds) {
                        FavoritesSection(favorites.size, order = order++)
                        BubbleGrid(favorites, ::number, firstOrder = order, stars, onOpen = onOpen)
                    }
                    order += favorites.size
                } else if (onStar != null) {
                    FavoritesHint(order = order++)
                }
                // The big card shows the last tool used, unless it is already up there in the favourites.
                val others = ready.filterNot { it in favorites }
                val featured = if (others.isEmpty() || favorites.any { it.id == lastUsedId }) null else featuredTool(others, lastUsedId)
                if (featured != null) {
                    SkinHomeSection(if (featured.id == lastUsedId) "Dernier utilisé" else "Pour commencer", order = order++)
                    // Keyed by the tool, so a new featured tool plays its entrance again instead of morphing the old card.
                    key(featured.id) {
                        SkinToolCard(featured, number(featured), featured.id in favoriteIds, stars.onStar?.let { star -> { star(featured) } }) { onOpen(featured) }
                    }
                }
                categories.forEach { c ->
                    val inCategory = ready.filter { it != featured && it !in favorites && ToolCategories.of(it) == c }
                    if (inCategory.isNotEmpty()) {
                        SkinHomeSection(c.label, order = order++)
                        BubbleGrid(inCategory, ::number, firstOrder = order, stars, onOpen = onOpen)
                        order += inCategory.size
                    }
                }
                if (upcoming.isNotEmpty()) SkinComingSoon(upcoming, firstNumber = ready.size + 1)
            }
        }
    }
}

/** Which tools are starred, and what a star does (null: no stars drawn). */
private class StarRef(val ids: List<String>, val onStar: ((GulpTool) -> Unit)?)

/** Tools as small bubbles, two per row of equal height, each with its star. */
@Composable
private fun BubbleGrid(tools: List<GulpTool>, number: (GulpTool) -> Int, firstOrder: Int, stars: StarRef, onOpen: (GulpTool) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tools.chunked(2).forEachIndexed { row, pair ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEachIndexed { col, tool ->
                    SkinToolBubble(
                        tool,
                        number(tool),
                        order = firstOrder + row * 2 + col,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        starred = tool.id in stars.ids,
                        onStar = stars.onStar?.let { star -> { star(tool) } },
                    ) { onOpen(tool) }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Title of the starred tools: the skin's section title, with a slowly turning star in front. */
@Composable
private fun FavoritesSection(count: Int, order: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FavoritesGlyph(Modifier.padding(end = 8.dp, top = 4.dp).staggeredEntrance(order, baseDelay = 220))
        Box(Modifier.weight(1f)) { SkinHomeSection(if (count > 1) "Favoris · $count" else "Favoris", order = order) }
    }
}

/** No star yet: one line telling where they are, its star turning. */
@Composable
private fun FavoritesHint(order: Int) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Row(
        Modifier
            .fillMaxWidth()
            .staggeredEntrance(order, baseDelay = 220)
            .clip(RoundedCornerShape(LocalTokens.current.cardRadius))
            .background(Palette.Surface.copy(alpha = 0.6f))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FavoritesGlyph(size = 16.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            if (terminal) "# touche [ ] sur un outil pour l'épingler ici" else "Touche l'étoile d'un outil pour l'épingler ici, en haut.",
            color = Palette.TextSecondary,
            fontSize = 13.sp,
            lineHeight = 17.sp,
        )
    }
}

/** Nothing matches: a magnifier that searches left and right, and what to try instead. */
@Composable
private fun HomeEmpty(query: String, category: ToolCategory?, onReset: () -> Unit) {
    val terminal = LocalSkin.current == Skin.TERMINAL
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
        Spacer(Modifier.height(16.dp))
        com.powermaxv2.ytdownloader.ui.TonalButton(
            text = if (terminal) "[ TOUT AFFICHER ]" else "Tout afficher",
            icon = null,
            compact = true,
            onClick = onReset,
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
    // On the home screen every bubble reads the same clock: one animator instead of one per bubble.
    val shared = LocalBobClock.current
    val own = if (shared == null && periodMs > 0) {
        val transition = rememberInfiniteTransition(label = "bob")
        transition.animateFloat(0f, 1f, infiniteRepeatable(tween(periodMs, easing = LinearEasing)), label = "bobPhase")
    } else {
        null
    }
    return graphicsLayer {
        val t = (shared ?: own)?.value ?: 0f
        val angle = (t * 2f * PI.toFloat()) - index * 0.9f
        translationY = sin(angle) * amplitude.toPx()
        rotationZ = sin(angle + 0.6f) * 3f
    }
}

/** The home screen's shared floating clock (0..1 over [BOB_PERIOD_MS]); null elsewhere, where each [bob] runs its own. */
val LocalBobClock = staticCompositionLocalOf<State<Float>?> { null }

private const val BOB_PERIOD_MS = 2400

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
