package takagi.ru.monica.steam.friends.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import takagi.ru.monica.steam.foundation.ui.SteamExpressivePullToRefresh
import takagi.ru.monica.steam.friends.chat.domain.SteamChatSession
import takagi.ru.monica.steam.friends.chat.presentation.SteamChatFailureReason
import takagi.ru.monica.steam.friends.chat.presentation.SteamChatUiState
import takagi.ru.monica.steam.friends.chat.richmedia.ui.SteamChatRemoteImage
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatSummary
import takagi.ru.monica.steam.friends.groupchat.presentation.SteamGroupChatUiState
import takagi.ru.monica.steam.friends.ui.FriendAvatar
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance

internal enum class SteamConversationType { DIRECT, GROUP }

internal data class SteamConversationListEntry(
    val type: SteamConversationType,
    val id: String,
    val chatId: String = "",
    val title: String,
    val subtitle: String,
    val timestamp: Long,
    val unreadCount: Int,
    val pinned: Boolean,
    val friend: SteamFriend? = null,
    val avatarUrl: String = ""
) {
    val stableKey: String get() = "${type.name}:$id:$chatId"
}

internal fun buildSteamConversationEntries(
    sessions: List<SteamChatSession>,
    groups: List<SteamGroupChatSummary>,
    friends: List<SteamFriend>,
    query: String,
    pinnedPartnerSteamIds: Set<String>,
    pinnedGroupIds: Set<String>
): List<SteamConversationListEntry> {
    val normalizedQuery = query.trim()
    val friendsById = friends.associateBy(SteamFriend::steamId)
    val direct = sessions.map { session ->
        val friend = friendsById[session.partnerSteamId]
        SteamConversationListEntry(
            type = SteamConversationType.DIRECT,
            id = session.partnerSteamId,
            title = friend?.displayName ?: session.partnerSteamId,
            subtitle = friend?.gameName?.takeIf(String::isNotBlank).orEmpty(),
            timestamp = session.lastMessageTimestamp,
            unreadCount = session.unreadCount,
            pinned = session.partnerSteamId in pinnedPartnerSteamIds,
            friend = friend
        )
    }.toMutableList()
    if (normalizedQuery.isNotBlank()) {
        val existing = direct.mapTo(mutableSetOf()) { it.id }
        friends.filter { friend ->
            friend.steamId !in existing && (
                friend.displayName.contains(normalizedQuery, true) ||
                    friend.realName.contains(normalizedQuery, true) ||
                    friend.steamId.contains(normalizedQuery, true)
                )
        }.forEach { friend ->
            direct += SteamConversationListEntry(
                type = SteamConversationType.DIRECT,
                id = friend.steamId,
                title = friend.displayName,
                subtitle = friend.gameName,
                timestamp = 0L,
                unreadCount = 0,
                pinned = friend.steamId in pinnedPartnerSteamIds,
                friend = friend
            )
        }
    }
    val groupEntries = groups.map { group ->
        val room = group.rooms.firstOrNull { it.chatId == group.preferredChatId }
            ?: group.rooms.maxByOrNull { it.lastMessageTimestamp }
        SteamConversationListEntry(
            type = SteamConversationType.GROUP,
            id = group.groupId,
            chatId = room?.chatId ?: group.preferredChatId,
            title = group.name,
            subtitle = room?.lastMessage.orEmpty().ifBlank { group.tagline }
                .ifBlank { "${group.activeMemberCount} members" },
            timestamp = room?.lastMessageTimestamp ?: 0L,
            unreadCount = group.unreadCount,
            pinned = group.groupId in pinnedGroupIds,
            avatarUrl = group.avatarUrl
        )
    }
    return (direct + groupEntries)
        .filter { entry ->
            normalizedQuery.isBlank() || entry.title.contains(normalizedQuery, true) ||
                entry.subtitle.contains(normalizedQuery, true) || entry.id.contains(normalizedQuery)
        }
        .sortedWith(
            compareByDescending<SteamConversationListEntry> { it.pinned }
                .thenByDescending { it.timestamp }
                .thenBy { it.title.lowercase() }
        )
}

@Composable
internal fun SteamConversationList(
    chatState: SteamChatUiState,
    groupState: SteamGroupChatUiState,
    friends: List<SteamFriend>,
    query: String,
    pinnedPartnerSteamIds: Set<String>,
    pinnedGroupIds: Set<String>,
    onOpenDirect: (String) -> Unit,
    onOpenGroup: (String, String) -> Unit,
    onRefresh: () -> Unit,
    onCreateGroup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dockClearance = LocalSteamDockContentClearance.current
    val entries = buildSteamConversationEntries(
        sessions = chatState.sessions?.sessions.orEmpty(),
        groups = groupState.groups,
        friends = friends,
        query = query,
        pinnedPartnerSteamIds = pinnedPartnerSteamIds,
        pinnedGroupIds = pinnedGroupIds
    )
    SteamExpressivePullToRefresh(
        refreshing = chatState.sessionsRefreshing || groupState.groupsRefreshing,
        onRefresh = onRefresh,
        modifier = modifier
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = 8.dp,
                bottom = dockClearance + 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            chatState.sessionsFailure?.let { failure ->
                item("conversation-error") { ChatFailureBanner(failure, onRefresh) }
            }
            if (groupState.groupsFailure) {
                item("group-conversation-error") {
                    ChatFailureBanner(SteamChatFailureReason.UNAVAILABLE, onRefresh)
                }
            }
            if ((chatState.sessionsLoading || groupState.groupsLoading) && entries.isEmpty()) {
                item("conversation-loading") {
                    Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (entries.isEmpty()) {
                item("conversation-empty") {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 56.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.ChatBubbleOutline, null, Modifier.size(46.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("暂无聊天", style = MaterialTheme.typography.titleMedium)
                    }
                }
            } else {
                items(entries, key = SteamConversationListEntry::stableKey) { entry ->
                    SteamConversationRow(
                        entry = entry,
                        onClick = {
                            if (entry.type == SteamConversationType.DIRECT) onOpenDirect(entry.id)
                            else onOpenGroup(entry.id, entry.chatId)
                        },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onCreateGroup,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = dockClearance + 12.dp),
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("新建群聊") }
        )
    }
}

@Composable
private fun SteamConversationRow(
    entry: SteamConversationListEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 72.dp),
        shape = RoundedCornerShape(18.dp),
        color = if (entry.unreadCount > 0) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                entry.friend != null -> FriendAvatar(entry.friend, 50)
                entry.avatarUrl.isNotBlank() -> SteamChatRemoteImage(
                    entry.avatarUrl,
                    entry.title,
                    Modifier.size(50.dp).clip(CircleShape)
                )
                else -> Surface(Modifier.size(50.dp), CircleShape, MaterialTheme.colorScheme.primaryContainer) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Groups, null) }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (entry.subtitle.isNotBlank()) {
                    Text(entry.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (entry.timestamp > 0L) Text(
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.timestamp * 1_000L)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (entry.unreadCount > 0) Badge { Text(entry.unreadCount.coerceAtMost(99).toString()) }
            }
        }
    }
}
