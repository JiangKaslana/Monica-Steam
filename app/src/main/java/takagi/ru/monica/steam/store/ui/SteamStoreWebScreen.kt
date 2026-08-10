package takagi.ru.monica.steam.store.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import takagi.ru.monica.steam.store.data.*
import java.net.URI
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import java.security.SecureRandom
import takagi.ru.monica.R
import takagi.ru.monica.steam.store.gift.data.SteamStoreGiftCheckoutProtocol
import takagi.ru.monica.steam.store.gift.domain.SteamStoreCheckoutLine
import takagi.ru.monica.ui.common.selection.SelectionActionBar
import takagi.ru.monica.ui.common.selection.SelectionActionBarAction

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SteamStoreWebScreen(
    url: String,
    steamLoginSecure: String?,
    expectedSteamId: String? = null,
    title: String? = null,
    checkoutLines: List<SteamStoreCheckoutLine> = emptyList(),
    requireAuthenticatedSession: Boolean = false,
    clientMode: SteamWebClientMode = SteamWebClientMode.DEFAULT,
    onDownloadRequested: ((String) -> Unit)? = null,
    onPlatformViewVisibilityChanged: (Boolean) -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val initialBackground = when (clientMode) {
        SteamWebClientMode.COMMUNITY_DESKTOP -> Color(0xFF1B2838)
        SteamWebClientMode.DEFAULT -> MaterialTheme.colorScheme.background
    }
    val sessionDecision = remember(expectedSteamId, steamLoginSecure, requireAuthenticatedSession) {
        SteamWebAccountSessionPolicy.decide(
            expectedSteamId = expectedSteamId,
            steamLoginSecure = steamLoginSecure,
            requireAuthenticatedSession = requireAuthenticatedSession,
        )
    }
    val sessionScopeKey = remember(expectedSteamId, steamLoginSecure, requireAuthenticatedSession) {
        listOf(
            expectedSteamId.orEmpty(),
            steamLoginSecure.orEmpty(),
            requireAuthenticatedSession.toString(),
        )
    }
    var webView by remember(sessionScopeKey) { mutableStateOf<WebView?>(null) }
    var progress by remember(sessionScopeKey) { mutableIntStateOf(0) }
    var currentUrl by remember(sessionScopeKey, url) { mutableStateOf(url) }
    var canGoBack by remember(sessionScopeKey) { mutableStateOf(false) }
    var platformViewReady by remember(sessionScopeKey) { mutableStateOf(false) }
    var platformViewSignaled by remember(sessionScopeKey) { mutableStateOf(false) }
    val sessionId = remember(sessionScopeKey) { randomSessionId() }
    val checkoutQueue = remember(sessionScopeKey, checkoutLines) {
        checkoutLines.toMutableList()
    }
    val platformViewVisibilityCallback by rememberUpdatedState(
        onPlatformViewVisibilityChanged
    )
    val downloadRequestCallback by rememberUpdatedState(onDownloadRequested)

    LaunchedEffect(sessionScopeKey, sessionDecision.canLoad) {
        platformViewReady = false
        platformViewVisibilityCallback(true)
        platformViewSignaled = true
        if (sessionDecision.canLoad) {
            withFrameNanos { }
            platformViewReady = true
        }
    }

    DisposableEffect(sessionScopeKey, sessionDecision.canLoad) {
        onDispose {
            if (platformViewSignaled) {
                platformViewVisibilityCallback(false)
            }
        }
    }

    LaunchedEffect(sessionScopeKey, sessionDecision.canLoad) {
        if (!sessionDecision.canLoad) {
            CookieManager.getInstance().clearSteamCookies()
        }
    }

    fun handleBack() {
        val view = webView
        if (view?.canGoBack() == true) view.goBack() else onClose()
    }
    fun shareCurrentPage() {
        val target = currentUrl.takeIf(String::isNotBlank) ?: return
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title ?: target)
            putExtra(Intent.EXTRA_TEXT, target)
        }
        runCatching {
            context.startActivity(
                Intent.createChooser(
                    sendIntent,
                    context.getString(R.string.steam_web_share_chooser)
                )
            )
        }
    }
    fun openCurrentPageExternally() {
        val target = currentUrl.takeIf(String::isNotBlank) ?: return
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
        }
    }
    BackHandler(onBack = ::handleBack)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(initialBackground)
    ) {
        if (!sessionDecision.canLoad) {
            SteamWebSessionError(problem = sessionDecision.problem)
        } else if (!platformViewReady) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = initialBackground
            ) {}
        } else {
            key(sessionScopeKey) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                    factory = { factoryContext ->
                        val cookies = CookieManager.getInstance().apply { setAcceptCookie(true) }
                        WebView(factoryContext).apply webView@{
                            webView = this
                            setBackgroundColor(initialBackground.toArgb())
                            setRendererPriorityPolicy(
                                WebView.RENDERER_PRIORITY_IMPORTANT,
                                true
                            )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.defaultTextEncodingName = "utf-8"
                            settings.setSupportZoom(true)
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            settings.userAgentString = SteamWebClientPolicy.userAgent(
                                mode = clientMode,
                                defaultUserAgent = settings.userAgentString
                            )
                            val displayPolicy = SteamWebClientPolicy.displayPolicy(clientMode)
                            settings.useWideViewPort = displayPolicy.useWideViewPort
                            settings.loadWithOverviewMode = displayPolicy.loadWithOverviewMode
                            settings.textZoom = displayPolicy.textZoomPercent
                            settings.databaseEnabled = false
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.allowFileAccessFromFileURLs = false
                            settings.allowUniversalAccessFromFileURLs = false
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            settings.safeBrowsingEnabled = true
                            settings.setSupportMultipleWindows(false)
                            isVerticalScrollBarEnabled = true
                            isHorizontalScrollBarEnabled = false
                            scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
                            isScrollbarFadingEnabled = true
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                            setDownloadListener { downloadUrl, _, _, _, _ ->
                                downloadUrl
                                    ?.takeIf(String::isNotBlank)
                                    ?.let { downloadRequestCallback?.invoke(it) }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    progress = newProgress
                                }
                            }
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    progress = 1
                                    currentUrl = url.orEmpty().ifBlank { currentUrl }
                                    canGoBack = view?.canGoBack() == true
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    progress = 100
                                    currentUrl = url.orEmpty().ifBlank { currentUrl }
                                    canGoBack = view?.canGoBack() == true
                                    CookieManager.getInstance().flush()
                                    view?.postInvalidate()
                                    val next = checkoutQueue.removeFirstOrNull()
                                    if (next != null) {
                                        val body = SteamStoreGiftCheckoutProtocol.addToCartBody(
                                            sessionId = sessionId,
                                            line = next
                                        )
                                        view?.postUrl(
                                            "https://store.steampowered.com/cart/addtocart/",
                                            body.toByteArray(Charsets.UTF_8)
                                        )
                                    } else if (checkoutLines.isNotEmpty() && !isSteamCartPage(url)) {
                                        view?.loadUrl("https://store.steampowered.com/cart/")
                                    }
                                }

                                override fun doUpdateVisitedHistory(
                                    view: WebView?,
                                    url: String?,
                                    isReload: Boolean
                                ) {
                                    super.doUpdateVisitedHistory(view, url, isReload)
                                    currentUrl = url.orEmpty().ifBlank { currentUrl }
                                    canGoBack = view?.canGoBack() == true
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val target = request?.url?.toString().orEmpty()
                                    if (SteamStoreNavigationPolicy.isAllowed(target)) return false
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
                                    }
                                    return true
                                }
                            }
                            val targetAllowed = SteamStoreNavigationPolicy.isAllowed(url)
                            cookies.replaceSteamCookies(
                                SteamStoreSessionPolicy.cookieWrites(
                                    steamLoginSecure = steamLoginSecure.takeIf {
                                        sessionDecision.installAuthenticatedCookie
                                    },
                                    sessionId = sessionId,
                                    clientMode = clientMode
                                )
                            ) {
                                if (webView === this@webView && targetAllowed) {
                                    loadUrl(url)
                                }
                            }
                        }
                    }
                )
            }
        }
        if (progress in 1..99 && sessionDecision.canLoad) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .zIndex(2f)
            )
        }
        SelectionActionBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .zIndex(2f),
            selectedCount = 0,
            onExit = onClose,
            onSelectAll = {},
            showSelectionControls = false,
            actions = listOf(
                SelectionActionBarAction(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.steam_web_back),
                    enabled = canGoBack,
                    onClick = ::handleBack
                ),
                SelectionActionBarAction(
                    icon = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.steam_web_refresh),
                    enabled = webView != null,
                    onClick = { webView?.reload() }
                ),
                SelectionActionBarAction(
                    icon = Icons.Default.Share,
                    contentDescription = stringResource(R.string.steam_web_share),
                    enabled = currentUrl.isNotBlank(),
                    onClick = ::shareCurrentPage
                ),
                SelectionActionBarAction(
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = stringResource(R.string.steam_web_open_external),
                    enabled = currentUrl.isNotBlank(),
                    onClick = ::openCurrentPageExternally
                )
            ),
            exitContentDescription = stringResource(R.string.steam_web_return_to_monica),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.96f)
        )
    }

    DisposableEffect(sessionScopeKey) {
        onDispose {
            webView?.apply {
                stopLoading()
                setDownloadListener(null)
                webChromeClient = null
                webViewClient = WebViewClient()
                destroy()
            }
            webView = null
        }
    }
}

@Composable
private fun SteamWebSessionError(problem: SteamWebSessionProblem?) {
    val message = stringResource(
        when (problem) {
            SteamWebSessionProblem.IDENTITY_MISMATCH -> R.string.steam_web_session_identity_mismatch
            SteamWebSessionProblem.INVALID_SESSION -> R.string.steam_web_session_invalid
            SteamWebSessionProblem.EXPECTED_ACCOUNT_REQUIRED -> R.string.steam_web_session_account_required
            SteamWebSessionProblem.AUTHENTICATED_SESSION_REQUIRED,
            null -> R.string.steam_web_session_login_required
        }
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Text(
            text = stringResource(R.string.steam_web_session_unavailable),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

internal fun isSteamCartPage(url: String?): Boolean = runCatching {
    val uri = URI(url.orEmpty())
    uri.scheme.equals("https", ignoreCase = true) &&
        uri.host.equals("store.steampowered.com", ignoreCase = true) &&
        uri.path.trimEnd('/') == "/cart"
}.getOrDefault(false)

private fun randomSessionId(): String {
    val bytes = ByteArray(12).also(SecureRandom()::nextBytes)
    return bytes.joinToString("") { "%02x".format(it) }
}
