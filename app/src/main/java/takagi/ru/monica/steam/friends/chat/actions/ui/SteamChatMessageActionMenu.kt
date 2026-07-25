package takagi.ru.monica.steam.friends.chat.actions.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import takagi.ru.monica.R
import takagi.ru.monica.steam.friends.chat.actions.domain.SteamChatReportReason
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatEmoticon
import takagi.ru.monica.steam.friends.chat.richmedia.ui.SteamChatRemoteImage

@Composable
fun SteamChatMessageActionMenu(
    touchPosition: IntOffset,
    canReport: Boolean,
    onDismiss: () -> Unit,
    onOpenReactions: () -> Unit,
    onCopy: () -> Unit,
    onReport: () -> Unit
) {
    TouchCenteredPopup(touchPosition, onDismiss) {
        Surface(
            modifier = Modifier.width(220.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(Modifier.padding(vertical = 8.dp)) {
                ActionRow(Icons.Default.EmojiEmotions, R.string.steam_chat_action_react, onOpenReactions)
                ActionRow(Icons.Default.ContentCopy, R.string.steam_chat_action_copy, onCopy)
                if (canReport) {
                    ActionRow(Icons.Default.Flag, R.string.steam_chat_action_report, onReport)
                }
            }
        }
    }
}

@Composable
fun SteamChatReactionPicker(
    touchPosition: IntOffset,
    emoticons: List<SteamChatEmoticon>,
    onDismiss: () -> Unit,
    onReact: (SteamChatEmoticon) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, emoticons) {
        emoticons.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
    }
    TouchCenteredPopup(touchPosition, onDismiss) {
        Surface(
            modifier = Modifier.width(328.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.steam_chat_reaction_picker_title),
                    style = MaterialTheme.typography.titleMedium
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = { Text(stringResource(R.string.steam_chat_rich_picker_search)) }
                )
                if (filtered.isEmpty()) {
                    Text(
                        text = stringResource(R.string.steam_chat_reaction_picker_empty),
                        modifier = Modifier.padding(vertical = 24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filtered, key = SteamChatEmoticon::name) { emoticon ->
                            Surface(
                                onClick = { onReact(emoticon) },
                                modifier = Modifier.size(64.dp),
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest
                            ) {
                                SteamChatRemoteImage(
                                    url = emoticon.imageUrl,
                                    contentDescription = emoticon.name,
                                    modifier = Modifier.padding(12.dp).size(40.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: Int,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, color = androidx.compose.ui.graphics.Color.Transparent) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null)
            Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun TouchCenteredPopup(
    touchPosition: IntOffset,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    Popup(
        popupPositionProvider = remember(touchPosition) {
            TouchCenteredPositionProvider(touchPosition)
        },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
        content = content
    )
}

internal class TouchCenteredPositionProvider(
    private val touchPosition: IntOffset,
    private val edgeMargin: Int = 16
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset = IntOffset(
        x = (touchPosition.x - popupContentSize.width / 2)
            .coerceIn(edgeMargin, (windowSize.width - popupContentSize.width - edgeMargin).coerceAtLeast(edgeMargin)),
        y = (touchPosition.y - popupContentSize.height / 2)
            .coerceIn(edgeMargin, (windowSize.height - popupContentSize.height - edgeMargin).coerceAtLeast(edgeMargin))
    )
}

@Composable
fun SteamChatReportDialog(
    selectedReason: SteamChatReportReason,
    onReasonSelected: (SteamChatReportReason) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.steam_chat_report_title)) },
        text = {
            Column {
                SteamChatReportReason.entries.forEach { reason ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedReason == reason, onClick = { onReasonSelected(reason) })
                        Text(stringResource(reason.labelResource()))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.steam_chat_report_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

private fun SteamChatReportReason.labelResource(): Int = when (this) {
    SteamChatReportReason.HARASSMENT -> R.string.steam_chat_report_harassment
    SteamChatReportReason.SCAM -> R.string.steam_chat_report_scam
    SteamChatReportReason.SPAM -> R.string.steam_chat_report_spam
    SteamChatReportReason.OTHER -> R.string.steam_chat_report_other
}
