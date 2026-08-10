package takagi.ru.monica.steam.network.optimization.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.steam.network.optimization.SteamNetworkOptimizationRuntime
import takagi.ru.monica.steam.network.optimization.diagnostics.SteamDnsOptimizationScanner
import takagi.ru.monica.steam.network.optimization.domain.SteamAutoHostsFormatter
import takagi.ru.monica.steam.network.optimization.domain.SteamAutoHostsSummary
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsOptimizationScanResult
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsProvider
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsScanProgress
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsScanStage
import takagi.ru.monica.ui.LocalReduceAnimations

@Composable
internal fun SteamNetworkOptimizationHeroCard(
    onOpenAdvanced: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scanner = remember { SteamDnsOptimizationScanner() }
    val reduceAnimations = LocalReduceAnimations.current
    val settings by SteamNetworkOptimizationRuntime.settings.collectAsState()
    val autoSummary = remember(settings.hostsText) {
        SteamAutoHostsFormatter.summary(settings.hostsText)
    }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var scanState by remember { mutableStateOf<AutoOptimizationUiState>(AutoOptimizationUiState.Idle) }

    LaunchedEffect(context) {
        SteamNetworkOptimizationRuntime.initialize(context)
    }

    fun startScan() {
        if (scanState.isBusy) return
        expanded = true
        scope.launch {
            scanState = AutoOptimizationUiState.Running(
                SteamDnsScanProgress(
                    stage = SteamDnsScanStage.RESOLVING,
                    completed = 0,
                    total = SteamDnsProvider.DEFAULTS.size *
                        SteamDnsOptimizationScanner.DEFAULT_TARGET_HOSTNAMES.size
                )
            )
            try {
                val result = scanner.scan { progress ->
                    scanState = AutoOptimizationUiState.Running(progress)
                }
                if (!result.isComplete) {
                    scanState = AutoOptimizationUiState.Error(
                        availableHostCount = result.availableHostCount,
                        totalHostCount = result.totalHostCount
                    )
                    return@launch
                }
                scanState = AutoOptimizationUiState.Applying
                scanState = if (SteamNetworkOptimizationRuntime.applyAutoOptimization(context, result)) {
                    AutoOptimizationUiState.Success(result)
                } else {
                    AutoOptimizationUiState.Error(
                        availableHostCount = result.availableHostCount,
                        totalHostCount = result.totalHostCount,
                        applyFailed = true
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                scanState = AutoOptimizationUiState.Error(
                    availableHostCount = 0,
                    totalHostCount = SteamDnsOptimizationScanner.DEFAULT_TARGET_HOSTNAMES.size
                )
            }
        }
    }

    val colors = heroColors(settings.enabled, scanState)
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(containerColor = colors.container)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraLarge)
                    .clickable { expanded = !expanded }
                    .defaultMinSize(minHeight = 88.dp)
                    .padding(start = 18.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    modifier = Modifier.size(54.dp),
                    shape = MaterialTheme.shapes.large,
                    color = colors.content.copy(alpha = 0.12f)
                ) {
                    Icon(
                        imageVector = colors.icon,
                        contentDescription = null,
                        modifier = Modifier.padding(15.dp),
                        tint = colors.content
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = stringResource(R.string.steam_network_auto_card_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.content
                    )
                    Text(
                        text = heroSubtitle(
                            enabled = settings.enabled,
                            hostCount = settings.hostCount,
                            summary = autoSummary,
                            state = scanState
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.content.copy(alpha = 0.76f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (scanState.isBusy) {
                    LoadingIndicator(
                        modifier = Modifier.size(28.dp),
                        color = colors.content
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) {
                            R.string.steam_network_auto_card_collapse
                        } else {
                            R.string.steam_network_auto_card_expand
                        }
                    ),
                    tint = colors.content
                )
            }

            if (reduceAnimations) {
                if (expanded) {
                    AutoOptimizationExpandedContent(
                        state = scanState,
                        summary = autoSummary,
                        enabled = settings.enabled,
                        contentColor = colors.content,
                        onScan = ::startScan,
                        onOpenAdvanced = onOpenAdvanced,
                        onDisable = {
                            SteamNetworkOptimizationRuntime.setEnabled(context, false)
                            scanState = AutoOptimizationUiState.Idle
                        }
                    )
                }
            } else {
                AnimatedVisibility(visible = expanded) {
                    AutoOptimizationExpandedContent(
                        state = scanState,
                        summary = autoSummary,
                        enabled = settings.enabled,
                        contentColor = colors.content,
                        onScan = ::startScan,
                        onOpenAdvanced = onOpenAdvanced,
                        onDisable = {
                            SteamNetworkOptimizationRuntime.setEnabled(context, false)
                            scanState = AutoOptimizationUiState.Idle
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AutoOptimizationExpandedContent(
    state: AutoOptimizationUiState,
    summary: SteamAutoHostsSummary?,
    enabled: Boolean,
    contentColor: Color,
    onScan: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onDisable: () -> Unit
) {
    Column {
        HorizontalDivider(color = contentColor.copy(alpha = 0.14f))
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.steam_network_auto_card_description),
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor.copy(alpha = 0.82f)
            )

            Text(
                text = stringResource(
                    R.string.steam_network_auto_sources,
                    SteamDnsProvider.DEFAULTS.size
                ),
                style = MaterialTheme.typography.labelLarge,
                color = contentColor
            )
            val selectedProviderIds = when (state) {
                is AutoOptimizationUiState.Success -> state.result.providerIds
                else -> summary?.providerIds.orEmpty()
            }.toSet()
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SteamDnsProvider.DEFAULTS.forEach { provider ->
                    ProviderPill(
                        name = provider.displayName,
                        selected = provider.id in selectedProviderIds,
                        contentColor = contentColor
                    )
                }
            }

            when (state) {
                is AutoOptimizationUiState.Running -> ScanProgressContent(
                    progress = state.progress,
                    contentColor = contentColor
                )
                AutoOptimizationUiState.Applying -> ScanProgressContent(
                    progress = null,
                    contentColor = contentColor
                )
                is AutoOptimizationUiState.Error -> ScanErrorContent(
                    state = state,
                    contentColor = contentColor
                )
                is AutoOptimizationUiState.Success -> CurrentSelectionContent(
                    selectedHostCount = state.result.availableHostCount,
                    totalHostCount = state.result.totalHostCount,
                    averageLatencyMillis = state.result.averageLatencyMillis,
                    contentColor = contentColor
                )
                AutoOptimizationUiState.Idle -> summary?.let {
                    CurrentSelectionContent(
                        selectedHostCount = it.selectedHostCount,
                        totalHostCount = it.totalHostCount,
                        averageLatencyMillis = it.averageLatencyMillis,
                        contentColor = contentColor
                    )
                }
            }

            Button(
                onClick = onScan,
                enabled = !state.isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
            ) {
                if (state.isBusy) {
                    LoadingIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(10.dp))
                } else {
                    Icon(Icons.Default.NetworkCheck, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    stringResource(
                        if (summary == null && state !is AutoOptimizationUiState.Success) {
                            R.string.steam_network_auto_scan_apply
                        } else {
                            R.string.steam_network_auto_rescan_apply
                        }
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onOpenAdvanced) {
                    Icon(Icons.Default.Tune, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.steam_network_auto_advanced))
                }
                if (enabled) {
                    TextButton(onClick = onDisable) {
                        Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.steam_network_auto_disable))
                    }
                }
            }

            Text(
                text = stringResource(R.string.steam_network_auto_privacy_scope),
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.68f)
            )
        }
    }
}

@Composable
private fun ProviderPill(
    name: String,
    selected: Boolean,
    contentColor: Color
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (selected) {
            contentColor.copy(alpha = 0.16f)
        } else {
            contentColor.copy(alpha = 0.08f)
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(
                        color = if (selected) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            contentColor.copy(alpha = 0.42f)
                        },
                        shape = MaterialTheme.shapes.extraLarge
                    )
            )
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor
            )
        }
    }
}

@Composable
private fun ScanProgressContent(
    progress: SteamDnsScanProgress?,
    contentColor: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearProgressIndicator(
            progress = { progress?.fraction ?: 1f },
            modifier = Modifier.fillMaxWidth(),
            color = contentColor,
            trackColor = contentColor.copy(alpha = 0.14f)
        )
        Text(
            text = if (progress == null) {
                stringResource(R.string.steam_network_auto_applying)
            } else {
                stringResource(
                    if (progress.stage == SteamDnsScanStage.RESOLVING) {
                        R.string.steam_network_auto_resolving_progress
                    } else {
                        R.string.steam_network_auto_verifying_progress
                    },
                    progress.completed,
                    progress.total
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = contentColor.copy(alpha = 0.78f)
        )
    }
}

@Composable
private fun ScanErrorContent(
    state: AutoOptimizationUiState.Error,
    contentColor: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.88f)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = stringResource(
                    if (state.applyFailed) {
                        R.string.steam_network_auto_apply_failed
                    } else {
                        R.string.steam_network_auto_scan_incomplete
                    },
                    state.availableHostCount,
                    state.totalHostCount
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun CurrentSelectionContent(
    selectedHostCount: Int,
    totalHostCount: Int,
    averageLatencyMillis: Long?,
    contentColor: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = contentColor.copy(alpha = 0.09f)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.steam_network_auto_current_selection),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
                Text(
                    text = if (averageLatencyMillis != null) {
                        stringResource(
                            R.string.steam_network_auto_result_summary,
                            selectedHostCount,
                            totalHostCount,
                            averageLatencyMillis
                        )
                    } else {
                        stringResource(
                            R.string.steam_network_auto_result_summary_no_latency,
                            selectedHostCount,
                            totalHostCount
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.74f)
                )
            }
        }
    }
}

@Composable
private fun heroSubtitle(
    enabled: Boolean,
    hostCount: Int,
    summary: SteamAutoHostsSummary?,
    state: AutoOptimizationUiState
): String = when (state) {
    is AutoOptimizationUiState.Running -> stringResource(
        if (state.progress.stage == SteamDnsScanStage.RESOLVING) {
            R.string.steam_network_auto_resolving
        } else {
            R.string.steam_network_auto_verifying
        }
    )
    AutoOptimizationUiState.Applying -> stringResource(R.string.steam_network_auto_applying)
    is AutoOptimizationUiState.Error -> stringResource(R.string.steam_network_auto_retry_hint)
    is AutoOptimizationUiState.Success -> stringResource(
        R.string.steam_network_auto_enabled_summary,
        state.result.availableHostCount,
        state.result.averageLatencyMillis ?: 0L
    )
    AutoOptimizationUiState.Idle -> when {
        enabled && summary != null -> stringResource(
            R.string.steam_network_auto_enabled_summary,
            summary.selectedHostCount,
            summary.averageLatencyMillis ?: 0L
        )
        enabled -> stringResource(R.string.steam_network_auto_manual_enabled, hostCount)
        hostCount > 0 -> stringResource(R.string.steam_network_auto_configured_disabled, hostCount)
        else -> stringResource(R.string.steam_network_auto_card_subtitle)
    }
}

@Composable
private fun heroColors(
    enabled: Boolean,
    state: AutoOptimizationUiState
): HeroColors = when {
    state is AutoOptimizationUiState.Error -> HeroColors(
        container = MaterialTheme.colorScheme.errorContainer,
        content = MaterialTheme.colorScheme.onErrorContainer,
        icon = Icons.Default.ErrorOutline
    )
    enabled -> HeroColors(
        container = MaterialTheme.colorScheme.primaryContainer,
        content = MaterialTheme.colorScheme.onPrimaryContainer,
        icon = Icons.Default.NetworkCheck
    )
    else -> HeroColors(
        container = MaterialTheme.colorScheme.surfaceContainerHigh,
        content = MaterialTheme.colorScheme.onSurface,
        icon = Icons.Default.Dns
    )
}

private sealed interface AutoOptimizationUiState {
    data object Idle : AutoOptimizationUiState
    data class Running(val progress: SteamDnsScanProgress) : AutoOptimizationUiState
    data object Applying : AutoOptimizationUiState
    data class Success(val result: SteamDnsOptimizationScanResult) : AutoOptimizationUiState
    data class Error(
        val availableHostCount: Int,
        val totalHostCount: Int,
        val applyFailed: Boolean = false
    ) : AutoOptimizationUiState
}

private val AutoOptimizationUiState.isBusy: Boolean
    get() = this is AutoOptimizationUiState.Running || this === AutoOptimizationUiState.Applying

private data class HeroColors(
    val container: Color,
    val content: Color,
    val icon: ImageVector
)
