package takagi.ru.monica.steam.friends.groupchat.presentation

import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatRealtimeEvent
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatRealtimeGateway
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

    @Test
    fun realtimeMessageEntersTheOpenRoomWithoutPolling() = runTest(dispatcher.scheduler) {
        val gateway = FakeGateway()
        val realtime = FakeRealtime()
        val viewModel = attachedViewModel(gateway, realtime)
        try {
            viewModel.openRoom("8", "9")
            runCurrent()
            val historyCallsBeforeEvent = gateway.historyCalls

            realtime.emit(ACCOUNT_ID, message(PARTNER_ID, "ping", timestamp = 120L, ordinal = 3))
            runCurrent()

            assertEquals(listOf("ping"), viewModel.state.value.thread?.messages?.map { it.body })
            assertEquals(historyCallsBeforeEvent, gateway.historyCalls)
        } finally {
            disposeForeground(viewModel)
        }
    }

    @Test
    fun repeatedBackgroundMessageMarksTheRoomUnreadOnce() = runTest(dispatcher.scheduler) {
        val realtime = FakeRealtime()
        val viewModel = attachedViewModel(FakeGateway(), realtime)
        try {
            realtime.emit(ACCOUNT_ID, message(PARTNER_ID, "hey", timestamp = 120L, ordinal = 3))
            runCurrent()
            realtime.emit(ACCOUNT_ID, message(PARTNER_ID, "hey", timestamp = 120L, ordinal = 3))
            runCurrent()

            val summary = viewModel.state.value.groups.single()
            assertEquals(1, summary.unreadCount)
            assertTrue(summary.rooms.single().unread)
            assertEquals("hey", summary.rooms.single().lastMessage)
        } finally {
            disposeForeground(viewModel)
        }
    }

    @Test
    fun ownRealtimeMessageLeavesTheBackgroundRoomRead() = runTest(dispatcher.scheduler) {
        val realtime = FakeRealtime()
        val viewModel = attachedViewModel(FakeGateway(), realtime)
        try {
            realtime.emit(ACCOUNT_ID, message(ACCOUNT_ID, "sent elsewhere", timestamp = 120L, ordinal = 3))
            runCurrent()

            val summary = viewModel.state.value.groups.single()
            assertEquals(0, summary.unreadCount)
            assertFalse(summary.rooms.single().unread)
        } finally {
            disposeForeground(viewModel)
        }
    }

    @Test
    fun eventsForThePreviousAccountCannotMutateTheActiveState() = runTest(dispatcher.scheduler) {
        val realtime = FakeRealtime()
        val viewModel = attachedViewModel(FakeGateway(), realtime)
        try {
            viewModel.selectAccount(account(id = 2L, steamId = OTHER_ACCOUNT_ID))
            runCurrent()
            realtime.emit(ACCOUNT_ID, message(PARTNER_ID, "stale", timestamp = 120L, ordinal = 3))
            runCurrent()

            assertEquals(OTHER_ACCOUNT_ID, viewModel.state.value.accountSteamId)
            assertEquals(0, viewModel.state.value.groups.single().unreadCount)
            assertEquals(listOf(ACCOUNT_ID, OTHER_ACCOUNT_ID), realtime.subscriptions)
        } finally {
            disposeForeground(viewModel)
        }
    }

    @Test
    fun openingARoomKeepsRealtimeGroupRefreshValid() = runTest(dispatcher.scheduler) {
        val gateway = FakeGateway()
        val realtime = FakeRealtime()
        val viewModel = attachedViewModel(gateway, realtime)
        try {
            viewModel.openRoom("8", "9")
            runCurrent()
            val historyCallsBeforeEvent = gateway.historyCalls
            gateway.groups = listOf(group("8", "Renamed"), group("12", "Second"))

            realtime.emit(ACCOUNT_ID, SteamGroupChatRealtimeEvent.RoomChanged("8"))
            runCurrent()

            assertEquals(listOf("Renamed", "Second"), viewModel.state.value.groups.map { it.name })
            assertEquals(historyCallsBeforeEvent + 1, gateway.historyCalls)
        } finally {
            disposeForeground(viewModel)
        }
    }

    @Test
    fun serverEchoOfAnOptimisticSendKeepsOneRow() = runTest(dispatcher.scheduler) {
        val gateway = FakeGateway()
        val realtime = FakeRealtime()
        val viewModel = attachedViewModel(gateway, realtime)
        try {
            viewModel.openRoom("8", "9")
            runCurrent()

            viewModel.sendMessage("hello")
            runCurrent()
            realtime.emit(ACCOUNT_ID, message(ACCOUNT_ID, "hello", timestamp = 101L, ordinal = 1))
            runCurrent()

            val messages = viewModel.state.value.thread?.messages.orEmpty()
            assertEquals(1, messages.size)
            assertEquals("client-1", messages.single().clientMessageId)
            assertEquals(SteamGroupChatDeliveryState.SENT, messages.single().deliveryState)
        } finally {
            disposeForeground(viewModel)
        }
    }

    @Test
    fun realtimeConnectionSlowsPollingToTheCorrectionInterval() = runTest(dispatcher.scheduler) {
        val gateway = FakeGateway()
        val realtime = FakeRealtime()
        val viewModel = attachedViewModel(gateway, realtime)
        try {
            realtime.emit(ACCOUNT_ID, SteamGroupChatRealtimeEvent.ConnectionChanged(true))
            runCurrent()
            assertTrue(viewModel.state.value.realtimeConnected)
            val groupCallsAfterConnect = gateway.groupCalls

            advanceTimeBy(15_001L)
            runCurrent()
            assertEquals(groupCallsAfterConnect, gateway.groupCalls)

            advanceTimeBy(45_000L)
            runCurrent()
            assertEquals(groupCallsAfterConnect + 1, gateway.groupCalls)
        } finally {
            disposeForeground(viewModel)
        }
    }

    @Test
    fun withoutARealtimeConnectionPollingStaysOnTheLegacyInterval() = runTest(dispatcher.scheduler) {
        val gateway = FakeGateway()
        val viewModel = attachedViewModel(gateway, FakeRealtime())
        try {
            assertFalse(viewModel.state.value.realtimeConnected)
            val groupCallsAfterAttach = gateway.groupCalls

            advanceTimeBy(15_001L)
            runCurrent()

            assertEquals(groupCallsAfterAttach + 1, gateway.groupCalls)
        } finally {
            disposeForeground(viewModel)
        }
    }

    private fun attachedViewModel(
        gateway: FakeGateway,
        realtime: FakeRealtime
    ): SteamGroupChatViewModel {
        val viewModel = viewModel(gateway, realtime)
        viewModel.selectAccount(account())
        dispatcher.scheduler.runCurrent()
        viewModel.setForeground(true)
        dispatcher.scheduler.runCurrent()
        return viewModel
    }

    private fun disposeForeground(viewModel: SteamGroupChatViewModel) {
        viewModel.setForeground(false)
        dispatcher.scheduler.runCurrent()
    }

    private fun viewModel(
        gateway: FakeGateway,
        realtime: SteamGroupChatRealtimeGateway? = null
    ) = SteamGroupChatViewModel(
        gateway = gateway,
        cache = MemoryCache(),
        ioDispatcher = dispatcher,
        nowMillis = { 100_000L },
        newClientId = { "client-1" },
        realtime = realtime
    )

    private fun message(
        senderSteamId: String,
        body: String,
        timestamp: Long,
        ordinal: Int,
        groupId: String = "8",
        chatId: String = "9"
    ) = SteamGroupChatRealtimeEvent.Message(
        SteamGroupChatMessage(groupId, chatId, senderSteamId, timestamp, ordinal, body)
    )

    private fun group(id: String, name: String) = SteamGroupChatSummary(
        groupId = id,
        name = name,
        defaultChatId = "9",
        rooms = listOf(SteamGroupChatRoom("9", "General"))
    )

    private fun account(id: Long = 1L, steamId: String = ACCOUNT_ID) = SteamAccount(
        id = id, steamId = steamId, accountName = "account", displayName = "Account",
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
        var groupCalls = 0
            private set
        var historyCalls = 0
            private set
        override fun getMyGroups(account: SteamAccount): List<SteamGroupChatSummary> {
            groupCalls++
            return groups
        }
        override fun getHistory(account: SteamAccount, groupId: String, chatId: String, before: SteamGroupChatHistoryBoundary?): SteamGroupChatMessagePage {
            historyCalls++
            return history(groupId, chatId)
        }
        override fun sendMessage(account: SteamAccount, groupId: String, chatId: String, body: String) = send(groupId, chatId, body)
        override fun createGroup(account: SteamAccount, request: SteamGroupChatCreateRequest): String {
            lastCreate = request
            return createdGroupId
        }
        override fun inviteFriend(account: SteamAccount, groupId: String, chatId: String, steamId: String) = Unit
        override fun acknowledge(account: SteamAccount, groupId: String, chatId: String, timestamp: Long) = Unit
    }

    private class FakeRealtime : SteamGroupChatRealtimeGateway {
        private val buses = ConcurrentHashMap<String, MutableSharedFlow<SteamGroupChatRealtimeEvent>>()
        val subscriptions = mutableListOf<String>()

        override fun events(account: SteamAccount): Flow<SteamGroupChatRealtimeEvent> {
            subscriptions += account.steamId
            return bus(account.steamId)
        }

        suspend fun emit(accountSteamId: String, event: SteamGroupChatRealtimeEvent) {
            bus(accountSteamId).emit(event)
        }

        private fun bus(accountSteamId: String) = buses.getOrPut(accountSteamId) {
            MutableSharedFlow(extraBufferCapacity = 16)
        }
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
        const val OTHER_ACCOUNT_ID = "76561198000000009"
        const val PARTNER_ID = "76561198000000002"
    }
}
