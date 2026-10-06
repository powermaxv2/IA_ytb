package com.powermaxv2.ytdownloader.ui.tools.preview

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powermaxv2.ytdownloader.core.Skin
import com.powermaxv2.ytdownloader.core.preview.PdfPages
import com.powermaxv2.ytdownloader.core.preview.PdfUnreadable
import com.powermaxv2.ytdownloader.core.preview.PreviewFile
import com.powermaxv2.ytdownloader.ui.LocalAccent
import com.powermaxv2.ytdownloader.ui.Palette
import com.powermaxv2.ytdownloader.ui.bounceClick
import com.powermaxv2.ytdownloader.ui.shimmer
import com.powermaxv2.ytdownloader.ui.skin.LocalSkin
import com.powermaxv2.ytdownloader.ui.skin.LocalTokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.concurrent.thread

// PDF viewer: one page at a time in a pager (swipe), each page rendered off the main thread into a bitmap of at
// most 2048 px, pinch to zoom. The renderer and its descriptor are closed when the viewer leaves.

/** Owner of the open document across the async open and the dispose (whichever comes last closes it). */
private class PdfHolder {
    var pages: PdfPages? = null
    var disposed = false
}

@Composable
internal fun PdfViewer(file: PreviewFile, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val holder = remember(file.uri) { PdfHolder() }
    var pages by remember(file.uri) { mutableStateOf<PdfPages?>(null) }
    var error by remember(file.uri) { mutableStateOf<String?>(null) }

    LaunchedEffect(file.uri) {
        val result = withContext(Dispatchers.IO) {
            try {
                val opened = PdfPages.open(context, file.uri)
                synchronized(holder) {
                    if (holder.disposed) {
                        opened.close()
                        null
                    } else {
                        holder.pages = opened
                        opened
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: PdfUnreadable) {
                error = e.message
                null
            } catch (e: Throwable) {
                error = "Ce PDF est illisible ou abîmé."
                null
            }
        }
        pages = result
        if (result != null && result.pageCount == 0) error = "Ce PDF n'a aucune page."
    }
    DisposableEffect(file.uri) {
        onDispose {
            val toClose = synchronized(holder) {
                holder.disposed = true
                holder.pages.also { holder.pages = null }
            }
            // Closing waits for a page being rendered: never on the main thread.
            if (toClose != null) thread(name = "gulp-pdf-close") { toClose.close() }
        }
    }

    val doc = pages
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ViewerFrame {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(if (LocalTokens.current.isLight) Color(0xFFEFE3D7) else Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    error != null -> Text(
                        error.orEmpty(),
                        color = Palette.TextSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp),
                    )
                    doc == null -> Box(Modifier.fillMaxSize().shimmer())
                    else -> PdfPager(doc)
                }
            }
        }
    }
}

@Composable
private fun PdfPager(doc: PdfPages) {
    val pager = rememberPagerState { doc.pageCount }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val widthPx = with(LocalDensity.current) { (maxWidth.toPx() * 1.5f).toInt() }.coerceIn(400, PdfPages.MAX_SIDE)
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), pageSpacing = 12.dp) { index ->
                PdfPage(doc, index, widthPx)
            }
        }
        // Page counter with arrows, at the bottom.
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PageArrow(left = true, enabled = pager.currentPage > 0) {
                scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
            }
            PageCounter(pager.currentPage + 1, doc.pageCount)
            PageArrow(left = false, enabled = pager.currentPage < doc.pageCount - 1) {
                scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
            }
        }
    }
}

@Composable
private fun PdfPage(doc: PdfPages, index: Int, widthPx: Int) {
    val scope = rememberCoroutineScope()
    val zoom = remember(doc, index) { ZoomState() }
    val bitmap by produceState<Bitmap?>(null, doc, index, widthPx) {
        value = withContext(Dispatchers.IO) {
            try {
                doc.render(index, widthPx)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // OutOfMemoryError on a huge page, a renderer error: the page stays blank.
                null
            }
        }
    }
    Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
        val b = bitmap
        if (b == null) {
            Box(
                Modifier
                    .fillMaxSize(0.8f)
                    .background(Color.White.copy(alpha = 0.6f))
                    .shimmer(),
            )
        } else {
            val image = remember(b) { b.asImageBitmap() }
            Image(
                image,
                contentDescription = "Page ${index + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .zoomable(zoom, scope),
            )
        }
    }
}

@Composable
private fun PageCounter(page: Int, count: Int) {
    val terminal = LocalSkin.current == Skin.TERMINAL
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val up = targetState > initialState
                (slideInVertically(spring(dampingRatio = 0.7f, stiffness = 420f)) { if (up) it else -it } + fadeIn(tween(140))) togetherWith
                    (slideOutVertically(tween(160)) { if (up) -it else it } + fadeOut(tween(120)))
            },
            label = "pdfPage",
        ) { p ->
            Text(p.toString(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = MonoFamily)
        }
        Text(if (terminal) "/$count" else " / $count", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, fontFamily = MonoFamily)
    }
}

@Composable
private fun PageArrow(left: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current.start
    Box(
        Modifier
            .size(36.dp)
            .bounceClick(enabled = enabled, pressedScale = 0.85f, onClick = onClick)
            .graphicsLayer { alpha = if (enabled) 1f else 0.3f },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (left) Icons.AutoMirrored.Rounded.KeyboardArrowLeft else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = if (left) "Page précédente" else "Page suivante",
            tint = if (enabled) Color.White else accent,
            modifier = Modifier.size(24.dp),
        )
    }
}
