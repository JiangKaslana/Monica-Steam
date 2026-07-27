package takagi.ru.monica.steam.friends.chat.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import takagi.ru.monica.steam.friends.chat.domain.SteamChatSession
import takagi.ru.monica.steam.friends.domain.SteamFriend

class SteamChatFriendPickerTest {
    @Test
    fun friendsWithRecentMessagesComeBeforeFriendsWithoutMessages() {
        val friends = listOf(
            friend("older"),
            friend("never"),
            friend("newer")
        )
        val sessions = listOf(
            SteamChatSession("older", lastMessageTimestamp = 100L),
            SteamChatSession("newer", lastMessageTimestamp = 300L)
        )

        val sorted = sortSteamChatFriendsByRecentMessage(friends, sessions)

        assertEquals(listOf("newer", "older", "never"), sorted.map(SteamFriend::steamId))
    }

    private fun friend(steamId: String) = SteamFriend(
        steamId = steamId,
        personaName = steamId
    )
}
