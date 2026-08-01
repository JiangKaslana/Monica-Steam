package takagi.ru.monica.steam.network.optimization.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance
import takagi.ru.monica.steam.network.optimization.SteamNetworkOptimizationRuntime
import takagi.ru.monica.steam.network.optimization.domain.SteamHostsRuleError
import takagi.ru.monica.steam.network.optimization.domain.SteamHostsRuleErrorReason
import takagi.ru.monica.steam.network.optimization.domain.SteamHostsRuleParser
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
    var hostsDraft by rememberSaveable { mutableStateOf(settings.hostsText) }
    LaunchedEffect(settings.hostsText) {
        hostsDraft = settings.hostsText
    }
    val parsedDraft = remember(hostsDraft) { SteamHostsRuleParser.parse(hostsDraft) }
    val firstError = parsedDraft.errors.firstOrNull()

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
                        subtitle = if (settings.hostCount > 0) {
                            context.getString(
                                R.string.steam_network_optimization_switch_description,
                                settings.hostCount
                            )
                        } else {
                            context.getString(R.string.steam_network_optimization_switch_empty)
                        },
                        checked = settings.enabled,
                        enabled = settings.hostCount > 0,
                        onCheckedChange = { enabled ->
                            SteamNetworkOptimizationRuntime.setEnabled(context, enabled)
                        }
                    )
                }
            }
            item {
                SettingsSection(
                    title = context.getString(R.string.steam_network_optimization_rules_section)
                ) {
                    CustomHostsEditor(
                        value = hostsDraft,
                        onValueChange = { hostsDraft = it },
                        hostCount = parsedDraft.hostCount,
                        error = firstError,
                        hasChanges = hostsDraft != settings.hostsText,
                        onSave = {
                            val result = SteamNetworkOptimizationRuntime.saveHosts(
                                context,
                                hostsDraft
                            )
                            if (result.isValid) {
                                Toast.makeText(
                                    context,
                                    R.string.steam_network_optimization_hosts_saved,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
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
        }
    }
}

@Composable
private fun CustomHostsEditor(
    value: String,
    onValueChange: (String) -> Unit,
    hostCount: Int,
    error: SteamHostsRuleError?,
    hasChanges: Boolean,
    onSave: () -> Unit
) {
    val context = LocalContext.current
    val supportingText = error?.let { hostsErrorText(it) }
        ?: context.getString(R.string.steam_network_optimization_hosts_helper)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(context.getString(R.string.steam_network_optimization_hosts_label)) },
                placeholder = {
                    Text(context.getString(R.string.steam_network_optimization_hosts_placeholder))
                },
                supportingText = { Text(supportingText) },
                isError = error != null,
                minLines = 6,
                maxLines = 12
            )
            Text(
                text = if (hostCount > 0 && error == null) {
                    context.getString(R.string.steam_network_optimization_hosts_ready, hostCount)
                } else {
                    context.getString(R.string.steam_network_optimization_hosts_empty)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FilledTonalButton(
                onClick = onSave,
                enabled = hasChanges && error == null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(context.getString(R.string.steam_network_optimization_hosts_save))
            }
        }
    }
}

@Composable
private fun hostsErrorText(error: SteamHostsRuleError): String {
    val stringId = when (error.reason) {
        SteamHostsRuleErrorReason.INVALID_FORMAT ->
            R.string.steam_network_optimization_error_format
        SteamHostsRuleErrorReason.INVALID_IP ->
            R.string.steam_network_optimization_error_ip
        SteamHostsRuleErrorReason.INVALID_HOSTNAME ->
            R.string.steam_network_optimization_error_hostname
        SteamHostsRuleErrorReason.UNUSABLE_ADDRESS ->
            R.string.steam_network_optimization_error_address
    }
    return LocalContext.current.getString(stringId, error.lineNumber)
}
