package takagi.ru.monica.steam.foundation.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R

@Composable
fun SteamPageOverflowMenu(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.refresh)) },
                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                enabled = !refreshing,
                onClick = {
                    expanded = false
                    onRefresh()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_title)) },
                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                onClick = {
                    expanded = false
                    onOpenSettings()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SteamExpressivePullToRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
        state = state,
        indicator = {
            if (refreshing || state.distanceFraction > 0f) {
                Box(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (refreshing) {
                        LoadingIndicator(
                            modifier = Modifier.size(42.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        LoadingIndicator(
                            progress = { state.distanceFraction.coerceIn(0f, 1f) },
                            modifier = Modifier.size(42.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        content = content
    )
}
