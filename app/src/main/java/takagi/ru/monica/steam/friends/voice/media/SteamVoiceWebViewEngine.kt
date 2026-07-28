package takagi.ru.monica.steam.friends.voice.media

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.webkit.ConsoleMessage
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import org.json.JSONObject
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger

internal interface SteamVoiceWebViewCallbacks {
    fun onLocalOffer(descriptionJson: String)
    fun onLocalAnswer(descriptionJson: String)
    fun onIceStateChanged(state: String)
    fun onFailure(message: String)
}

/**
 * Uses Android WebView's platform WebRTC implementation as a small, hidden
 * media surface. This keeps the APK free of a 40+ MB native libwebrtc AAR
 * while retaining the same WebRTC signaling Steam's official client uses.
 */
internal class SteamVoiceWebViewEngine(
    context: Context,
    private val callbacks: SteamVoiceWebViewCallbacks
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private var started = false

    @SuppressLint("SetJavaScriptEnabled")
    fun start() {
        if (started) return
        started = true
        mainHandler.post {
            if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                started = false
                callbacks.onFailure("Microphone permission is required for Steam voice chat")
                return@post
            }
            val view = runCatching { WebView(appContext).apply {
                setBackgroundColor(Color.TRANSPARENT)
                alpha = 0f
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.mediaPlaybackRequiresUserGesture = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.setSupportZoom(false)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
                }
                addJavascriptInterface(Bridge(), BRIDGE_NAME)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean = true

                    override fun onRenderProcessGone(
                        view: WebView?,
                        detail: RenderProcessGoneDetail?
                    ): Boolean {
                        webView = null
                        started = false
                        runCatching { view?.destroy() }
                        callbacks.onFailure(
                            if (detail?.didCrash() == true) {
                                "Android WebView voice renderer crashed"
                            } else {
                                "Android stopped the WebView voice renderer"
                            }
                        )
                        return true
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onPermissionRequest(request: PermissionRequest) {
                        mainHandler.post {
                            val origin = request.origin?.host.orEmpty()
                            if (origin == "steamcommunity.com" &&
                                ContextCompat.checkSelfPermission(
                                    appContext,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                            } else {
                                request.deny()
                            }
                        }
                    }

                    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                        if (consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                            SteamDiagLogger.append(
                                "voice_webview_console ${consoleMessage.message().take(180)}"
                            )
                        }
                        return true
                    }
                }
                WebView.setWebContentsDebuggingEnabled(false)
                loadDataWithBaseURL(
                    BASE_URL,
                    HTML,
                    "text/html",
                    "UTF-8",
                    null
                )
            } }.getOrElse { error ->
                started = false
                callbacks.onFailure(
                    error.message?.takeIf(String::isNotBlank)
                        ?: "Android WebView could not start Steam voice chat"
                )
                return@post
            }
            webView = view
        }
    }

    fun setRemoteDescription(descriptionJson: String) = evaluate(
        "window.monicaVoice && window.monicaVoice.setRemoteDescription(${JSONObject.quote(descriptionJson)})"
    )

    fun setMicrophoneMuted(muted: Boolean) = evaluate(
        "window.monicaVoice && window.monicaVoice.setMicrophoneMuted(${muted})"
    )

    fun setOutputMuted(muted: Boolean) = evaluate(
        "window.monicaVoice && window.monicaVoice.setOutputMuted(${muted})"
    )

    fun stop() {
        started = false
        mainHandler.post {
            webView?.let { view ->
                runCatching {
                    view.evaluateJavascript("window.monicaVoice && window.monicaVoice.stop()", null)
                    view.removeJavascriptInterface(BRIDGE_NAME)
                    view.stopLoading()
                    view.destroy()
                }
            }
            webView = null
        }
    }

    private fun evaluate(script: String) {
        mainHandler.post { webView?.evaluateJavascript(script, null) }
    }

    private inner class Bridge {
        @android.webkit.JavascriptInterface
        fun onLocalOffer(descriptionJson: String) = callbacks.onLocalOffer(descriptionJson)

        @android.webkit.JavascriptInterface
        fun onLocalAnswer(descriptionJson: String) = callbacks.onLocalAnswer(descriptionJson)

        @android.webkit.JavascriptInterface
        fun onIceStateChanged(state: String) = callbacks.onIceStateChanged(state)

        @android.webkit.JavascriptInterface
        fun onFailure(message: String) = callbacks.onFailure(message)
    }

    private companion object {
        const val BRIDGE_NAME = "MonicaVoiceBridge"
        const val BASE_URL = "https://steamcommunity.com/chat/voice/"
        val HTML = """
            <!doctype html><meta name="viewport" content="width=device-width">
            <script>
            (() => {
              let pc = null;
              let stream = null;
              const bridge = () => window.MonicaVoiceBridge;
              const fail = (e) => bridge().onFailure(String(e && e.message || e));
              const reportIce = () => {
                if (pc) bridge().onIceStateChanged(pc.iceConnectionState || "new");
              };
              window.monicaVoice = {
                async start() {
                  try {
                    stream = await navigator.mediaDevices.getUserMedia({
                      audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true },
                      video: false
                    });
                    pc = new RTCPeerConnection({ sdpSemantics: "plan-b" });
                    stream.getTracks().forEach(t => pc.addTrack(t, stream));
                    pc.oniceconnectionstatechange = reportIce;
                    pc.onconnectionstatechange = reportIce;
                    pc.ontrack = (event) => {
                      try {
                        const remote = document.createElement("audio");
                        remote.autoplay = true;
                        remote.controls = false;
                        remote.srcObject = event.streams && event.streams[0]
                          ? event.streams[0] : new MediaStream([event.track]);
                        remote.dataset.monicaRemote = "1";
                        document.body.appendChild(remote);
                      } catch (e) { fail(e); }
                    };
                    const offer = await pc.createOffer({
                      offerToReceiveAudio: true,
                      voiceActivityDetection: true
                    });
                    await pc.setLocalDescription(offer);
                    bridge().onLocalOffer(JSON.stringify(pc.localDescription));
                  } catch (e) { fail(e); }
                },
                async setRemoteDescription(raw) {
                  try {
                    if (!pc) return;
                    const description = typeof raw === "string" ? JSON.parse(raw) : raw;
                    await pc.setRemoteDescription(description);
                    if (description.type === "offer") {
                      const answer = await pc.createAnswer();
                      await pc.setLocalDescription(answer);
                      bridge().onLocalAnswer(JSON.stringify(pc.localDescription));
                    }
                  } catch (e) { fail(e); }
                },
                setMicrophoneMuted(muted) {
                  if (stream) stream.getAudioTracks().forEach(t => t.enabled = !muted);
                },
                setOutputMuted(muted) {
                  document.querySelectorAll("audio[data-monica-remote='1']").forEach(a => a.muted = !!muted);
                },
                stop() {
                  if (pc) { try { pc.close(); } catch (e) {} }
                  if (stream) stream.getTracks().forEach(t => t.stop());
                  document.querySelectorAll("audio[data-monica-remote='1']").forEach(a => {
                    try { a.pause(); a.srcObject = null; } catch (e) {}
                    a.remove();
                  });
                  pc = null; stream = null;
                }
              };
              window.addEventListener("load", () => window.monicaVoice.start());
            })();
            </script>
        """.trimIndent()
    }
}
