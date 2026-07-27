package takagi.ru.monica.steam.store.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.steam.store.domain.SteamStoreBrowseFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SteamStoreBrowseDrawer(
    selectedFilter: SteamStoreBrowseFilter,
    onSelectFilter: (SteamStoreBrowseFilter) -> Unit,
    onOpenPointsShop: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (openDrawer: () -> Unit) -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val closeDrawer: () -> Unit = { scope.launch { drawerState.close() } }
    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
    BackHandler(enabled = drawerState.isOpen, onBack = closeDrawer)

    // Steam's store controls are placed on the right side of the top bar. RTL
    // placement makes the standard M3 drawer enter from that same edge while
    // the actual store content remains laid out in its normal direction.
    androidx.compose.runtime.CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl
    ) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = true,
            modifier = modifier,
            drawerContent = {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalLayoutDirection provides LayoutDirection.Ltr
                ) {
                    ModalDrawerSheet(
                        modifier = Modifier
                            .fillMaxHeight()
                            .widthIn(max = 360.dp),
                        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        SteamStoreBrowseDrawerContent(
                            selectedFilter = selectedFilter,
                            onSelectFilter = {
                                onSelectFilter(it)
                                closeDrawer()
                            },
                            onOpenPointsShop = {
                                onOpenPointsShop()
                                closeDrawer()
                            },
                            onClose = closeDrawer
                        )
                    }
                }
            }
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Ltr
            ) {
                content(openDrawer)
            }
        }
    }
}

@Composable
private fun SteamStoreBrowseDrawerContent(
    selectedFilter: SteamStoreBrowseFilter,
    onSelectFilter: (SteamStoreBrowseFilter) -> Unit,
    onOpenPointsShop: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 12.dp, bottom = 12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.FilterAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.steam_store_browse),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            )
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.close)
                )
            }
        }
        HorizontalDivider()
        Column(modifier = Modifier.padding(top = 8.dp)) {
            SteamStoreBrowseFilter.entries.forEach { filter ->
                NavigationDrawerItem(
                    label = { Text(storeBrowseFilterLabel(filter)) },
                    selected = filter == selectedFilter,
                    onClick = { onSelectFilter(filter) },
                    icon = {
                        Icon(
                            imageVector = storeBrowseFilterIcon(filter),
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp)
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.steam_store_points_shop)) },
                selected = false,
                onClick = onOpenPointsShop,
                icon = {
                    Icon(Icons.Default.CardGiftcard, contentDescription = null)
                },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp)
            )
        }
    }
}
