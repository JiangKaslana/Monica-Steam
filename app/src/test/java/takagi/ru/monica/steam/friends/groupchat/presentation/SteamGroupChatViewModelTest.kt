package takagi.ru.monica.steam.friends.groupchat.presentation

import java.net.SocketTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.friends.groupchat.data.SteamGroupChatCache
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatCreateRequest
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatDeliveryState
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatGateway
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatGroupsSnapshot
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatHistoryBoundary
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatMessage
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatMessagePage
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatRoom
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatSummary
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatThreadSnapshot

@OptIn(ExperimentalCoroutinesApi::class)
class SteamGroupChatViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun teardown() = Dispatchers.resetMain()

    @Test
    fun timeoutWithServerEchoKeepsOneConfirmedMessage() = runTest(dispatcher.scheduler) {
        val gateway = FakeGateway().apply {
            send = { _, _, _ -> throw SocketTimeoutException("lost response") }
            history = { groupId, chatId -> SteamGroupChatMessagePage(listOf(
                SteamGroupChatMessage(groupId, chatId, ACCOUNT_ID, 100L, 7, "hello")
            ), false) }
        }
        val viewModel = viewModel(gateway)
        viewModel.selectAccount(account())
        runCurrent()
        viewModel.openRoom("8", "9")
        runCurrent()

        viewModel.sendMessage("hello")
        runCurrent()

        val messages = viewModel.state.value.thread?.messages.orEmpty()
        assertEquals(1, messages.size)
        assertEquals(SteamGroupChatDeliveryState.SENT, messages.single().deliveryState)
        assertEquals("client-1", messages.single().clientMessageId)
    }

    @Test
    fun createGroupRefreshesOfficialGroupList() = runTest(dispatcher.scheduler) {
        val gateway = FakeGateway().apply {
            createdGroupId = "88"
            groups = listOf(group("88", "New group"))
        }
        val viewModel = viewModel(gateway)
        viewModel.selectAccount(account())
        runCurrent()

        viewModel.createGroup("New group", listOf(PARTNER_ID))
        runCurrent()

        assertEquals("88", viewModel.state.value.createdGroupId)
        assertEquals("New group", viewModel.state.value.groups.single().name)
        assertEquals(listOf(PARTNER_ID), gateway.lastCreate?.inviteeSteamIds)
    }

    private fun viewModel(gateway: FakeGateway) = SteamGroupChatViewModel(
        gateway = gateway,
        cache = MemoryCache(),
        ioDispatcher = dispatcher,
        nowMillis = { 100_000L },
        newClientId = { "client-1" }
    )

    private fun group(id: String, name: String) = SteamGroupChatSummary(
        groupId = id,
        name = name,
        defaultChatId = "9",
        rooms = listOf(SteamGroupChatRoom("9", "General"))
    )

    private fun account() = SteamAccount(
        id = 1L, steamId = ACCOUNT_ID, accountName = "account", displayName = "Account",
        deviceId = "device", sharedSecret = "secret", identitySecret = null,
        revocationCode = null, tokenGid = null, accessToken = "token", refreshToken = null,
        steamLoginSecure = null, rawSteamGuardJson = "{}", selected = true,
        sortOrder = 1, createdAt = 0L, updatedAt = 0L
    )

    private class FakeGateway : SteamGroupChatGateway {
        var groups: List<SteamGroupChatSummary> = listOf(
            SteamGroupChatSummary("8", "Group", defaultChatId = "9", rooms = listOf(SteamGroupChatRoom("9", "General")))
        )
        var history: (String, String) -> SteamGroupChatMessagePage = { _, _ -> SteamGroupChatMessagePage(emptyList(), false) }
        var send: (String, String, String) -> SteamGroupChatMessage = { groupId, chatId, body ->
            SteamGroupChatMessage(groupId, chatId, ACCOUNT_ID, 101L, 1, body)
        }
        var createdGroupId = "8"
        var lastCreate: SteamGroupChatCreateRequest? = null
        override fun getMyGroups(account: SteamAccount) = groups
        override fun getHistory(account: SteamAccount, groupId: String, chatId: String, before: SteamGroupChatHistoryBoundary?) = history(groupId, chatId)
        override fun sendMessage(account: SteamAccount, groupId: String, chatId: String, body: String) = send(groupId, chatId, body)
        override fun createGroup(account: SteamAccount, request: SteamGroupChatCreateRequest): String {
            lastCreate = request
            return createdGroupId
        }
        override fun inviteFriend(account: SteamAccount, groupId: String, chatId: String, steamId: String) = Unit
        override fun acknowledge(account: SteamAccount, groupId: String, chatId: String, timestamp: Long) = Unit
    }

    private class MemoryCache : SteamGroupChatCache {
        var groups: SteamGroupChatGroupsSnapshot? = null
        var thread: SteamGroupChatThreadSnapshot? = null
        override fun loadGroups(accountSteamId: String) = groups
        override fun saveGroups(snapshot: SteamGroupChatGroupsSnapshot) { groups = snapshot }
        override fun loadThread(accountSteamId: String, groupId: String, chatId: String) = thread
        override fun saveThread(snapshot: SteamGroupChatThreadSnapshot) { thread = snapshot }
    }

    private companion object {
        const val ACCOUNT_ID = "76561198000000001"
        const val PARTNER_ID = "76561198000000002"
    }
}
