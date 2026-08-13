package takagi.ru.monica.steam.network.optimization.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance
import takagi.ru.monica.steam.network.SteamHttpClientProvider
import takagi.ru.monica.steam.network.optimization.SteamNetworkResolverSettingsRuntime
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsProvider
import takagi.ru.monica.steam.network.optimization.domain.SteamNetworkResolverSettings
import takagi.ru.monica.steam.network.optimization.domain.SteamResolverInputValidator
import takagi.ru.monica.steam.network.optimization.ui.components.SteamDynamicDnsSettingsCard
import takagi.ru.monica.steam.network.optimization.ui.components.SteamResolverServerBenchmarkCard

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
    val scope = rememberCoroutineScope()
    var cacheCount by rememberSaveable { mutableStateOf(SteamHttpClientProvider.dynamicDnsCacheSize()) }
    var refreshing by rememberSaveable { mutableStateOf(false) }

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
            item(key = "dynamic_dns") {
                SteamDynamicDnsSettingsCard(
                    enabled = settings.dynamicDnsEnabled,
                    activeProviderCount = settings.activeProviders.size,
                    cacheCount = cacheCount,
                    refreshing = refreshing,
                    canEnable = settings.hasResolver,
                    onEnabledChange = {
                        SteamNetworkResolverSettingsRuntime.setDynamicDnsEnabled(
                            applicationContext,
                            it
                        )
                    },
                    onClearCache = {
                        SteamHttpClientProvider.clearDynamicDnsCache()
                        cacheCount = SteamHttpClientProvider.dynamicDnsCacheSize()
                    },
                    onForceRefresh = {
                        if (!refreshing) {
                            refreshing = true
                            scope.launch {
                                try {
                                    cacheCount = SteamHttpClientProvider.refreshDynamicDnsCache()
                                } finally {
                                    refreshing = false
                                }
                            }
                        }
                    }
                )
            }
            item(key = "resolver_servers") {
                SteamResolverServerBenchmarkCard(
                    onRemoveCustomDns = {
                        SteamNetworkResolverSettingsRuntime.removeCustomDns(
                            applicationContext,
                            it
                        )
                    },
                    onRemoveCustomDoh = {
                        SteamNetworkResolverSettingsRuntime.removeCustomDoh(
                            applicationContext,
                            it
                        )
                    }
                )
            }
            item(key = "custom_dns") {
                ResolverAddCard(
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
                    }
                )
            }
            item(key = "custom_doh") {
                DohResolverAddCard(
                    values = settings.customDohEndpoints,
                    limit = SteamNetworkResolverSettings.MAX_CUSTOM_DOH,
                    onAdd = { endpoint, bootstrapAddresses ->
                        SteamNetworkResolverSettingsRuntime.addCustomDoh(
                            applicationContext,
                            endpoint,
                            bootstrapAddresses
                        )
                    }
                )
            }
            item(key = "ech_doh") {
                EchResolverSelectionCard(
                    providers = settings.selectableDohProviders,
                    selectedProviderId = settings.echDohProviderId,
                    onSelect = { providerId ->
                        SteamNetworkResolverSettingsRuntime.setEchDohProvider(
                            applicationContext,
                            providerId
                        )
                    }
                )
            }
            item(key = "resolver_strategy") {
                ResolverStrategyCard(
                    preferIpv6 = settings.preferIpv6,
                    onPreferIpv6 = {
                        SteamNetworkResolverSettingsRuntime.setPreferIpv6(
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
private fun EchResolverSelectionCard(
    providers: List<SteamDnsProvider>,
    selectedProviderId: String?,
    onSelect: (String?) -> Unit
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val selectedProvider = providers.firstOrNull { it.id == selectedProviderId }
    val selectedLabel = selectedProvider?.displayName
        ?: stringResource(R.string.steam_network_ech_same_as_dns)
    val selectedDescription = selectedProvider?.dohUrl
        ?: stringResource(R.string.steam_network_ech_same_as_dns_description)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp).size(22.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.steam_network_ech_server_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.steam_network_ech_server_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = selectedLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = selectedDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = { showDialog = true }) {
                Text(stringResource(R.string.steam_network_ech_choose))
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.steam_network_ech_dialog_title)) },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                ) {
                    item(key = "same_as_dns") {
                        EchResolverOptionRow(
                            title = stringResource(R.string.steam_network_ech_same_as_dns),
                            description = stringResource(
                                R.string.steam_network_ech_same_as_dns_description
                            ),
                            selected = selectedProviderId == null,
                            onClick = {
                                onSelect(null)
                                showDialog = false
                            }
                        )
                    }
                    items(
                        items = providers,
                        key = SteamDnsProvider::id
                    ) { provider ->
                        EchResolverOptionRow(
                            title = provider.displayName,
                            description = provider.dohUrl.orEmpty(),
                            selected = selectedProviderId == provider.id,
                            onClick = {
                                onSelect(provider.id)
                                showDialog = false
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.steam_network_ech_done))
                }
            }
        )
    }
}

@Composable
private fun EchResolverOptionRow(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RadioButton(
            selected = selected,
            onClick = null
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ResolverStrategyCard(
    preferIpv6: Boolean,
    onPreferIpv6: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        ResolverToggleRow(
            title = stringResource(R.string.steam_network_ipv6_prefer),
            description = stringResource(R.string.steam_network_ipv6_prefer_description),
            checked = preferIpv6,
            onCheckedChange = onPreferIpv6
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
private fun ResolverAddCard(
    title: String,
    description: String,
    placeholder: String,
    icon: ImageVector,
    values: List<String>,
    limit: Int,
    normalize: (String) -> String?,
    onAdd: (String) -> Boolean
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
            ResolverAddCardHeader(
                title = title,
                description = description,
                icon = icon,
                count = values.size,
                limit = limit
            )

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
        }
    }
}

@Composable
private fun DohResolverAddCard(
    values: List<String>,
    limit: Int,
    onAdd: (String, String) -> Boolean
) {
    var endpointInput by rememberSaveable { mutableStateOf("") }
    var bootstrapInput by rememberSaveable { mutableStateOf("") }
    val normalizedEndpoint = SteamResolverInputValidator.normalizeDohEndpoint(endpointInput)
    val normalizedBootstrap = SteamResolverInputValidator.normalizeBootstrapAddresses(bootstrapInput)
    val endpointInvalid = endpointInput.isNotBlank() && normalizedEndpoint == null
    val bootstrapInvalid = bootstrapInput.isNotBlank() && normalizedBootstrap == null
    val canAdd = normalizedEndpoint != null &&
        normalizedEndpoint !in values &&
        normalizedBootstrap != null &&
        values.size < limit

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
            ResolverAddCardHeader(
                title = stringResource(R.string.steam_network_custom_doh_title),
                description = stringResource(R.string.steam_network_custom_doh_bootstrap_description),
                icon = Icons.Default.Public,
                count = values.size,
                limit = limit
            )

            OutlinedTextField(
                value = endpointInput,
                onValueChange = { endpointInput = it.take(512) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = endpointInvalid,
                label = { Text(stringResource(R.string.steam_network_custom_doh_url_label)) },
                placeholder = { Text(stringResource(R.string.steam_network_custom_doh_placeholder)) },
                supportingText = if (endpointInvalid) {
                    { Text(stringResource(R.string.steam_network_resolver_invalid)) }
                } else {
                    null
                }
            )

            OutlinedTextField(
                value = bootstrapInput,
                onValueChange = { bootstrapInput = it.take(512) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = bootstrapInvalid,
                label = { Text(stringResource(R.string.steam_network_custom_doh_bootstrap_label)) },
                placeholder = {
                    Text(stringResource(R.string.steam_network_custom_doh_bootstrap_placeholder))
                },
                supportingText = {
                    Text(
                        if (bootstrapInvalid) {
                            stringResource(R.string.steam_network_custom_doh_bootstrap_invalid)
                        } else {
                            stringResource(R.string.steam_network_custom_doh_bootstrap_hint)
                        }
                    )
                }
            )

            FilledTonalButton(
                onClick = {
                    if (onAdd(endpointInput, bootstrapInput)) {
                        endpointInput = ""
                        bootstrapInput = ""
                    }
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
        }
    }
}

@Composable
private fun ResolverAddCardHeader(
    title: String,
    description: String,
    icon: ImageVector,
    count: Int,
    limit: Int
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
            text = "$count/$limit",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
                text = stringResource(R.string.steam_network_dynamic_dns_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
