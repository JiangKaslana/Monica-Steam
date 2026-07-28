package takagi.ru.monica.steam.friends.voice.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import takagi.ru.monica.steam.friends.voice.presentation.SteamVoiceCallRuntime

/** Notification controls remain available even when the chat activity is not visible. */
class SteamVoiceActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val runtime = SteamVoiceCallRuntime.get(context)
        when (intent?.action) {
            SteamVoiceCallService.ACTION_ACCEPT -> runtime.acceptIncomingFromNotification()
            SteamVoiceCallService.ACTION_REJECT -> runtime.rejectIncomingFromNotification()
            SteamVoiceCallService.ACTION_TOGGLE_MIC -> runtime.toggleMicrophone()
            SteamVoiceCallService.ACTION_TOGGLE_OUTPUT -> runtime.toggleOutput()
            SteamVoiceCallService.ACTION_STOP -> runtime.stop()
        }
    }
}
