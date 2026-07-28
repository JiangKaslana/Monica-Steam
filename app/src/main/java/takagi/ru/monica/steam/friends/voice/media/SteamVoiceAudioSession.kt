package takagi.ru.monica.steam.friends.voice.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

/** Keeps Android audio routing and focus in voice-communication mode for the call lifetime. */
internal class SteamVoiceAudioSession(context: Context) {
    private val audioManager = context.applicationContext
        .getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var previousMode: Int? = null
    private var focusRequest: AudioFocusRequest? = null
    private var started = false

    fun start() {
        if (started) return
        started = true
        previousMode = audioManager.mode
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener { }
                .build()
            focusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
        }
    }

    fun stop() {
        if (!started) return
        started = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let(audioManager::abandonAudioFocusRequest)
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
        previousMode?.let { mode -> audioManager.mode = mode }
        previousMode = null
    }
}
