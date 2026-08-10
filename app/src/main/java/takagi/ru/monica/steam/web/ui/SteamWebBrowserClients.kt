package takagi.ru.monica.steam.web.ui

import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Message
import android.view.View
import android.webkit.HttpAuthHandler
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import takagi.ru.monica.steam.web.domain.SteamWebFailureKind
import takagi.ru.monica.steam.web.domain.SteamWebNavigationPolicy
import takagi.ru.monica.steam.web.domain.SteamWebPageFailure

internal class SteamBrowserWebViewClient(
    private val openExternal: (String) -> Boolean,
    private val onPageStarted: (WebView, String) -> Unit,
    private val onPageCommitVisible: (WebView, String) -> Unit,
    private val onPageFinished: (WebView, String) -> Unit,
    private val onHistoryChanged: (WebView, String) -> Unit,
    private val onFailure: (SteamWebPageFailure) -> Unit,
    private val onRendererGone: (WebView, Boolean) -> Unit,
) : WebViewClient() {
    private var hasCommittedContent = false

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        if (!hasCommittedContent) view.alpha = 0f
        onPageStarted(view, url)
    }

    override fun onPageCommitVisible(view: WebView, url: String) {
        hasCommittedContent = true
        view.alpha = 1f
        onPageCommitVisible(view, url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        hasCommittedContent = true
        view.alpha = 1f
        onPageFinished(view, url)
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        onHistoryChanged(view, url.orEmpty())
    }

    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest
    ): Boolean {
        val target = request.url?.toString().orEmpty()
        if (SteamWebNavigationPolicy.isAllowed(target)) return false
        if (!request.isForMainFrame) return true
        if (SteamWebNavigationPolicy.isSafeExternal(target) && openExternal(target)) return true
        onFailure(
            SteamWebPageFailure(
                kind = SteamWebFailureKind.UNSAFE_NAVIGATION,
                failingUrl = target
            )
        )
        return true
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError
    ) {
        if (!request.isForMainFrame) return
        onFailure(
            SteamWebPageFailure(
                kind = SteamWebFailureKind.NETWORK,
                description = error.description?.toString(),
                failingUrl = request.url?.toString()
            )
        )
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse
    ) {
        if (!request.isForMainFrame || errorResponse.statusCode < 400) return
        onFailure(
            SteamWebPageFailure(
                kind = SteamWebFailureKind.HTTP,
                description = errorResponse.reasonPhrase,
                failingUrl = request.url?.toString(),
                statusCode = errorResponse.statusCode
            )
        )
    }

    override fun onReceivedSslError(
        view: WebView,
        handler: SslErrorHandler,
        error: SslError
    ) {
        handler.cancel()
        view.stopLoading()
        onFailure(
            SteamWebPageFailure(
                kind = SteamWebFailureKind.SSL,
                failingUrl = error.url
            )
        )
    }

    override fun onReceivedHttpAuthRequest(
        view: WebView,
        handler: HttpAuthHandler,
        host: String?,
        realm: String?
    ) {
        handler.cancel()
        onFailure(
            SteamWebPageFailure(
                kind = SteamWebFailureKind.HTTP,
                failingUrl = view.url,
                statusCode = 401
            )
        )
    }

    override fun onFormResubmission(view: WebView, dontResend: Message, resend: Message) {
        dontResend.sendToTarget()
    }

    override fun onRenderProcessGone(
        view: WebView,
        detail: RenderProcessGoneDetail
    ): Boolean {
        onRendererGone(view, detail.didCrash())
        return true
    }
}

internal class SteamBrowserWebChromeClient(
    private val onProgressChanged: (Int) -> Unit,
    private val onTitleChanged: (String?) -> Unit,
    private val onFileChooser: (
        ValueCallback<Array<Uri>>,
        FileChooserParams
    ) -> Boolean,
    private val onPermissionRequest: (PermissionRequest) -> Unit,
    private val onPermissionRequestCanceled: (PermissionRequest) -> Unit,
    private val onShowCustomView: (View, CustomViewCallback) -> Unit,
    private val onHideCustomView: () -> Unit,
) : WebChromeClient() {
    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        onProgressChanged(newProgress.coerceIn(0, 100))
    }

    override fun onReceivedTitle(view: WebView?, title: String?) {
        onTitleChanged(title?.trim()?.takeIf(String::isNotBlank))
    }

    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: FileChooserParams?
    ): Boolean {
        if (filePathCallback == null || fileChooserParams == null) return false
        return onFileChooser(filePathCallback, fileChooserParams)
    }

    override fun onPermissionRequest(request: PermissionRequest) {
        onPermissionRequest(request)
    }

    override fun onPermissionRequestCanceled(request: PermissionRequest) {
        onPermissionRequestCanceled(request)
    }

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        onShowCustomView(view, callback)
    }

    override fun onHideCustomView() {
        onHideCustomView()
    }
}
