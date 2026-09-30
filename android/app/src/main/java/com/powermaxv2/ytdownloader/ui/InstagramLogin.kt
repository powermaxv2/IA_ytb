package com.powermaxv2.ytdownloader.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.powermaxv2.ytdownloader.core.InstagramSession
import com.powermaxv2.ytdownloader.core.Platform

/**
 * Hosts the login page may navigate to: Instagram, Meta's account and security-check pages, and Facebook for
 * "Log in with Facebook". Frames inside the page (captchas…) are not restricted.
 */
private val allowedHosts = listOf(
    "instagram.com", "cdninstagram.com", "facebook.com", "facebook.net", "fbcdn.net", "fbsbx.com", "meta.com",
)

private fun hasSessionCookie(): Boolean =
    runCatching { CookieManager.getInstance().getCookie(InstagramSession.HOME) }.getOrNull()?.contains("sessionid=") == true

/**
 * Instagram login on instagram.com, in its own screen: the WebView sits in a plain view layout (no Compose
 * animation around it), and anything that keeps the page from showing is reported on screen.
 * Finishes with RESULT_OK as soon as Instagram has set the session cookie.
 */
class InstagramLoginActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var defaultAgent: String
    private var pageProgress by mutableIntStateOf(0)
    private var problem by mutableStateOf<String?>(null)
    private var problemDetail by mutableStateOf<String?>(null)
    private var lastScriptError: String? = null
    private var browserAgent = false
    private var rendererGone = false
    private var done = false
    private val timers = Handler(Looper.getMainLooper())

    // Instagram often finishes the login without loading a new page (it is a web app): watch the cookies.
    private val watchCookies = object : Runnable {
        override fun run() {
            if (!checkLogin()) timers.postDelayed(this, 700)
        }
    }
    private val watchSlowLoad = Runnable {
        if (pageProgress < 100 && problem == null) {
            showProblem("Instagram met du temps à répondre.", "Vérifiez la connexion Internet, puis réessayez.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        webView = createWebView()
        val header = ComposeView(this).apply {
            setContent {
                AppTheme(Platform.INSTAGRAM) {
                    LoginHeader(pageProgress, problem, problemDetail, onClose = ::finish, onRetry = ::retry)
                }
            }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Palette.Background.toArgb())
            addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            WindowInsetsCompat.CONSUMED
        }
        setContentView(root)
        onBackPressedDispatcher.addCallback(this) {
            if (!rendererGone && webView.canGoBack()) webView.goBack() else finish()
        }
        webView.loadUrl(InstagramSession.LOGIN_URL)
        timers.postDelayed(watchCookies, 700)
    }

    override fun onDestroy() {
        timers.removeCallbacksAndMessages(null)
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }

    private fun checkLogin(): Boolean {
        if (done || !hasSessionCookie()) return done
        done = true
        CookieManager.getInstance().flush()
        setResult(RESULT_OK)
        finish()
        return true
    }

    private fun showProblem(message: String, detail: String?) {
        problem = message
        problemDetail = detail
    }

    /** Reloads the page, alternating between the WebView's own identity and plain Chrome's. */
    private fun retry() {
        if (rendererGone) {
            recreate()
            return
        }
        problem = null
        problemDetail = null
        lastScriptError = null
        browserAgent = !browserAgent
        webView.settings.userAgentString = if (browserAgent) InstagramSession.browserUserAgent(defaultAgent) else defaultAgent
        webView.loadUrl(InstagramSession.LOGIN_URL)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView = WebView(this).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        defaultAgent = settings.userAgentString
        // WebView adds an "X-Requested-With: <package name>" header to every request. Instagram's CDN
        // rejects it in its CORS check, so the login page's scripts never load and the page stays stuck on
        // the Instagram splash. An empty allow-list stops WebView from sending that header to any site.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
            WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet())
        }
        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(this, true)
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                pageProgress = newProgress
            }

            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) lastScriptError = message.message().take(240)
                return false
            }
        }
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                val url = request.url
                val host = url.host?.lowercase() ?: return true
                val allowed = url.scheme == "https" && allowedHosts.any { host == it || host.endsWith(".$it") }
                return !allowed
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                timers.removeCallbacks(watchSlowLoad)
                timers.postDelayed(watchSlowLoad, 25_000)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                timers.removeCallbacks(watchSlowLoad)
                if (checkLogin()) return
                // A page that stays empty a few seconds after loading: its script did not run.
                timers.postDelayed({
                    if (done || rendererGone || problem != null) return@postDelayed
                    view.evaluateJavascript("(function(){return document.body ? document.body.innerText.trim().length : 0})()") { length ->
                        if ((length?.toIntOrNull() ?: 0) == 0 && problem == null) {
                            showProblem(
                                "La page Instagram est restée vide.",
                                lastScriptError?.let { "Détail : $it" } ?: "Appuyez sur Réessayer.",
                            )
                        }
                    }
                }, 6_000)
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                checkLogin()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    showProblem("Impossible de charger instagram.com.", "${error.description} (code ${error.errorCode})")
                }
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame && response.statusCode >= 400) {
                    showProblem("Instagram a répondu par une erreur ${response.statusCode}.", "Réessayez dans un instant.")
                }
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                rendererGone = true
                showProblem("Le moteur web du téléphone s'est arrêté.", "Réessayez, ou mettez à jour « Android System WebView ».")
                return true
            }
        }
    }
}

@Composable
private fun LoginHeader(progress: Int, problem: String?, detail: String?, onClose: () -> Unit, onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Palette.Background),
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
            IconCircleButton(Icons.Rounded.Refresh, Modifier.padding(end = 10.dp), onClick = onRetry)
            PlatformGlyph(Platform.INSTAGRAM, Modifier.size(28.dp).pulsing(0.95f, 1.05f, 1600))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp),
        ) {
            if (progress < 100) GradientProgressBar(if (progress < 10) -1f else progress / 100f, height = 3.dp)
        }
        AnimatedVisibility(
            visible = problem != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                Modifier
                    .padding(14.dp)
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(16.dp), Palette.Danger.copy(alpha = 0.12f))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Palette.Danger, modifier = Modifier.size(20.dp))
                    Text(problem ?: "", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                detail?.let { Text(it, color = Palette.TextSecondary, fontSize = 12.sp) }
                SecondaryButton("Réessayer", Icons.Rounded.Refresh, compact = true, onClick = onRetry)
            }
        }
    }
}
