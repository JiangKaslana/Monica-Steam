package takagi.ru.monica.steam.friends.groupchat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.network.SteamProtoWriter

class SteamGroupChatParserTest {
    @Test
    fun parsesGroupRoomsAndUnreadStateFromOfficialChatRoomSchema() {
        val room = SteamProtoWriter().apply {
            writeUint64(1, "9001")
            writeString(2, "General")
            writeVarint(5, 200L)
            writeVarint(6, 1L)
            writeString(7, "Hello group")
            writeVarint(8, 39_734_274L)
        }
        val roomUserState = SteamProtoWriter().apply {
            writeUint64(1, "9001")
            writeVarint(3, 150L)
        }
        val userState = SteamProtoWriter().apply { writeMessage(3, roomUserState) }
        val summary = SteamProtoWriter().apply {
            writeUint64(1, "8001")
            writeString(2, "Monica testers")
            writeVarint(3, 12L)
            writeUint64(5, "9001")
            writeMessage(6, room)
            writeString(8, "Play together")
            writeVarint(10, 39_734_274L)
            writeVarint(10, 39_734_275L)
            writeVarint(12, 50L)
        }
        val pair = SteamProtoWriter().apply {
            writeMessage(1, userState)
            writeMessage(2, summary)
        }
        val response = SteamProtoWriter().apply { writeMessage(1, pair) }.toByteArray()

        val group = SteamGroupChatParser.parseGroups(response).single()

        assertEquals("8001", group.groupId)
        assertEquals("Monica testers", group.name)
        assertEquals(12, group.activeMemberCount)
        assertEquals(1, group.unreadCount)
        assertEquals(
            listOf("76561198000000002", "76561198000000003"),
            group.topMemberSteamIds
        )
        assertTrue(group.rooms.single().unread)
        assertEquals("76561198000000002", group.rooms.single().lastSenderSteamId)
    }

    @Test
    fun parsesMessagesDeletedStateAndServerEvents() {
        val normal = SteamProtoWriter().apply {
            writeVarint(1, 39_734_274L)
            writeVarint(2, 300L)
            writeString(3, "Hello")
            writeVarint(4, 2L)
        }
        val event = SteamProtoWriter().apply {
            writeVarint(1, 2L)
            writeString(2, "A member joined")
        }
        val system = SteamProtoWriter().apply {
            writeVarint(2, 301L)
            writeVarint(4, 3L)
            writeMessage(5, event)
            writeBool(6, false)
        }
        val response = SteamProtoWriter().apply {
            writeMessage(1, normal)
            writeMessage(1, system)
            writeBool(4, true)
        }.toByteArray()

        val page = SteamGroupChatParser.parseHistory(response, "8001", "9001")

        assertEquals(listOf("Hello", "A member joined"), page.messages.map { it.body })
        assertEquals(2, page.messages.last().serverEventType)
        assertFalse(page.messages.last().deleted)
        assertTrue(page.moreAvailable)
    }

    @Test
    fun parsesCreateAndSendResponses() {
        val created = SteamProtoWriter().apply { writeUint64(1, "18446744073709551610") }.toByteArray()
        val sent = SteamProtoWriter().apply {
            writeString(1, "message")
            writeVarint(2, 500L)
            writeVarint(3, 9L)
        }.toByteArray()

        assertEquals("18446744073709551610", SteamGroupChatParser.parseCreatedGroupId(created))
        assertEquals(
            9,
            SteamGroupChatParser.parseSentMessage(sent, "8", "9", "76561198000000001", "message").ordinal
        )
    }

    @Test
    fun parsesOfficialGroupAvatarSha() {
        val sha = ByteArray(20) { it.toByte() }
        val room = SteamProtoWriter().apply { writeUint64(1, "9001") }
        val summary = SteamProtoWriter().apply {
            writeUint64(1, "8001")
            writeString(2, "Avatar group")
            writeUint64(5, "9001")
            writeMessage(6, room)
            writeBytes(11, sha)
        }
        val pair = SteamProtoWriter().apply { writeMessage(2, summary) }
        val response = SteamProtoWriter().apply { writeMessage(1, pair) }.toByteArray()

        assertEquals(
            "https://avatars.steamstatic.com/000102030405060708090a0b0c0d0e0f10111213_full.jpg",
            SteamGroupChatParser.parseGroups(response).single().avatarUrl
        )
    }
}
