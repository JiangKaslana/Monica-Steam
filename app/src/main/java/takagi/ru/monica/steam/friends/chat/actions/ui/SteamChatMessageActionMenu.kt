package takagi.ru.monica.steam.friends.chat.actions.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.friends.chat.actions.domain.SteamChatReportReason
import takagi.ru.monica.steam.friends.chat.richmedia.domain.SteamChatEmoticon
import takagi.ru.monica.steam.friends.chat.richmedia.ui.SteamChatRemoteImage

@Composable
fun SteamChatMessageActionMenu(
    expanded: Boolean,
    emoticons: List<SteamChatEmoticon>,
    canReport: Boolean,
    onDismiss: () -> Unit,
    onReact: (SteamChatEmoticon) -> Unit,
    onCopy: () -> Unit,
    onReport: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (emoticons.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                emoticons.take(8).forEach { emoticon ->
                    Surface(
                        onClick = { onReact(emoticon) },
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        SteamChatRemoteImage(
                            url = emoticon.imageUrl,
                            contentDescription = emoticon.name,
                            modifier = Modifier.padding(9.dp).size(24.dp)
                        )
                    }
                }
            }
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.steam_chat_action_copy)) },
            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
            onClick = onCopy
        )
        if (canReport) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.steam_chat_action_report)) },
                leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null) },
                onClick = onReport
            )
        }
    }
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
            androidx.compose.foundation.layout.Column {
                SteamChatReportReason.entries.forEach { reason ->
                    Row {
                        RadioButton(
                            selected = selectedReason == reason,
                            onClick = { onReasonSelected(reason) }
                        )
                        Text(
                            text = stringResource(reason.labelResource()),
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.steam_chat_report_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private fun SteamChatReportReason.labelResource(): Int = when (this) {
    SteamChatReportReason.HARASSMENT -> R.string.steam_chat_report_harassment
    SteamChatReportReason.SCAM -> R.string.steam_chat_report_scam
    SteamChatReportReason.SPAM -> R.string.steam_chat_report_spam
    SteamChatReportReason.OTHER -> R.string.steam_chat_report_other
}
