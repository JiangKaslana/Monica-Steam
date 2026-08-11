package takagi.ru.monica.steam.network.optimization.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.steam.network.optimization.diagnostics.SteamResolverBenchmark
import takagi.ru.monica.steam.network.optimization.diagnostics.SteamResolverBenchmarkResult
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsProvider

@Composable
internal fun SteamResolverServerBenchmarkCard(
    providers: List<SteamDnsProvider>,
    enabledProviderIds: Set<String>,
    onBuiltInProviderEnabledChange: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val benchmark = remember { SteamResolverBenchmark() }
    var results by remember { mutableStateOf<Map<String, SteamResolverBenchmarkResult>>(emptyMap()) }
    var runningIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun benchmarkOne(provider: SteamDnsProvider) {
        if (provider.id in runningIds) return
        runningIds = runningIds + provider.id
        scope.launch {
            try {
                val result = benchmark.benchmark(provider)
                results = results + (provider.id to result)
            } finally {
                runningIds = runningIds - provider.id
            }
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.steam_network_resolver_servers_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.steam_network_resolver_benchmark_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalButton(
                    enabled = providers.isNotEmpty() && runningIds.isEmpty(),
                    onClick = {
                        if (providers.isNotEmpty() && runningIds.isEmpty()) {
                            val ids = providers.map(SteamDnsProvider::id).toSet()
                            runningIds = ids
                            scope.launch {
                                try {
                                    val measured = providers.map { provider ->
                                        async { provider.id to benchmark.benchmark(provider) }
                                    }.awaitAll().toMap()
                                    results = results + measured
                                } finally {
                                    runningIds = emptySet()
                                }
                            }
                        }
                    }
                ) {
                    Icon(Icons.Default.NetworkCheck, contentDescription = null)
                    Text(
                        text = stringResource(R.string.steam_network_resolver_benchmark_all),
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }

            providers.forEachIndexed { index, provider ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 18.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                    )
                }
                val isBuiltInDoh = provider.isDoh && SteamDnsProvider.DEFAULTS.any {
                    !it.isSystem && it.id == provider.id
                }
                ResolverBenchmarkRow(
                    provider = provider,
                    result = results[provider.id],
                    running = provider.id in runningIds,
                    enabled = provider.id in enabledProviderIds,
                    canToggle = isBuiltInDoh,
                    onEnabledChange = { enabled ->
                        onBuiltInProviderEnabledChange(provider.id, enabled)
                    },
                    onBenchmark = { benchmarkOne(provider) }
                )
            }
        }
    }
}

@Composable
private fun ResolverBenchmarkRow(
    provider: SteamDnsProvider,
    result: SteamResolverBenchmarkResult?,
    running: Boolean,
    enabled: Boolean,
    canToggle: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onBenchmark: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = provider.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = if (enabled || !canToggle) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = provider.dohUrl
                    ?: provider.udpServer
                    ?: stringResource(R.string.steam_network_resolver_system_endpoint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = when {
                    running -> stringResource(R.string.steam_network_resolver_benchmark_running)
                    result == null -> "—"
                    !result.isAvailable -> stringResource(
                        R.string.steam_network_resolver_benchmark_unavailable
                    )
                    result.successfulHosts == result.totalHosts &&
                        result.averageLatencyMillis != null -> stringResource(
                        R.string.steam_network_resolver_benchmark_latency,
                        result.averageLatencyMillis
                    )
                    result.averageLatencyMillis != null -> stringResource(
                        R.string.steam_network_resolver_benchmark_partial,
                        result.successfulHosts,
                        result.totalHosts,
                        result.averageLatencyMillis
                    )
                    else -> stringResource(R.string.steam_network_resolver_benchmark_unavailable)
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (result?.isAvailable == true) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            TextButton(
                onClick = onBenchmark,
                enabled = !running
            ) {
                Text(stringResource(R.string.steam_network_resolver_benchmark_one))
            }
        }
        if (canToggle) {
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange
            )
        }
    }
}
