package takagi.ru.monica.steam.friends.chat.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamChatIntegrationGuardTest {
    @Test
    fun chatIsAnIndependentCapsuleMenuPageWithFriendEntry() {
        val activity = projectFile(
            "app/src/main/java/takagi/ru/monica/MonicaSteamActivity.kt"
        ).readText()
        val dock = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/navigation/SteamDockSettings.kt"
        ).readText()
        val tokenScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/ui/SteamScreen.kt"
        ).readText()
        val friendsScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/ui/SteamFriendsScreen.kt"
        ).readText()
        val friendDetail = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/ui/SteamFriendDetailScreen.kt"
        ).readText()
        val chatScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamChatScreen.kt"
        ).readText()
        assertTrue(activity.contains("MonicaSteamPage.CHAT"))
        assertTrue(activity.contains("standalone = true"))
        assertTrue(dock.contains("CHAT"))
        assertTrue(tokenScreen.contains("onOpenChat"))
        assertTrue(friendsScreen.contains("onStartChat"))
        assertTrue(friendDetail.contains("steam_chat_send_message"))
        assertTrue(chatScreen.contains("ExpressiveTopBar("))
        assertTrue(chatScreen.contains("Scaffold("))
        assertTrue(chatScreen.contains("SteamChatFriendPicker("))
        assertTrue(chatScreen.contains("BackHandler"))
        assertTrue(chatScreen.contains("easyNotesScreenEnter()"))
    }

    @Test
    fun chatKeepsDataPresentationAndTelegramStyleUiSeparated() {
        val root = projectFile("app/src/main/java/takagi/ru/monica/steam/friends/chat")
        assertTrue(root.resolve("domain").isDirectory)
        assertTrue(root.resolve("data").isDirectory)
        assertTrue(root.resolve("presentation").isDirectory)
        assertTrue(root.resolve("ui").isDirectory)
        assertTrue(root.listFiles().orEmpty().none { it.extension == "kt" })

        val uiFiles = root.resolve("ui").listFiles().orEmpty().filter { it.extension == "kt" }
        assertTrue(uiFiles.size >= 5)
        uiFiles.forEach { file ->
            assertTrue("${file.name} is too large", file.readLines().size <= 400)
        }
        root.resolve("presentation").listFiles().orEmpty()
            .filter { it.extension == "kt" }
            .forEach { file ->
                assertTrue("${file.name} is too large", file.readLines().size <= 450)
            }

        val thread = root.resolve("ui/SteamChatThread.kt").readText()
        val bubble = root.resolve("ui/SteamChatMessageBubble.kt").readText()
        val composer = root.resolve("ui/SteamChatComposer.kt").readText()
        assertTrue(thread.contains("animateScrollToItem"))
        assertTrue(thread.contains("animateItem()"))
        assertTrue(thread.contains("statusBarsPadding()"))
        assertTrue(thread.contains("Column(modifier = modifier.fillMaxSize().imePadding())"))
        assertTrue(bubble.contains("SteamChatDeliveryState.FAILED"))
        assertTrue(bubble.contains("RoundedCornerShape"))
        assertTrue(bubble.contains("MessageReactionStrip("))
        assertTrue(bubble.contains("SteamChatRemoteImage("))
        assertTrue(bubble.contains("reaction.count.toString()"))
        assertFalse(composer.contains("imePadding()"))
        assertTrue(composer.contains("heightIn(min = 52.dp"))
    }

    @Test
    fun openingAThreadCanHideRootSteamChrome() {
        val chatScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamChatScreen.kt"
        ).readText()
        val threadLifecycle = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamChatThreadLifecycle.kt"
        ).readText()
        val steamScreen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/token/ui/SteamScreen.kt"
        ).readText()
        val activity = projectFile(
            "app/src/main/java/takagi/ru/monica/MonicaSteamActivity.kt"
        ).readText()

        assertTrue(chatScreen.contains("onThreadVisibilityChange"))
        assertTrue(chatScreen.contains("SteamChatThreadLifecycle("))
        assertTrue(threadLifecycle.contains("DisposableEffect(Unit)"))
        assertTrue(activity.contains("onThreadVisibilityChange"))
        assertTrue(activity.contains("MonicaSteamPage.CHAT"))
        assertTrue(activity.contains("isSteamChatThreadOpen"))
        assertTrue(activity.contains("!isSteamChatThreadOpen"))
    }

    @Test
    fun richMediaUsesOwnedOfficialCatalogAndSeparateSteamTabs() {
        val catalog = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/richmedia/data/SteamChatCatalogService.kt"
        ).readText()
        val picker = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/richmedia/ui/SteamChatRichMediaPicker.kt"
        ).readText()
        val pickerControls = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/richmedia/ui/SteamChatRichMediaPickerControls.kt"
        ).readText()
        assertFalse(catalog.contains("QueryRewardItems"))
        assertTrue(catalog.contains("STICKERS_FIELD"))
        assertTrue(catalog.contains("EFFECTS_FIELD"))
        assertTrue(picker.contains("RichPickerPage.EMOTICON"))
        assertTrue(picker.contains("RichPickerPage.STICKER"))
        assertTrue(picker.contains("RichPickerPage.EFFECT"))
        val remoteImage = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/richmedia/ui/SteamChatRemoteImage.kt"
        ).readText()
        assertTrue(remoteImage.contains("Animatable"))
        assertTrue(remoteImage.contains("APNGDrawable"))
        assertTrue(remoteImage.contains("APNGDrawable.fromFile"))
        assertTrue(remoteImage.contains("SteamPixelAnimatedDrawable"))
        assertTrue(remoteImage.contains("SteamAnimatedImageView"))
        assertTrue(remoteImage.contains("isAnimatedPng"))
        assertTrue(remoteImage.contains("onDispose"))
        assertTrue(remoteImage.contains("stopSteamAnimation"))
        assertTrue(remoteImage.contains("staticSteamImageFilterQuality"))
        assertTrue(remoteImage.contains("setAutoPlay(true)"))
        assertTrue(remoteImage.contains("startSteamAnimation"))
        assertTrue(picker.contains("catalogFailure"))
        assertFalse(picker.contains("ModalBottomSheet"))
        assertFalse(picker.contains("SingleChoiceSegmentedButtonRow"))
        assertTrue(picker.contains("SteamChatRichMediaPickerPanel"))
        assertTrue(pickerControls.contains("SplitButtonLayout"))
        val composer = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/ui/SteamChatComposer.kt"
        ).readText()
        assertTrue(composer.contains("SteamChatRichMediaPickerPanel"))
        assertTrue(composer.contains("BackHandler(enabled = showRichPicker)"))
        assertTrue(composer.contains("onFocusChanged"))
        assertTrue(
            composer.indexOf("SteamChatRichMediaPickerPanel(") >
                composer.indexOf("FilledTonalIconButton(")
        )
        val richContent = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/richmedia/ui/SteamChatRichMessageContent.kt"
        ).readText()
        val attachmentContent = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/richmedia/ui/SteamChatAttachmentContent.kt"
        ).readText()
        assertTrue(richContent.contains("OfficialMessage"))
        assertTrue(attachmentContent.contains("steam_chat_spoiler_reveal"))
        assertTrue(attachmentContent.contains("Crossfade("))
        assertTrue(attachmentContent.contains("SteamFullscreenImageViewer("))
    }

    @Test
    fun cacheUsesMonicaEncryptionAndAccountThreadIsolation() {
        val cache = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/data/SteamChatCache.kt"
        ).readText()
        val viewModel = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/presentation/SteamChatViewModel.kt"
        ).readText()
        val guard = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/presentation/SteamChatRequestGuard.kt"
        ).readText()

        assertTrue(cache.contains("encryptDataLegacyCompat"))
        assertTrue(cache.contains("thread|\$accountSteamId|\$partnerSteamId"))
        assertTrue(viewModel.contains("SteamChatRequestGuard"))
        assertTrue(guard.contains("accountSteamId == account.steamId"))
    }

    @Test
    fun privateChatUsesTheSharedSourceAwareSessionBoundary() {
        val factory = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/presentation/SteamChatViewModelFactory.kt"
        ).readText()
        val realtime = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/data/SteamFriendChatRealtimeService.kt"
        ).readText()
        val actions = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/actions/presentation/SteamChatMessageActionViewModel.kt"
        ).readText()
        val richMedia = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/friends/chat/richmedia/presentation/SteamChatRichMediaViewModel.kt"
        ).readText()
        val chatRoot = projectFile("app/src/main/java/takagi/ru/monica/steam/friends/chat")

        assertTrue(factory.contains("sessionResolver"))
        assertTrue(realtime.contains("sessionResolver.resolveOrKeep"))
        assertTrue(actions.contains("sessionResolver.resolveOrKeep"))
        assertTrue(richMedia.contains("sessionResolver.resolveOrKeep"))
        assertFalse(factory.contains("SteamChatSessionStore"))
        assertFalse(chatRoot.resolve("data/SteamChatSessionStore.kt").exists())

        chatRoot.resolve("presentation").listFiles().orEmpty()
            .filter { it.extension == "kt" }
            .forEach { file ->
                assertFalse(
                    "${file.name} must not own a feature-local refresh service",
                    file.readText().contains("SteamSessionRefreshService")
                )
            }
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = directory.parentFile!!
        }
        return File(directory, path)
    }
}
