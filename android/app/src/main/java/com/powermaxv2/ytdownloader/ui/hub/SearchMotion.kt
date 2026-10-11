package com.powermaxv2.ytdownloader.ui.hub

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow

// Motion shared by the search bars of the three skins: the hint that cycles through tool names, the field
// itself (focus state, keyboard "search" key) and the clear button that pops in.

/** Index into [count] items that moves on every [periodMs]: drives the cycling hint of the search bar. */
@Composable
fun rememberCycle(count: Int, periodMs: Int = 2400): State<Int> {
    val transition = rememberInfiniteTransition(label = "hintCycle")
    val t by transition.animateFloat(
        0f, count.coerceAtLeast(1).toFloat(), infiniteRepeatable(tween(periodMs * count.coerceAtLeast(1), easing = LinearEasing)), label = "hintCycleT",
    )
    return remember(count) { derivedStateOf { t.toInt().coerceIn(0, (count - 1).coerceAtLeast(0)) } }
}

/** "[prefix]« Compresseur »", the name rolling up to the next tool every couple of seconds. */
@Composable
fun RollingHint(prefix: String, words: List<String>, style: TextStyle, color: Color, quote: (String) -> String = { "« $it »" }) {
    val index by rememberCycle(words.size)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(prefix, color = color, style = style, maxLines = 1)
        AnimatedContent(
            targetState = words.getOrNull(index).orEmpty(),
            transitionSpec = {
                (slideInVertically(spring(dampingRatio = 0.7f, stiffness = 300f)) { it } + fadeIn(tween(220)))
                    .togetherWith(slideOutVertically(tween(220)) { -it } + fadeOut(tween(160)))
            },
            label = "rollingHint",
        ) { word ->
            Text(if (word.isEmpty()) "" else quote(word), color = color, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The text field of a search bar: single line, "search" key closes the keyboard. [decoration] draws the hint
 * around the typed text ([inner]); [focused] follows the focus so the skin can light its outline.
 */
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    style: TextStyle,
    cursor: Color,
    interaction: MutableInteractionSource,
    modifier: Modifier = Modifier,
    hint: @Composable () -> Unit,
) {
    val focus = LocalFocusManager.current
    BasicTextField(
        value = query,
        onValueChange = { onQueryChange(it.take(60)) },
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(cursor),
        interactionSource = interaction,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        modifier = modifier.semantics { contentDescription = "Chercher un outil" },
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) hint()
                inner()
            }
        },
    )
}

@Composable
fun MutableInteractionSource.focusedState(): State<Boolean> = collectIsFocusedAsState()

/** The clear button pops in (spring scale) once something is typed and shrinks away when the field is empty. */
@Composable
fun RowScope.ClearPop(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(spring(dampingRatio = 0.5f, stiffness = 500f)) + fadeIn(),
        exit = scaleOut(tween(140)) + fadeOut(tween(140)),
    ) { content() }
}
