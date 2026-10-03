package com.powermaxv2.ytdownloader.ui.hub

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.powermaxv2.ytdownloader.ui.bounceClick

/**
 * Wraps the Gulp logo at the top of a tool: with [onHome], a small chevron that nudges left now and then sits
 * before it and a tap goes back to the home screen of the hub. Without it, just the logo.
 */
@Composable
fun HomeLink(onHome: (() -> Unit)?, tint: Color, modifier: Modifier = Modifier, chevron: Dp = 22.dp, content: @Composable () -> Unit) {
    if (onHome == null) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) { content() }
        return
    }
    val transition = rememberInfiniteTransition(label = "homeNudge")
    // Rests most of the cycle, then two little pulls to the left: "you can go back".
    val nudge by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "homeNudgeX",
    )
    Row(
        modifier
            .semantics(mergeDescendants = true) {
                contentDescription = "Retour à l'accueil"
                role = Role.Button
            }
            .bounceClick(pressedScale = 0.94f, onClick = onHome),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.ChevronLeft,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(chevron)
                .graphicsLayer {
                    val t = nudge
                    // Two short pulls between 70 % and 100 % of the cycle.
                    val pull = if (t < 0.7f) 0f else kotlin.math.sin((t - 0.7f) / 0.3f * 2f * Math.PI.toFloat()).let { if (it > 0f) it else it * 0.4f }
                    translationX = -pull * 4.dp.toPx()
                },
        )
        Spacer(Modifier.width(2.dp))
        content()
    }
}
