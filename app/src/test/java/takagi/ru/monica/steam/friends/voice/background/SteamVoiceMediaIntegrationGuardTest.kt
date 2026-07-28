package takagi.ru.monica.steam.friends.voice.background

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamVoiceMediaIntegrationGuardTest {
    @Test
    fun manifestDeclaresMicrophonePlaybackServiceAndNotificationReceiver() {
        val manifest = projectFile("app/src/main/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("android.permission.RECORD_AUDIO"))
        assertTrue(manifest.contains("android.permission.MODIFY_AUDIO_SETTINGS"))
        assertTrue(manifest.contains("android.permission.FOREGROUND_SERVICE_MICROPHONE"))
        assertTrue(manifest.contains("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"))
        assertTrue(manifest.contains(".steam.friends.voice.background.SteamVoiceCallService"))
        assertTrue(manifest.contains("android:foregroundServiceType=\"microphone|mediaPlayback\""))
        assertTrue(manifest.contains(".steam.friends.voice.background.SteamVoiceActionReceiver"))
    }

    @Test
    fun mediaUsesPlatformWebRtcWithoutAddingTheLargeNativeWebRtcDependency() {
        val engine = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/voice/media/SteamVoiceWebViewEngine.kt"
        ).readText()
        val dependencies = projectFile("app/build.gradle").readText()

        assertTrue(engine.contains("navigator.mediaDevices.getUserMedia"))
        assertTrue(engine.contains("new RTCPeerConnection"))
        assertTrue(engine.contains("RESOURCE_AUDIO_CAPTURE"))
        assertTrue(engine.contains("onRenderProcessGone"))
        assertFalse(dependencies.contains("webrtc-sdk"))
        assertFalse(dependencies.contains("org.webrtc"))
    }

    @Test
    fun incomingCallsUseHighPriorityNotificationActionsWithoutStartingTheMicService() {
        val publisher = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/voice/background/SteamVoiceNotificationPublisher.kt"
        ).readText()
        val runtime = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/voice/presentation/SteamVoiceCallRuntime.kt"
        ).readText()

        assertTrue(publisher.contains("IMPORTANCE_HIGH"))
        assertTrue(publisher.contains("PendingIntent.getBroadcast"))
        assertTrue(runtime.contains("notificationPublisher.post(_state.value)"))
        assertTrue(runtime.indexOf("gateway.answerDirectVoice") < runtime.indexOf("initialVoiceChatId = request.voiceChatId"))
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = requireNotNull(directory.parentFile).canonicalFile
        }
        return File(directory, path)
    }
}
