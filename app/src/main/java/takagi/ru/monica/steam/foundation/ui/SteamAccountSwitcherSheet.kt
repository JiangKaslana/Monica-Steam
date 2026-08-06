package takagi.ru.monica.steam.foundation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.LocalMdbxDatabase
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.data.SteamStorageSource
import takagi.ru.monica.ui.components.MonicaExpressiveFilterChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SteamAccountSwitcherSheet(
    accounts: List<SteamAccount>,
    selectedAccountId: Long?,
    storageSource: SteamStorageSource,
    mdbxDatabases: List<LocalMdbxDatabase>,
    loading: Boolean,
    errorMessage: String?,
    onSelectStorageSource: (SteamStorageSource) -> Unit,
    onSelectAccount: (Long) -> Unit,
    onAddAccount: () -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        tonalElevation = 0.dp
    ) {
        Text(
            text = stringResource(R.string.steam_switch_account),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )

        if (mdbxDatabases.isNotEmpty()) {
            Text(
                text = stringResource(R.string.category_selection_menu_databases),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 8.dp)
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "local") {
                    MonicaExpressiveFilterChip(
                        selected = storageSource is SteamStorageSource.Local,
                        onClick = { onSelectStorageSource(SteamStorageSource.Local) },
                        label = stringResource(R.string.category_selection_menu_local_database),
                        leadingIcon = Icons.Default.Smartphone
                    )
                }
                items(mdbxDatabases, key = LocalMdbxDatabase::id) { database ->
                    MonicaExpressiveFilterChip(
                        selected = storageSource is SteamStorageSource.Mdbx &&
                            storageSource.databaseId == database.id,
                        onClick = {
                            onSelectStorageSource(SteamStorageSource.Mdbx(database.id))
                        },
                        label = database.name.ifBlank { "MDBX" },
                        leadingIcon = Icons.Default.Storage,
                        statusDotColor = Color(0xFF22C55E)
                    )
                }
            }
        }

        when {
            loading -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 112.dp)
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(top = 28.dp))
                }
            }
            !errorMessage.isNullOrBlank() -> {
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.steam_cannot_load_mdbx_accounts))
                    },
                    supportingContent = {
                        Text(
                            text = errorMessage,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    trailingContent = {
                        IconButton(onClick = onRefresh) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.refresh)
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
            accounts.isEmpty() -> {
                Text(
                    text = stringResource(R.string.steam_store_no_account),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp)
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    itemsIndexed(
                        items = accounts,
                        key = { _, account -> account.id }
                    ) { index, account ->
                        ListItem(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp)
                                .clickable { onSelectAccount(account.id) },
                            headlineContent = {
                                Text(
                                    text = account.displayName.ifBlank {
                                        account.accountName.ifBlank { account.visibleSteamId }
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = listOf(account.accountName, account.visibleSteamId)
                                        .filter(String::isNotBlank)
                                        .distinct()
                                        .joinToString(" · "),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingContent = {
                                SteamAvatarImage(account = account, size = 48.dp)
                            },
                            trailingContent = {
                                if (account.id == selectedAccountId) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = stringResource(
                                            R.string.steam_selected_account_marker
                                        ),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                        if (index < accounts.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(start = 80.dp))
                        }
                    }
                }
            }
        }

        HorizontalDivider()
        ListItem(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable {
                    onDismiss()
                    onAddAccount()
                },
            headlineContent = {
                Text(
                    text = stringResource(R.string.steam_add_account_title),
                    fontWeight = FontWeight.Medium
                )
            },
            leadingContent = {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}
