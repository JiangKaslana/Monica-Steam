package takagi.ru.monica.steam.friends.voice.background

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.friends.voice.media.SteamVoiceAudioSession
import takagi.ru.monica.steam.friends.voice.presentation.SteamVoiceCallRuntime

class SteamVoiceCallService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var publisher: SteamVoiceNotificationPublisher
    private lateinit var audioSession: SteamVoiceAudioSession
    private var stateJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        publisher = SteamVoiceNotificationPublisher(this)
        val runtime = SteamVoiceCallRuntime.get(this)
        audioSession = SteamVoiceAudioSession(
            this,
            runtime::updateAudioRoutes
        )
        runCatching(audioSession::start).onFailure { error ->
            SteamDiagLogger.append(
                "voice_audio_session failed type=${error::class.java.simpleName}"
            )
        }
        val initialState = runtime.state.value
        startForegroundCompat(publisher.notification(initialState))
        stateJob = scope.launch {
            runtime.state.collectLatest { state ->
                if (state.isActive) {
                    audioSession.applyRoute(state.requestedAudioRoute)
                    publisher.post(state)
                } else {
                    publisher.cancel()
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val runtime = SteamVoiceCallRuntime.get(this)
        when (intent?.action) {
            ACTION_TOGGLE_MIC -> runtime.toggleMicrophone()
            ACTION_TOGGLE_OUTPUT -> runtime.toggleOutput()
            ACTION_STOP -> runtime.stop()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stateJob?.cancel()
        scope.coroutineContext[Job]?.cancel()
        audioSession.stop()
        publisher.cancel()
        super.onDestroy()
    }

    private fun startForegroundCompat(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                SteamVoiceNotificationPublisher.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(SteamVoiceNotificationPublisher.NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val ACTION_ACCEPT = "takagi.ru.monica.steam.voice.ACCEPT"
        const val ACTION_REJECT = "takagi.ru.monica.steam.voice.REJECT"
        const val ACTION_TOGGLE_MIC = "takagi.ru.monica.steam.voice.TOGGLE_MIC"
        const val ACTION_TOGGLE_OUTPUT = "takagi.ru.monica.steam.voice.TOGGLE_OUTPUT"
        const val ACTION_STOP = "takagi.ru.monica.steam.voice.STOP"

        fun start(context: android.content.Context) {
            androidx.core.content.ContextCompat.startForegroundService(
                context.applicationContext,
                Intent(context.applicationContext, SteamVoiceCallService::class.java)
            )
        }

        fun stop(context: android.content.Context) {
            context.applicationContext.stopService(
                Intent(context.applicationContext, SteamVoiceCallService::class.java)
            )
        }
    }
}
