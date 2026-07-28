package takagi.ru.monica.steam.friends.chat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
internal fun SteamChatBackHandlers(
    standalone: Boolean,
    showFriends: Boolean,
    subpage: SteamChatSubpage?,
    directThreadOpen: Boolean,
    groupThreadOpen: Boolean,
    onShowFriendsChange: (Boolean) -> Unit,
    onSubpageChange: (SteamChatSubpage?) -> Unit,
    onCloseDirectThread: () -> Unit,
    onCloseGroupThread: () -> Unit
) {
    BackHandler(enabled = subpage != null) {
        onSubpageChange(
            if (subpage == SteamChatSubpage.SEARCH || subpage == SteamChatSubpage.ADMIN) {
                SteamChatSubpage.INFO
            } else null
        )
    }
    BackHandler(enabled = subpage == null && directThreadOpen, onBack = onCloseDirectThread)
    BackHandler(enabled = subpage == null && groupThreadOpen, onBack = onCloseGroupThread)
    BackHandler(enabled = standalone && !directThreadOpen && showFriends) {
        onShowFriendsChange(false)
    }
}
