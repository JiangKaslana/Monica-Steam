package takagi.ru.monica.steam.network.optimization.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.steam.navigation.ui.LocalSteamDockContentClearance
import takagi.ru.monica.steam.network.optimization.SteamNetworkOptimizationRuntime
import takagi.ru.monica.steam.network.optimization.diagnostics.SteamDnsOptimizationScanner
import takagi.ru.monica.steam.network.optimization.domain.SteamAutoHostsFormatter
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsProvider
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsScanProgress
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsScanStage
import takagi.ru.monica.steam.network.optimization.ui.components.SteamNetworkAdvancedSettingsCard
import takagi.ru.monica.steam.network.optimization.ui.components.SteamNetworkAutomaticScanCard
import takagi.ru.monica.steam.network.optimization.ui.components.SteamNetworkCurrentSelectionCard
import takagi.ru.monica.steam.network.optimization.ui.components.SteamNetworkResolverSourcesSection
import takagi.ru.monica.steam.network.optimization.ui.components.SteamNetworkScopeCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SteamNetworkOptimizationAutoScreen(
    onNavigateBack: () -> Unit,
    onOpenAdvanced: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scanner = remember { SteamDnsOptimizationScanner() }
    val dockClearance = LocalSteamDockContentClearance.current
    val settings by SteamNetworkOptimizationRuntime.settings.collectAsState()
    val summary = remember(settings.hostsText) {
        SteamAutoHostsFormatter.summary(settings.hostsText)
    }
    var scanState by remember {
        mutableStateOf<SteamAutoOptimizationUiState>(SteamAutoOptimizationUiState.Idle)
    }

    LaunchedEffect(context) {
        SteamNetworkOptimizationRuntime.initialize(context)
    }

    fun startScan() {
        if (scanState.isBusy) return
        scope.launch {
            scanState = SteamAutoOptimizationUiState.Running(
                SteamDnsScanProgress(
                    stage = SteamDnsScanStage.RESOLVING,
                    completed = 0,
                    total = SteamDnsProvider.DEFAULTS.size *
                        SteamDnsOptimizationScanner.DEFAULT_TARGET_HOSTNAMES.size
                )
            )
            try {
                val result = scanner.scan { progress ->
                    scanState = SteamAutoOptimizationUiState.Running(progress)
                }
                if (!result.isComplete) {
                    scanState = SteamAutoOptimizationUiState.Error(
                        availableHostCount = result.availableHostCount,
                        totalHostCount = result.totalHostCount
                    )
                    return@launch
                }
                scanState = SteamAutoOptimizationUiState.Applying
                scanState = if (
                    SteamNetworkOptimizationRuntime.applyAutoOptimization(context, result)
                ) {
                    SteamAutoOptimizationUiState.Success(result)
                } else {
                    SteamAutoOptimizationUiState.Error(
                        availableHostCount = result.availableHostCount,
                        totalHostCount = result.totalHostCount,
                        applyFailed = true
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                scanState = SteamAutoOptimizationUiState.Error(
                    availableHostCount = 0,
                    totalHostCount = SteamDnsOptimizationScanner.DEFAULT_TARGET_HOSTNAMES.size
                )
            }
        }
    }

    val selectedProviderIds = when (val state = scanState) {
        is SteamAutoOptimizationUiState.Success -> state.result.providerIds
        else -> summary?.providerIds.orEmpty()
    }.toSet()
    val selectedRoutes = (scanState as? SteamAutoOptimizationUiState.Success)
        ?.result
        ?.selectedRoutes
        .orEmpty()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.steam_network_auto_card_title)) },
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 12.dp,
                end = 16.dp,
                bottom = dockClearance + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "automatic_scan") {
                SteamNetworkAutomaticScanCard(
                    state = scanState,
                    summary = summary,
                    enabled = settings.enabled,
                    onScan = ::startScan,
                    onDisable = {
                        SteamNetworkOptimizationRuntime.setEnabled(context, false)
                        scanState = SteamAutoOptimizationUiState.Idle
                    }
                )
            }
            item(key = "resolver_sources") {
                SteamNetworkResolverSourcesSection(
                    selectedProviderIds = selectedProviderIds
                )
            }
            item(key = "current_selection") {
                SteamNetworkCurrentSelectionCard(
                    summary = summary,
                    routes = selectedRoutes
                )
            }
            item(key = "advanced_settings") {
                SteamNetworkAdvancedSettingsCard(onClick = onOpenAdvanced)
            }
            item(key = "scope") {
                SteamNetworkScopeCard()
            }
        }
    }
}
