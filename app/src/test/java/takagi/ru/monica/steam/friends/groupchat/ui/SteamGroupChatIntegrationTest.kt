package takagi.ru.monica.steam.friends.groupchat.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamGroupChatIntegrationTest {
    @Test
    fun groupChatIsAnIndependentSteamBackedModule() {
        val root = projectFile("app/src/main/java/takagi/ru/monica/steam/friends/groupchat")
        assertTrue(root.resolve("domain").isDirectory)
        assertTrue(root.resolve("data").isDirectory)
        assertTrue(root.resolve("presentation").isDirectory)
        assertTrue(root.resolve("ui").isDirectory)

        val service = root.resolve("data/SteamGroupChatService.kt").readText()
        val attachmentTargets = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/richmedia/data/SteamChatAttachmentTargetFields.kt"
        ).readText()
        assertTrue(service.contains("ChatRoom.\$method#1"))
        assertTrue(service.contains("CreateChatRoomGroup"))
        assertTrue(service.contains("GetMyChatRoomGroups"))
        assertTrue(service.contains("GetMessageHistory"))
        assertTrue(service.contains("SendChatMessage"))
        assertTrue(service.contains("InviteFriendToChatRoomGroup"))
        assertTrue(attachmentTargets.contains("chat_group_id"))
        assertTrue(attachmentTargets.contains("chat_id"))
    }

    @Test
    fun chatPageExposesGroupListCreateInviteAndFullScreenThread() {
        val chatScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamChatScreen.kt"
        ).readText()
        val threadLifecycle = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamChatThreadLifecycle.kt"
        ).readText()
        val groupList = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/groupchat/ui/SteamGroupChatList.kt"
        ).readText()
        val thread = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/groupchat/ui/SteamGroupChatThread.kt"
        ).readText()
        val dialogs = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/groupchat/ui/SteamGroupChatDialogs.kt"
        ).readText()

        assertTrue(chatScreen.contains("SteamGroupChatViewModel"))
        assertTrue(chatScreen.contains("SteamGroupChatList("))
        assertTrue(chatScreen.contains("SteamGroupChatThreadHost("))
        assertTrue(chatScreen.contains("SteamGroupChatDialogsHost("))
        assertTrue(chatScreen.contains("groupChatState.selectedChatId != null"))
        assertTrue(chatScreen.contains("SteamChatThreadLifecycle("))
        assertTrue(threadLifecycle.contains("richMediaViewModel.selectGroupRoom"))
        assertTrue(threadLifecycle.contains("groupChatViewModel.refreshThread()"))
        assertTrue(groupList.contains("SteamExpressivePullToRefresh"))
        assertTrue(groupList.contains("ExtendedFloatingActionButton"))
        assertTrue(thread.contains("SteamGroupChatThread("))
        assertTrue(thread.contains("SteamChatRichMessageContent"))
        assertTrue(thread.contains("SteamChatComposer("))
        assertTrue(thread.contains("onUploadAttachment"))
        assertTrue(thread.contains("statusBarsPadding()"))
        assertTrue(dialogs.contains("SteamCreateGroupDialog("))
        assertTrue(dialogs.contains("SteamInviteFriendDialog("))
        assertTrue(dialogs.contains("FriendSelectionList"))
    }

    @Test
    fun inviteDialogPinsActionsInsideSafeScreenBounds() {
        val dialogs = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/groupchat/ui/SteamGroupChatDialogs.kt"
        ).readText()

        assertTrue(dialogs.contains("usePlatformDefaultWidth = false"))
        assertTrue(dialogs.contains("windowInsetsPadding(WindowInsets.safeDrawing)"))
        assertTrue(dialogs.contains("Modifier.weight(1f)"))
    }

    @Test
    fun groupThreadResizesForImeWithoutPanningTheHeader() {
        val thread = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/groupchat/ui/SteamGroupChatThread.kt"
        ).readText()
        val manifest = projectFile("app/src/main/AndroidManifest.xml").readText()

        assertTrue(thread.contains("imePadding()"))
        assertTrue(manifest.contains("android:windowSoftInputMode=\"adjustResize\""))
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (directory.parentFile != null && !File(directory, "settings.gradle").exists()) {
            directory = requireNotNull(directory.parentFile)
        }
        return File(directory, path)
    }
}
