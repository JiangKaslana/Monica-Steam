package takagi.ru.monica.steam.friends.chat.info.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamChatInfoIntegrationGuardTest {
    @Test
    fun infoAndSearchRemainIndependentSafeAreaPages() {
        val info = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/info/ui/SteamChatInfoScreen.kt"
        ).readText()
        val search = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/info/ui/SteamChatHistorySearchScreen.kt"
        ).readText()

        assertTrue(info.contains("statusBarsPadding()"))
        assertTrue(info.contains("navigationBarsPadding()"))
        assertTrue(info.contains("Modifier.size(48.dp)"))
        assertTrue(info.contains("Switch("))
        assertTrue(search.contains("statusBarsPadding()"))
        assertTrue(search.contains("onOpenMessage"))
    }

    @Test
    fun directAndGroupHeadersOpenTheSharedInfoFlow() {
        val direct = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamChatThread.kt"
        ).readText()
        val group = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/groupchat/ui/SteamGroupChatThread.kt"
        ).readText()
        val host = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamChatScreen.kt"
        ).readText()

        assertTrue(direct.contains("onOpenInfo"))
        assertTrue(group.contains("onOpenInfo"))
        assertTrue(host.contains("SteamChatInfoScreen("))
        assertTrue(host.contains("SteamChatHistorySearchScreen("))
        assertTrue(host.contains("initialGroupInvitees = setOf(partnerSteamId)"))
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (directory.parentFile != null && !File(directory, "settings.gradle").exists()) {
            directory = requireNotNull(directory.parentFile)
        }
        return File(directory, path)
    }
}
