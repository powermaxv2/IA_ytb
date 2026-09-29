package com.powermaxv2.ytdownloader.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.powermaxv2.ytdownloader.core.InstagramSession
import com.powermaxv2.ytdownloader.core.Platform
import kotlinx.coroutines.delay

/**
 * Hosts the login page may navigate to: Instagram, Meta's account and security-check pages, and Facebook for
 * "Log in with Facebook". Frames inside the page (captchas…) are not restricted.
 */
private val allowedHosts = listOf(
    "instagram.com", "cdninstagram.com", "facebook.com", "facebook.net", "fbcdn.net", "fbsbx.com", "meta.com",
)

private fun hasSessionCookie(): Boolean =
    runCatching { CookieManager.getInstance().getCookie(InstagramSession.HOME) }.getOrNull()?.contains("sessionid=") == true

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InstagramLoginScreen(onClose: () -> Unit, onLoggedIn: () -> Unit) {
    var loading by remember { mutableStateOf(true) }
    val currentOnLoggedIn by rememberUpdatedState(onLoggedIn)
    BackHandler(onBack = onClose)
    // Instagram often finishes the login without loading a new page (it is a web app): watch the cookies instead.
    LaunchedEffect(Unit) {
        while (true) {
            delay(700)
            if (hasSessionCookie()) currentOnLoggedIn()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconCircleButton(Icons.Rounded.Close, onClick = onClose)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Connexion Instagram", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = Palette.Success, modifier = Modifier.size(12.dp))
                    Text("instagram.com · mot de passe jamais lu par l'app", color = Palette.TextSecondary, fontSize = 11.sp)
                }
            }
            PlatformGlyph(Platform.INSTAGRAM, Modifier.size(28.dp).pulsing(0.95f, 1.05f, 1600))
        }
        Box(Modifier.fillMaxWidth()) {
            androidx.compose.animation.AnimatedVisibility(visible = loading, enter = fadeIn(), exit = fadeOut()) {
                GradientProgressBar(-1f, height = 3.dp)
            }
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = InstagramSession.browserUserAgent(settings.userAgentString)
                    val cookies = CookieManager.getInstance()
                    cookies.setAcceptCookie(true)
                    cookies.setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            if (!request.isForMainFrame) return false
                            val url = request.url
                            val host = url.host?.lowercase() ?: return true
                            val allowed = url.scheme == "https" && allowedHosts.any { host == it || host.endsWith(".$it") }
                            return !allowed
                        }

                        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                            loading = true
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            loading = false
                            if (hasSessionCookie()) currentOnLoggedIn()
                        }

                        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                            if (hasSessionCookie()) currentOnLoggedIn()
                        }
                    }
                    loadUrl(InstagramSession.LOGIN_URL)
                }
            },
            onRelease = { webView ->
                webView.stopLoading()
                webView.destroy()
            },
        )
    }
}
