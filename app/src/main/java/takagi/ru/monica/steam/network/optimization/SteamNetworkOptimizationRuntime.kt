package takagi.ru.monica.steam.network.optimization

import android.content.Context
import android.content.SharedPreferences
import java.net.InetAddress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.network.SteamHttpClientProvider
import takagi.ru.monica.steam.network.optimization.domain.SteamHostsParseResult
import takagi.ru.monica.steam.network.optimization.domain.SteamHostsRuleParser
import takagi.ru.monica.steam.network.optimization.domain.SteamNetworkOptimizationSettings

object SteamNetworkOptimizationRuntime {
    private const val PREFERENCES_NAME = "steam_network_optimization"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_CUSTOM_HOSTS = "custom_hosts"

    private val mutableSettings = MutableStateFlow(SteamNetworkOptimizationSettings())
    val settings: StateFlow<SteamNetworkOptimizationSettings> = mutableSettings.asStateFlow()

    @Volatile
    private var initialized = false
    @Volatile
    private var hostOverrides: Map<String, List<InetAddress>> = emptyMap()
    private lateinit var preferences: SharedPreferences

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        preferences = context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
        val hostsText = preferences.getString(KEY_CUSTOM_HOSTS, "").orEmpty()
        val parsed = SteamHostsRuleParser.parse(hostsText)
        hostOverrides = parsed.addresses
        val persistedEnabled = preferences.getBoolean(KEY_ENABLED, false)
        val enabled = persistedEnabled && parsed.isValid && parsed.addresses.isNotEmpty()
        if (persistedEnabled != enabled) {
            preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
        }
        mutableSettings.value = SteamNetworkOptimizationSettings(
            enabled = enabled,
            hostsText = hostsText,
            hostCount = parsed.hostCount
        )
        initialized = true
    }

    @Synchronized
    fun setEnabled(context: Context, enabled: Boolean) {
        initialize(context)
        val acceptedEnabled = enabled && hostOverrides.isNotEmpty()
        if (mutableSettings.value.enabled == acceptedEnabled) return
        preferences.edit().putBoolean(KEY_ENABLED, acceptedEnabled).apply()
        mutableSettings.value = mutableSettings.value.copy(enabled = acceptedEnabled)
        SteamHttpClientProvider.onCustomHostsChanged()
        runCatching {
            SteamDiagLogger.append(
                "custom_hosts enabled=$acceptedEnabled hosts=${hostOverrides.size} scope=app"
            )
        }
    }

    @Synchronized
    fun saveHosts(context: Context, hostsText: String): SteamHostsParseResult {
        initialize(context)
        val parsed = SteamHostsRuleParser.parse(hostsText)
        if (!parsed.isValid) return parsed

        val enabled = mutableSettings.value.enabled && parsed.addresses.isNotEmpty()
        preferences.edit()
            .putString(KEY_CUSTOM_HOSTS, hostsText)
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
        hostOverrides = parsed.addresses
        mutableSettings.value = SteamNetworkOptimizationSettings(
            enabled = enabled,
            hostsText = hostsText,
            hostCount = parsed.hostCount
        )
        SteamHttpClientProvider.onCustomHostsChanged()
        runCatching {
            SteamDiagLogger.append("custom_hosts saved hosts=${parsed.hostCount} enabled=$enabled")
        }
        return parsed
    }

    internal fun addressesForHost(hostname: String): List<InetAddress> {
        if (!mutableSettings.value.enabled) return emptyList()
        return hostOverrides[SteamHostsRuleParser.normalizeHostname(hostname)].orEmpty()
    }
}
