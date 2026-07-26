package takagi.ru.monica.steam.store.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import takagi.ru.monica.steam.store.data.*
import java.net.URLEncoder
import java.net.URI
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import java.security.SecureRandom
import takagi.ru.monica.R

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SteamStoreWebScreen(
    url: String,
    steamLoginSecure: String?,
    expectedSteamId: String? = null,
    title: String? = null,
    securityNote: String? = null,
    checkoutPackageIds: List<Int> = emptyList(),
    requireAuthenticatedSession: Boolean = false,
    clientMode: SteamWebClientMode = SteamWebClientMode.DEFAULT,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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
    val sessionId = remember(sessionScopeKey) { randomSessionId() }
    val checkoutQueue = remember(sessionScopeKey, checkoutPackageIds) {
        checkoutPackageIds.toMutableList()
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
    BackHandler(onBack = ::handleBack)

    Column(modifier = modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = ::handleBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
                Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                    Text(
                        text = title ?: stringResource(R.string.steam_store_web_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = securityNote ?: stringResource(R.string.steam_store_security_note),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.steam_store_close))
                }
            }
        }
        if (progress in 1..99 && sessionDecision.canLoad) {
            LinearProgressIndicator(progress = { progress / 100f })
        }
        if (!sessionDecision.canLoad) {
            SteamWebSessionError(problem = sessionDecision.problem)
        } else {
            key(sessionScopeKey) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { factoryContext ->
                        val cookies = CookieManager.getInstance().apply { setAcceptCookie(true) }
                        WebView(factoryContext).apply webView@{
                            webView = this
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.userAgentString = SteamWebClientPolicy.userAgent(
                                mode = clientMode,
                                defaultUserAgent = settings.userAgentString
                            )
                            settings.useWideViewPort = SteamWebClientPolicy.usesDesktopLayout(clientMode)
                            settings.loadWithOverviewMode = SteamWebClientPolicy.usesDesktopLayout(clientMode)
                            settings.databaseEnabled = false
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.allowFileAccessFromFileURLs = false
                            settings.allowUniversalAccessFromFileURLs = false
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            settings.safeBrowsingEnabled = true
                            settings.setSupportMultipleWindows(false)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    progress = newProgress
                                }
                            }
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    progress = 1
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    val next = checkoutQueue.removeFirstOrNull()
                                    if (next != null) {
                                        val body = "action=add_to_cart&sessionid=${encodeForm(sessionId)}&subid=$next"
                                        view?.postUrl(
                                            "https://store.steampowered.com/cart/addtocart/",
                                            body.toByteArray(Charsets.UTF_8)
                                        )
                                    } else if (checkoutPackageIds.isNotEmpty() && !isSteamCartPage(url)) {
                                        view?.loadUrl("https://store.steampowered.com/cart/")
                                    }
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
    }

    DisposableEffect(sessionScopeKey) {
        onDispose {
            webView?.apply {
                stopLoading()
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

private fun encodeForm(value: String): String = URLEncoder.encode(value, "UTF-8")

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
