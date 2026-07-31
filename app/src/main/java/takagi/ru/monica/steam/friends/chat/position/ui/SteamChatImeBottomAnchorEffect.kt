package takagi.ru.monica.steam.friends.chat.position.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import takagi.ru.monica.steam.friends.chat.position.domain.SteamChatImeAnchorState
import takagi.ru.monica.steam.friends.chat.position.domain.reduceSteamChatImeAnchor

@Composable
internal fun SteamChatImeBottomAnchorEffect(
    conversationKey: String,
    messageCount: Int,
    leadingItemCount: Int,
    messagesBelow: Int,
    restored: Boolean,
    listState: LazyListState
) {
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val latestMessagesBelow by rememberUpdatedState(messagesBelow)
    val latestMessageCount by rememberUpdatedState(messageCount)
    val latestLeadingItemCount by rememberUpdatedState(leadingItemCount)
    var anchorState by remember(conversationKey) {
        mutableStateOf(
            SteamChatImeAnchorState(
                imeVisible = imeVisible,
                wasAtBottomBeforeIme = messagesBelow == 0,
                restored = restored
            )
        )
    }
    LaunchedEffect(
        conversationKey,
        imeVisible,
        restored
    ) {
        val result = reduceSteamChatImeAnchor(
            previous = anchorState,
            imeVisible = imeVisible,
            atBottom = latestMessagesBelow == 0,
            restored = restored,
            hasMessages = latestMessageCount > 0
        )
        anchorState = result.state
        if (result.shouldScrollToLatest) {
            withFrameNanos { }
            listState.animateToLatestSteamChatMessage(
                latestMessageCount,
                latestLeadingItemCount
            )
        }
    }
    LaunchedEffect(conversationKey, imeVisible, messagesBelow, restored) {
        if (!imeVisible) {
            anchorState = reduceSteamChatImeAnchor(
                previous = anchorState,
                imeVisible = false,
                atBottom = messagesBelow == 0,
                restored = restored,
                hasMessages = messageCount > 0
            ).state
        }
    }
}
