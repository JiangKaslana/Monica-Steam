package takagi.ru.monica.steam.network.optimization.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import takagi.ru.monica.steam.network.optimization.diagnostics.SteamDnsOptimizationScanner
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsOptimizationScanResult
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsProvider
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsScanProgress
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsScanStage

internal class SteamNetworkOptimizationViewModel(
    private val scan: suspend ((SteamDnsScanProgress) -> Unit) ->
        SteamDnsOptimizationScanResult = createDefaultNetworkScan()
) : ViewModel() {
    private val mutableScanState = MutableStateFlow<SteamAutoOptimizationUiState>(
        SteamAutoOptimizationUiState.Idle
    )
    val scanState: StateFlow<SteamAutoOptimizationUiState> = mutableScanState.asStateFlow()

    private var scanJob: Job? = null

    fun startScan(
        applyOptimization: (SteamDnsOptimizationScanResult) -> Boolean
    ) {
        if (scanJob?.isActive == true || mutableScanState.value.isBusy) return
        scanJob = viewModelScope.launch {
            mutableScanState.value = SteamAutoOptimizationUiState.Running(
                SteamDnsScanProgress(
                    stage = SteamDnsScanStage.RESOLVING,
                    completed = 0,
                    total = SteamDnsProvider.DEFAULTS.size *
                        SteamDnsOptimizationScanner.DEFAULT_TARGET_HOSTNAMES.size
                )
            )
            try {
                val result = scan { progress ->
                    mutableScanState.value = SteamAutoOptimizationUiState.Running(progress)
                }
                if (!result.isApplicable) {
                    mutableScanState.value = SteamAutoOptimizationUiState.Error(
                        availableHostCount = result.availableHostCount,
                        totalHostCount = result.totalHostCount
                    )
                    return@launch
                }
                mutableScanState.value = SteamAutoOptimizationUiState.Applying
                mutableScanState.value = if (applyOptimization(result)) {
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
                mutableScanState.value = SteamAutoOptimizationUiState.Error(
                    availableHostCount = 0,
                    totalHostCount = SteamDnsOptimizationScanner.DEFAULT_TARGET_HOSTNAMES.size
                )
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        mutableScanState.value = SteamAutoOptimizationUiState.Idle
    }
}

private fun createDefaultNetworkScan(): suspend ((SteamDnsScanProgress) -> Unit) ->
    SteamDnsOptimizationScanResult {
    val scanner = SteamDnsOptimizationScanner()
    return { onProgress -> scanner.scan(onProgress) }
}
