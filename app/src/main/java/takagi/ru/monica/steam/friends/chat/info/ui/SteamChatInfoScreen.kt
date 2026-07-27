package takagi.ru.monica.steam.friends.chat.info.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.steam.friends.chat.info.domain.SteamChatConversationPreferences
import takagi.ru.monica.steam.friends.chat.richmedia.ui.SteamChatRemoteImage
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatSummary
import takagi.ru.monica.steam.friends.ui.FriendAvatar

@Composable
internal fun SteamChatInfoScreen(
    title: String,
    directFriend: SteamFriend?,
    group: SteamGroupChatSummary?,
    members: List<SteamFriend>,
    preferences: SteamChatConversationPreferences,
    canEditGroup: Boolean,
    updatingGroup: Boolean,
    updatingGroupAvatar: Boolean,
    onBack: () -> Unit,
    onAddMember: () -> Unit,
    onSearchHistory: () -> Unit,
    onPreferencesChange: (SteamChatConversationPreferences) -> Unit,
    onUpdateGroup: (String, String) -> Unit,
    onUpdateGroupAvatar: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by remember { mutableStateOf(false) }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.toString()?.let(onUpdateGroupAvatar)
    }
    Column(modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            if (group != null && canEditGroup) {
                IconButton(onClick = { editing = true }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Edit, "Edit group")
                }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (group != null) {
                item("group-header") {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .then(
                                    if (canEditGroup && !updatingGroupAvatar) {
                                        Modifier.clickable { avatarPicker.launch("image/*") }
                                    } else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (group.avatarUrl.isNotBlank()) {
                                SteamChatRemoteImage(
                                    group.avatarUrl,
                                    group.name,
                                    Modifier.fillMaxSize()
                                )
                            } else {
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Groups, null, Modifier.size(34.dp))
                                    }
                                }
                            }
                            if (updatingGroupAvatar) {
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(28.dp),
                                            color = MaterialTheme.colorScheme.inverseOnSurface,
                                            strokeWidth = 3.dp
                                        )
                                    }
                                }
                            } else if (canEditGroup) {
                                Surface(
                                    modifier = Modifier.align(Alignment.BottomEnd).size(26.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Change group avatar",
                                            modifier = Modifier.size(15.dp),
                                            tint = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                            }
                        }
                        Column(Modifier.padding(start = 16.dp).weight(1f)) {
                            Text(group.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${group.activeMemberCount} 位成员", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item("members") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    directFriend?.let { friend ->
                        item(friend.steamId) { MemberAvatar(friend) }
                    }
                    items(members.filter { it.steamId != directFriend?.steamId }, key = SteamFriend::steamId) {
                        MemberAvatar(it)
                    }
                    item("add") {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                modifier = Modifier.size(58.dp).clickable(onClick = onAddMember),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, "Add member") } }
                            Text("添加", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
            if (group != null && group.tagline.isNotBlank()) {
                item("tagline") {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("群组简介", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Text(group.tagline, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            item("settings") {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Column {
                        InfoRow("查找聊天记录", Icons.Default.Search, onSearchHistory)
                        ToggleRow("消息免打扰", preferences.muted) {
                            onPreferencesChange(preferences.copy(muted = it))
                        }
                        ToggleRow("置顶聊天", preferences.pinned) {
                            onPreferencesChange(preferences.copy(pinned = it))
                        }
                    }
                }
            }
        }
    }
    if (editing && group != null) {
        EditGroupDialog(
            group = group,
            saving = updatingGroup,
            onDismiss = { if (!updatingGroup) editing = false },
            onSave = { name, tagline -> onUpdateGroup(name, tagline); editing = false }
        )
    }
}

@Composable
private fun MemberAvatar(friend: SteamFriend) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(70.dp)) {
        FriendAvatar(friend, 58)
        Text(friend.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun InfoRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(58.dp).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Text(label, Modifier.padding(start = 14.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked, onCheckedChange)
    }
}

@Composable
private fun EditGroupDialog(
    group: SteamGroupChatSummary,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember(group.groupId) { mutableStateOf(group.name) }
    var tagline by remember(group.groupId) { mutableStateOf(group.tagline) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑群组信息") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { if (it.length <= 64) name = it }, label = { Text("群组名称") }, singleLine = true)
                OutlinedTextField(tagline, { if (it.length <= 128) tagline = it }, label = { Text("群组简介") }, minLines = 2)
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name.trim(), tagline.trim()) }, enabled = name.isNotBlank() && !saving) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") } }
    )
}
