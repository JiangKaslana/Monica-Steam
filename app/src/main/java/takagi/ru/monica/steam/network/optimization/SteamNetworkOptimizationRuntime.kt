package takagi.ru.monica.steam.network.optimization

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.network.SteamHttpClientProvider
import takagi.ru.monica.steam.network.optimization.domain.SteamNetworkOptimizationSettings

object SteamNetworkOptimizationRuntime {
    private const val PREFERENCES_NAME = "steam_network_optimization"
    private const val KEY_ENABLED = "enabled"

    private val mutableSettings = MutableStateFlow(SteamNetworkOptimizationSettings())
    val settings: StateFlow<SteamNetworkOptimizationSettings> = mutableSettings.asStateFlow()

    @Volatile
    private var initialized = false
    private lateinit var preferences: SharedPreferences

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        preferences = context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
        mutableSettings.value = SteamNetworkOptimizationSettings(
            enabled = preferences.getBoolean(KEY_ENABLED, false)
        )
        initialized = true
    }

    fun isEnabled(): Boolean = mutableSettings.value.enabled

    fun setEnabled(context: Context, enabled: Boolean) {
        initialize(context)
        if (mutableSettings.value.enabled == enabled) return
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
        mutableSettings.value = SteamNetworkOptimizationSettings(enabled = enabled)
        SteamHttpClientProvider.onOptimizationChanged()
        runCatching {
            SteamDiagLogger.append("network_optimization enabled=$enabled scope=app")
        }
    }

    fun clearDnsCache() {
        SteamHttpClientProvider.clearDnsCache()
        runCatching { SteamDiagLogger.append("network_optimization dns_cache_cleared") }
    }
}
