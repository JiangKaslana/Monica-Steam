package takagi.ru.monica.steam.network.optimization.ui

import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance
import takagi.ru.monica.steam.network.optimization.SteamNetworkOptimizationRuntime
import takagi.ru.monica.ui.screens.SettingsItem
import takagi.ru.monica.ui.screens.SettingsItemWithSwitch
import takagi.ru.monica.ui.screens.SettingsSection

@Composable
fun SteamNetworkOptimizationSettingsEntry(onClick: () -> Unit) {
    val context = LocalContext.current
    SettingsSection(title = context.getString(R.string.steam_network_optimization_section)) {
        SettingsItem(
            icon = Icons.Default.Dns,
            title = context.getString(R.string.steam_network_optimization_title),
            subtitle = context.getString(R.string.steam_network_optimization_description),
            onClick = onClick
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SteamNetworkOptimizationSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LaunchedEffect(context) {
        SteamNetworkOptimizationRuntime.initialize(context)
    }
    val settings by SteamNetworkOptimizationRuntime.settings.collectAsState()
    val dockClearance = LocalSteamDockContentClearance.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(context.getString(R.string.steam_network_optimization_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            context.getString(R.string.back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = dockClearance + 24.dp)
        ) {
            item {
                SettingsSection(
                    title = context.getString(R.string.steam_network_optimization_status_section)
                ) {
                    SettingsItemWithSwitch(
                        icon = Icons.Default.Dns,
                        title = context.getString(R.string.steam_network_optimization_switch_title),
                        subtitle = context.getString(R.string.steam_network_optimization_switch_description),
                        checked = settings.enabled,
                        onCheckedChange = { enabled ->
                            SteamNetworkOptimizationRuntime.setEnabled(context, enabled)
                        }
                    )
                }
            }
            item {
                SettingsSection(
                    title = context.getString(R.string.steam_network_optimization_scope_section)
                ) {
                    SettingsItem(
                        icon = Icons.Default.Security,
                        title = context.getString(R.string.steam_network_optimization_scope_title),
                        subtitle = context.getString(R.string.steam_network_optimization_scope_description),
                        onClick = {},
                        trailingContent = {}
                    )
                }
            }
            item {
                SettingsSection(
                    title = context.getString(R.string.steam_network_optimization_maintenance_section)
                ) {
                    SettingsItem(
                        icon = Icons.Default.DeleteSweep,
                        title = context.getString(R.string.steam_network_optimization_clear_cache),
                        subtitle = context.getString(R.string.steam_network_optimization_clear_cache_description),
                        onClick = {
                            SteamNetworkOptimizationRuntime.clearDnsCache()
                            Toast.makeText(
                                context,
                                R.string.steam_network_optimization_cache_cleared,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
            }
        }
    }
}
