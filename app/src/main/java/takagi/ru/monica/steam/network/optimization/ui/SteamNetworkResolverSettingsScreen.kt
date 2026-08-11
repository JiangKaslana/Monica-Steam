package takagi.ru.monica.steam.network.optimization.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance
import takagi.ru.monica.steam.network.SteamHttpClientProvider
import takagi.ru.monica.steam.network.optimization.SteamNetworkResolverSettingsRuntime
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsProvider
import takagi.ru.monica.steam.network.optimization.domain.SteamNetworkResolverSettings
import takagi.ru.monica.steam.network.optimization.domain.SteamResolverInputValidator
import takagi.ru.monica.steam.network.optimization.ui.components.SteamDynamicDnsSettingsCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SteamNetworkResolverSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val applicationContext = context.applicationContext
    val settings by SteamNetworkResolverSettingsRuntime.settings.collectAsState()
    val dockClearance = LocalSteamDockContentClearance.current
    var cacheCount by rememberSaveable { mutableStateOf(SteamHttpClientProvider.dynamicDnsCacheSize()) }

    LaunchedEffect(context) {
        SteamNetworkResolverSettingsRuntime.initialize(context)
        cacheCount = SteamHttpClientProvider.dynamicDnsCacheSize()
    }
    LaunchedEffect(settings) {
        cacheCount = SteamHttpClientProvider.dynamicDnsCacheSize()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.steam_network_resolver_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 12.dp,
                end = 16.dp,
                bottom = dockClearance + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "resolver_status") {
                ResolverStatusCard(settings)
            }
            item(key = "dynamic_dns") {
                SteamDynamicDnsSettingsCard(
                    enabled = settings.dynamicDnsEnabled,
                    activeProviderCount = settings.activeProviders.count { !it.isSystem },
                    cacheCount = cacheCount,
                    onEnabledChange = {
                        SteamNetworkResolverSettingsRuntime.setDynamicDnsEnabled(
                            applicationContext,
                            it
                        )
                    },
                    onClearCache = {
                        SteamHttpClientProvider.clearDynamicDnsCache()
                        cacheCount = SteamHttpClientProvider.dynamicDnsCacheSize()
                    }
                )
            }
            item(key = "resolver_defaults") {
                ResolverDefaultsCard(
                    settings = settings,
                    onUseSystemDns = {
                        SteamNetworkResolverSettingsRuntime.setUseSystemDns(
                            applicationContext,
                            it
                        )
                    },
                    onUseBuiltInDoh = {
                        SteamNetworkResolverSettingsRuntime.setUseBuiltInDoh(
                            applicationContext,
                            it
                        )
                    }
                )
            }
            item(key = "custom_dns") {
                ResolverEditorCard(
                    title = stringResource(R.string.steam_network_custom_dns_title),
                    description = stringResource(R.string.steam_network_custom_dns_description),
                    placeholder = stringResource(R.string.steam_network_custom_dns_placeholder),
                    icon = Icons.Default.Dns,
                    values = settings.customDnsServers,
                    limit = SteamNetworkResolverSettings.MAX_CUSTOM_DNS,
                    normalize = SteamResolverInputValidator::normalizeDnsServer,
                    onAdd = {
                        SteamNetworkResolverSettingsRuntime.addCustomDns(
                            applicationContext,
                            it
                        )
                    },
                    onRemove = {
                        SteamNetworkResolverSettingsRuntime.removeCustomDns(
                            applicationContext,
                            it
                        )
                    }
                )
            }
            item(key = "custom_doh") {
                ResolverEditorCard(
                    title = stringResource(R.string.steam_network_custom_doh_title),
                    description = stringResource(R.string.steam_network_custom_doh_description),
                    placeholder = stringResource(R.string.steam_network_custom_doh_placeholder),
                    icon = Icons.Default.Public,
                    values = settings.customDohEndpoints,
                    limit = SteamNetworkResolverSettings.MAX_CUSTOM_DOH,
                    normalize = SteamResolverInputValidator::normalizeDohEndpoint,
                    onAdd = {
                        SteamNetworkResolverSettingsRuntime.addCustomDoh(
                            applicationContext,
                            it
                        )
                    },
                    onRemove = {
                        SteamNetworkResolverSettingsRuntime.removeCustomDoh(
                            applicationContext,
                            it
                        )
                    }
                )
            }
            item(key = "resolver_privacy") {
                ResolverPrivacyCard()
            }
        }
    }
}

@Composable
private fun ResolverStatusCard(settings: SteamNetworkResolverSettings) {
    val activeCount = settings.activeProviders.size
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (settings.hasResolver) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = if (settings.hasResolver) {
                    stringResource(R.string.steam_network_resolver_active_count, activeCount)
                } else {
                    stringResource(R.string.steam_network_resolver_none)
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.steam_network_resolver_summary),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ResolverDefaultsCard(
    settings: SteamNetworkResolverSettings,
    onUseSystemDns: (Boolean) -> Unit,
    onUseBuiltInDoh: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        ResolverToggleRow(
            title = stringResource(R.string.steam_network_system_dns),
            description = stringResource(R.string.steam_network_system_dns_description),
            checked = settings.useSystemDns,
            onCheckedChange = onUseSystemDns
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 18.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        )
        ResolverToggleRow(
            title = stringResource(R.string.steam_network_builtin_doh),
            description = SteamDnsProvider.DEFAULTS
                .filterNot(SteamDnsProvider::isSystem)
                .joinToString(" · ", transform = SteamDnsProvider::displayName),
            checked = settings.useBuiltInDoh,
            onCheckedChange = onUseBuiltInDoh
        )
    }
}

@Composable
private fun ResolverToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ResolverEditorCard(
    title: String,
    description: String,
    placeholder: String,
    icon: ImageVector,
    values: List<String>,
    limit: Int,
    normalize: (String) -> String?,
    onAdd: (String) -> Boolean,
    onRemove: (String) -> Unit
) {
    var input by rememberSaveable(title) { mutableStateOf("") }
    val normalized = normalize(input)
    val inputInvalid = input.isNotBlank() && normalized == null
    val canAdd = normalized != null && normalized !in values && values.size < limit

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp).size(22.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${values.size}/$limit",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = input,
                onValueChange = { input = it.take(512) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = inputInvalid,
                placeholder = { Text(placeholder) },
                supportingText = if (inputInvalid) {
                    { Text(stringResource(R.string.steam_network_resolver_invalid)) }
                } else {
                    null
                }
            )
            FilledTonalButton(
                onClick = {
                    if (onAdd(input)) input = ""
                },
                enabled = canAdd,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(
                    text = stringResource(R.string.steam_network_resolver_add),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            values.forEach { value ->
                ResolverEndpointRow(value = value, onRemove = { onRemove(value) })
            }
        }
    }
}

@Composable
private fun ResolverEndpointRow(value: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Text(
                text = value,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.delete),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun ResolverPrivacyCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.steam_network_resolver_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
