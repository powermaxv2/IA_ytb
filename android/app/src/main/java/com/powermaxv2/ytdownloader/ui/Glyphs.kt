package com.powermaxv2.ytdownloader.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Platform

// MARK: - Platform glyphs (simple generic shapes)

@Composable
fun PlatformGlyph(platform: Platform, modifier: Modifier = Modifier) {
    when (platform) {
        Platform.YOUTUBE -> Canvas(modifier) {
            val h = size.height * 0.72f
            val top = (size.height - h) / 2
            drawRoundRect(Color(0xFFFF0033), topLeft = Offset(0f, top), size = Size(size.width, h), cornerRadius = CornerRadius(h * 0.3f))
            val triangle = Path().apply {
                moveTo(size.width * 0.41f, top + h * 0.28f)
                lineTo(size.width * 0.41f, top + h * 0.72f)
                lineTo(size.width * 0.66f, top + h * 0.5f)
                close()
            }
            drawPath(triangle, Color.White)
        }
        Platform.INSTAGRAM -> Canvas(modifier) {
            val brush = Brush.linearGradient(
                listOf(Palette.InstaOrange, Palette.InstaPink, Palette.InstaPurple),
                start = Offset(0f, size.height),
                end = Offset(size.width, 0f),
            )
            val stroke = size.minDimension * 0.1f
            drawRoundRect(
                brush,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius(size.minDimension * 0.28f),
                style = Stroke(stroke),
            )
            drawCircle(brush, radius = size.minDimension * 0.2f, style = Stroke(stroke))
            drawCircle(brush, radius = stroke * 0.7f, center = Offset(size.width * 0.74f, size.height * 0.26f))
        }
        Platform.TWITTER -> Canvas(modifier) {
            blackTile()
            val s = size.minDimension
            val near = s * 0.29f
            val far = s * 0.71f
            drawLine(Color.White, Offset(near, near), Offset(far, far), strokeWidth = s * 0.11f, cap = StrokeCap.Round)
            drawLine(Color.White, Offset(far, near), Offset(near, far), strokeWidth = s * 0.075f, cap = StrokeCap.Round)
        }
        Platform.TIKTOK -> Canvas(modifier) {
            blackTile()
            val s = size.minDimension
            val note = Path().apply {
                moveTo(s * 0.57f, s * 0.20f)
                lineTo(s * 0.57f, s * 0.62f)
                moveTo(s * 0.57f, s * 0.20f)
                cubicTo(s * 0.57f, s * 0.34f, s * 0.66f, s * 0.40f, s * 0.78f, s * 0.40f)
                addOval(Rect(Offset(s * 0.44f, s * 0.64f), s * 0.115f))
            }
            val stroke = Stroke(s * 0.085f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            translate(-s * 0.03f, -s * 0.025f) { drawPath(note, Color(0xFF25F4EE), style = stroke) }
            translate(s * 0.03f, s * 0.025f) { drawPath(note, Color(0xFFFE2C55), style = stroke) }
            drawPath(note, Color.White, style = stroke)
        }
        Platform.SNAPCHAT -> Canvas(modifier) {
            val s = size.minDimension
            drawRoundRect(Color(0xFFFFFC00), cornerRadius = CornerRadius(s * 0.24f))
            val ghost = Path().apply {
                moveTo(s * 0.28f, s * 0.74f)
                lineTo(s * 0.28f, s * 0.44f)
                cubicTo(s * 0.28f, s * 0.26f, s * 0.38f, s * 0.16f, s * 0.50f, s * 0.16f)
                cubicTo(s * 0.62f, s * 0.16f, s * 0.72f, s * 0.26f, s * 0.72f, s * 0.44f)
                lineTo(s * 0.72f, s * 0.74f)
                quadraticTo(s * 0.647f, s * 0.80f, s * 0.573f, s * 0.74f)
                quadraticTo(s * 0.50f, s * 0.68f, s * 0.427f, s * 0.74f)
                quadraticTo(s * 0.353f, s * 0.80f, s * 0.28f, s * 0.74f)
                close()
            }
            drawPath(ghost, Color.White)
            drawPath(ghost, Color(0xFF1A1A1A), style = Stroke(s * 0.05f, join = StrokeJoin.Round))
        }
        Platform.FACEBOOK -> Canvas(modifier) {
            // Blue disc with the white "f" running off its bottom edge, drawn on a 24-unit grid.
            val k = size.minDimension / 24f
            val disc = Path().apply { addOval(Rect(Offset(12f * k, 12f * k), 12f * k)) }
            drawPath(disc, Color(0xFF0866FF))
            val f = Path().apply {
                moveTo(9.1f * k, 25f * k)
                lineTo(9.1f * k, 15.71f * k)
                lineTo(6.63f * k, 15.71f * k)
                lineTo(6.63f * k, 12.04f * k)
                lineTo(9.1f * k, 12.04f * k)
                lineTo(9.1f * k, 10.46f * k)
                cubicTo(9.1f * k, 6.38f * k, 10.95f * k, 4.49f * k, 14.96f * k, 4.49f * k)
                cubicTo(15.6f * k, 4.49f * k, 16.9f * k, 4.6f * k, 17.57f * k, 4.78f * k)
                lineTo(17.57f * k, 8.11f * k)
                cubicTo(17.2f * k, 8.07f * k, 16.6f * k, 8.06f * k, 16.18f * k, 8.06f * k)
                cubicTo(14.37f * k, 8.06f * k, 13.45f * k, 8.75f * k, 13.45f * k, 10.75f * k)
                lineTo(13.45f * k, 12.04f * k)
                lineTo(17.37f * k, 12.04f * k)
                lineTo(16.7f * k, 15.71f * k)
                lineTo(13.45f * k, 15.71f * k)
                lineTo(13.45f * k, 25f * k)
                close()
            }
            clipPath(disc) { drawPath(f, Color.White) }
        }
        Platform.BEREAL -> BoxWithConstraints(
            modifier
                .clip(RoundedCornerShape(20))
                .background(Color(0xFF0B0B0F))
                .border(0.7.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(20)),
            contentAlignment = Alignment.Center,
        ) {
            val side = minOf(maxWidth, maxHeight)
            Text(
                "Be",
                color = Color.White,
                fontSize = with(LocalDensity.current) { (side * 0.48f).toSp() },
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.4).sp,
                maxLines = 1,
                softWrap = false,
            )
        }
        Platform.OTHER -> Icon(
            Icons.Rounded.Link,
            contentDescription = null,
            modifier = modifier.gradientTint(LocalAccent.current.brush),
            tint = Color.White,
        )
    }
}

/** Near-black rounded square with a faint outline, the backdrop of the X and TikTok logos. */
private fun DrawScope.blackTile() {
    val s = size.minDimension
    val corner = CornerRadius(s * 0.24f)
    drawRoundRect(Color(0xFF0B0B0F), cornerRadius = corner)
    val stroke = s * 0.05f
    drawRoundRect(
        Color.White.copy(alpha = 0.32f),
        topLeft = Offset(stroke / 2, stroke / 2),
        size = Size(s - stroke, s - stroke),
        cornerRadius = corner,
        style = Stroke(stroke),
    )
}

/** Glyph that pops (overshooting spring) whenever the detected platform changes. */
@Composable
fun AnimatedPlatformGlyph(platform: Platform, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = platform,
        transitionSpec = {
            (scaleIn(spring(dampingRatio = 0.42f, stiffness = 380f), initialScale = 0.2f) + fadeIn()) togetherWith
                (scaleOut(targetScale = 0.2f) + fadeOut(tween(120)))
        },
        label = "glyph",
        modifier = modifier,
    ) { target ->
        PlatformGlyph(target, Modifier.fillMaxSize())
    }
}
