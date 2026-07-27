package takagi.ru.monica.steam.friends.groupchat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.steam.friends.chat.richmedia.presentation.SteamChatRichMediaUiState
import takagi.ru.monica.steam.friends.chat.position.domain.SteamChatReadingConversationKey
import takagi.ru.monica.steam.friends.chat.position.ui.SteamChatAutoScrollToLatestEffect
import takagi.ru.monica.steam.friends.chat.position.ui.SteamChatJumpToLatestButton
import takagi.ru.monica.steam.friends.chat.position.ui.animateToLatestSteamChatMessage
import takagi.ru.monica.steam.friends.chat.position.ui.rememberSteamChatReadingPosition
import takagi.ru.monica.steam.friends.chat.richmedia.ui.SteamChatRichMessageContent
import takagi.ru.monica.steam.friends.chat.ui.SteamChatComposer
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatDeliveryState
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatMessage
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatSummary
import takagi.ru.monica.steam.friends.groupchat.presentation.SteamGroupChatUiState

@Composable
internal fun SteamGroupChatThread(
    state: SteamGroupChatUiState,
    richMediaState: SteamChatRichMediaUiState,
    group: SteamGroupChatSummary,
    friends: List<SteamFriend>,
    targetMessageId: String? = null,
    onBack: () -> Unit,
    onOpenInfo: () -> Unit,
    onOpenRoom: (String, String) -> Unit,
    onLoadOlder: () -> Unit,
    onSend: (String) -> Unit,
    onInvite: () -> Unit,
    onAttachmentSelected: (String) -> Unit,
    onAttachmentSpoilerChanged: (Boolean) -> Unit,
    onUploadAttachment: () -> Unit,
    onClearAttachment: () -> Unit,
    onClearAttachmentFailure: () -> Unit,
    onRefreshCatalogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val messages = state.thread?.messages.orEmpty()
    val friendsById = remember(friends) { friends.associateBy(SteamFriend::steamId) }
    val listState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()
    val conversationKey = remember(state.accountSteamId, group.groupId, state.selectedChatId) {
        SteamChatReadingConversationKey.group(
            state.accountSteamId,
            group.groupId,
            state.selectedChatId.orEmpty()
        )
    }
    val messageIds = remember(messages) { messages.map(SteamGroupChatMessage::stableId) }
    val leadingItemCount = if (state.loadingOlder) 1 else 0
    val readingUi by rememberSteamChatReadingPosition(
        conversationKey = conversationKey,
        messageIds = messageIds,
        requestedMessageId = targetMessageId,
        leadingItemCount = leadingItemCount,
        listState = listState
    )
    val shouldLoadOlder by remember(listState, state.loadingOlder, state.thread?.moreAvailable) {
        derivedStateOf {
            state.thread?.moreAvailable == true && !state.loadingOlder &&
                listState.layoutInfo.visibleItemsInfo.firstOrNull()?.index?.let { it <= 2 } == true
        }
    }
    LaunchedEffect(shouldLoadOlder) { if (shouldLoadOlder) onLoadOlder() }
    SteamChatAutoScrollToLatestEffect(
        conversationKey = conversationKey,
        latestMessageId = messages.lastOrNull()?.stableId,
        latestMessageIsOutgoing = messages.lastOrNull()?.senderSteamId == state.accountSteamId,
        messageCount = messages.size,
        leadingItemCount = leadingItemCount,
        messagesBelow = readingUi.messagesBelow,
        restored = readingUi.restored,
        listState = listState
    )

    Column(modifier.fillMaxSize().imePadding()) {
        GroupThreadHeader(group, onBack, onOpenInfo, onInvite)
        if (group.rooms.size > 1) LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(group.rooms, key = { it.chatId }) { room ->
                FilterChip(
                    selected = room.chatId == state.selectedChatId,
                    onClick = { onOpenRoom(group.groupId, room.chatId) },
                    label = { Text(room.name, maxLines = 1) }
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.threadLoading && state.thread == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (state.loadingOlder) item("older-loading") {
                        Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        }
                    }
                    items(messages, key = SteamGroupChatMessage::stableId) { message ->
                        GroupMessageBubble(
                            message = message,
                            outgoing = message.senderSteamId == state.accountSteamId,
                            senderName = friendsById[message.senderSteamId]?.displayName
                                ?: message.senderSteamId.takeLast(8)
                        )
                    }
                }
            }
            SteamChatJumpToLatestButton(
                visible = readingUi.restored && readingUi.messagesBelow > 0,
                messagesBelow = readingUi.messagesBelow,
                onClick = {
                    scrollScope.launch {
                        listState.animateToLatestSteamChatMessage(messages.size, leadingItemCount)
                    }
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
            )
        }
        SteamChatComposer(
            richMediaState = richMediaState,
            onSend = onSend,
            onAttachmentSelected = onAttachmentSelected,
            onAttachmentSpoilerChanged = onAttachmentSpoilerChanged,
            onUploadAttachment = onUploadAttachment,
            onClearAttachment = onClearAttachment,
            onClearAttachmentFailure = onClearAttachmentFailure,
            onRefreshCatalogs = onRefreshCatalogs,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun SteamGroupChatThreadHost(
    state: SteamGroupChatUiState,
    richMediaState: SteamChatRichMediaUiState,
    friends: List<SteamFriend>,
    targetMessageId: String? = null,
    onBack: () -> Unit,
    onOpenInfo: () -> Unit,
    onOpenRoom: (String, String) -> Unit,
    onLoadOlder: () -> Unit,
    onSend: (String) -> Unit,
    onInvite: () -> Unit,
    onAttachmentSelected: (String) -> Unit,
    onAttachmentSpoilerChanged: (Boolean) -> Unit,
    onUploadAttachment: () -> Unit,
    onClearAttachment: () -> Unit,
    onClearAttachmentFailure: () -> Unit,
    onRefreshCatalogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val group = state.groups.firstOrNull { it.groupId == state.selectedGroupId } ?: return
    SteamGroupChatThread(
        state = state,
        richMediaState = richMediaState,
        group = group,
        friends = friends,
        targetMessageId = targetMessageId,
        onBack = onBack,
        onOpenInfo = onOpenInfo,
        onOpenRoom = onOpenRoom,
        onLoadOlder = onLoadOlder,
        onSend = onSend,
        onInvite = onInvite,
        onAttachmentSelected = onAttachmentSelected,
        onAttachmentSpoilerChanged = onAttachmentSpoilerChanged,
        onUploadAttachment = onUploadAttachment,
        onClearAttachment = onClearAttachment,
        onClearAttachmentFailure = onClearAttachmentFailure,
        onRefreshCatalogs = onRefreshCatalogs,
        modifier = modifier
    )
}

@Composable
private fun GroupThreadHeader(
    group: SteamGroupChatSummary,
    onBack: () -> Unit,
    onOpenInfo: () -> Unit,
    onInvite: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
        Column(Modifier.weight(1f).clickable(onClick = onOpenInfo).padding(vertical = 4.dp)) {
            Text(group.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(R.string.steam_group_chat_members, group.activeMemberCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onInvite) { Icon(Icons.Default.GroupAdd, stringResource(R.string.steam_group_chat_invite)) }
    }
}

@Composable
private fun GroupMessageBubble(message: SteamGroupChatMessage, outgoing: Boolean, senderName: String) {
    if (message.serverEventType > 0) {
        val eventText = if (message.senderSteamId.isNotBlank() && senderName.isNotBlank()) {
            "$senderName ${message.body}"
        } else message.body
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Text(eventText, Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium)
            }
        }
        return
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = if (outgoing) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (outgoing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                if (!outgoing) Text(senderName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                if (message.deleted) {
                    Text("Message deleted", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else SteamChatRichMessageContent(message.body)
                Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.timestamp * 1_000L)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (outgoing) {
                        Spacer(Modifier.width(3.dp))
                        when (message.deliveryState) {
                            SteamGroupChatDeliveryState.QUEUED,
                            SteamGroupChatDeliveryState.SENDING,
                            SteamGroupChatDeliveryState.VERIFYING -> CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
                            SteamGroupChatDeliveryState.SENT -> Icon(Icons.Default.Done, null, Modifier.size(15.dp))
                            SteamGroupChatDeliveryState.FAILED -> Icon(Icons.Default.ErrorOutline, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
