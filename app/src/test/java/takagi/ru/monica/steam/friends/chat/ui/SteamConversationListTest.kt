package takagi.ru.monica.steam.friends.chat.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.friends.chat.domain.SteamChatSession
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatRoom
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatSummary
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceCallState
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceConnectionState
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceParticipant
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceTarget
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceTargetType

class SteamConversationListTest {
    @Test
    fun directAndGroupChatsShareOneChronologicalList() {
        val entries = buildSteamConversationEntries(
            sessions = listOf(SteamChatSession("76561198000000001", lastMessageTimestamp = 100L)),
            groups = listOf(group(timestamp = 200L)),
            friends = listOf(SteamFriend("76561198000000001", personaName = "Friend")),
            query = "",
            pinnedPartnerSteamIds = emptySet(),
            pinnedGroupIds = emptySet()
        )

        assertEquals(listOf(SteamConversationType.GROUP, SteamConversationType.DIRECT), entries.map { it.type })
        assertEquals("9001", entries.first().chatId)
    }

    @Test
    fun pinnedConversationWinsOverTimestampAcrossTypes() {
        val entries = buildSteamConversationEntries(
            sessions = listOf(SteamChatSession("76561198000000001", lastMessageTimestamp = 300L)),
            groups = listOf(group(timestamp = 100L)),
            friends = emptyList(),
            query = "",
            pinnedPartnerSteamIds = emptySet(),
            pinnedGroupIds = setOf("8001")
        )

        assertEquals(SteamConversationType.GROUP, entries.first().type)
        assertTrue(entries.first().pinned)
    }

    @Test
    fun activeLocalGroupVoiceIsVisibleBeforeTheServerSummaryRefreshes() {
        val entries = buildSteamConversationEntries(
            sessions = emptyList(),
            groups = listOf(group(timestamp = 100L)),
            friends = emptyList(),
            query = "",
            pinnedPartnerSteamIds = emptySet(),
            pinnedGroupIds = emptySet(),
            voiceState = SteamVoiceCallState(
                accountSteamId = "76561198000000001",
                target = SteamVoiceTarget(
                    type = SteamVoiceTargetType.GROUP,
                    title = "Group",
                    groupId = "8001",
                    chatId = "9002"
                ),
                state = SteamVoiceConnectionState.CONNECTED,
                participants = listOf(SteamVoiceParticipant("76561198000000002"))
            )
        )

        assertTrue(entries.single().voiceActive)
        assertEquals(2, entries.single().voiceMemberCount)
    }

    private fun group(timestamp: Long) = SteamGroupChatSummary(
        groupId = "8001",
        name = "Group",
        activeMemberCount = 3,
        defaultChatId = "9001",
        rooms = listOf(
            SteamGroupChatRoom("9001", "General", lastMessageTimestamp = timestamp, lastMessage = "Hi")
        )
    )
}
