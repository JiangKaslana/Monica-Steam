package takagi.ru.monica.steam.network.optimization

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import takagi.ru.monica.steam.network.optimization.domain.SteamNetworkResolverSettings
import takagi.ru.monica.steam.network.optimization.domain.SteamResolverInputValidator

object SteamNetworkResolverSettingsRuntime {
    private const val PREFERENCES_NAME = "steam_network_optimization"
    private const val KEY_USE_SYSTEM_DNS = "resolver_use_system_dns"
    private const val KEY_USE_BUILT_IN_DOH = "resolver_use_built_in_doh"
    private const val KEY_CUSTOM_DNS = "resolver_custom_dns"
    private const val KEY_CUSTOM_DOH = "resolver_custom_doh"

    private val mutableSettings = MutableStateFlow(SteamNetworkResolverSettings())
    val settings: StateFlow<SteamNetworkResolverSettings> = mutableSettings.asStateFlow()

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
        mutableSettings.value = SteamNetworkResolverSettings(
            useSystemDns = preferences.getBoolean(KEY_USE_SYSTEM_DNS, true),
            useBuiltInDoh = preferences.getBoolean(KEY_USE_BUILT_IN_DOH, true),
            customDnsServers = preferences.getStringSet(KEY_CUSTOM_DNS, emptySet())
                .orEmpty()
                .mapNotNull(SteamResolverInputValidator::normalizeDnsServer)
                .distinct()
                .sorted(),
            customDohEndpoints = preferences.getStringSet(KEY_CUSTOM_DOH, emptySet())
                .orEmpty()
                .mapNotNull(SteamResolverInputValidator::normalizeDohEndpoint)
                .distinct()
                .sorted()
        )
        initialized = true
    }

    @Synchronized
    fun setUseSystemDns(context: Context, enabled: Boolean) {
        initialize(context)
        preferences.edit().putBoolean(KEY_USE_SYSTEM_DNS, enabled).apply()
        mutableSettings.value = mutableSettings.value.copy(useSystemDns = enabled)
    }

    @Synchronized
    fun setUseBuiltInDoh(context: Context, enabled: Boolean) {
        initialize(context)
        preferences.edit().putBoolean(KEY_USE_BUILT_IN_DOH, enabled).apply()
        mutableSettings.value = mutableSettings.value.copy(useBuiltInDoh = enabled)
    }

    @Synchronized
    fun addCustomDns(context: Context, raw: String): Boolean {
        initialize(context)
        val value = SteamResolverInputValidator.normalizeDnsServer(raw) ?: return false
        val current = mutableSettings.value.customDnsServers
        if (value in current || current.size >= SteamNetworkResolverSettings.MAX_CUSTOM_DNS) {
            return false
        }
        val updated = (current + value).distinct().sorted()
        saveStringSet(KEY_CUSTOM_DNS, updated)
        mutableSettings.value = mutableSettings.value.copy(customDnsServers = updated)
        return true
    }

    @Synchronized
    fun removeCustomDns(context: Context, value: String) {
        initialize(context)
        val updated = mutableSettings.value.customDnsServers - value
        saveStringSet(KEY_CUSTOM_DNS, updated)
        mutableSettings.value = mutableSettings.value.copy(customDnsServers = updated)
    }

    @Synchronized
    fun addCustomDoh(context: Context, raw: String): Boolean {
        initialize(context)
        val value = SteamResolverInputValidator.normalizeDohEndpoint(raw) ?: return false
        val current = mutableSettings.value.customDohEndpoints
        if (value in current || current.size >= SteamNetworkResolverSettings.MAX_CUSTOM_DOH) {
            return false
        }
        val updated = (current + value).distinct().sorted()
        saveStringSet(KEY_CUSTOM_DOH, updated)
        mutableSettings.value = mutableSettings.value.copy(customDohEndpoints = updated)
        return true
    }

    @Synchronized
    fun removeCustomDoh(context: Context, value: String) {
        initialize(context)
        val updated = mutableSettings.value.customDohEndpoints - value
        saveStringSet(KEY_CUSTOM_DOH, updated)
        mutableSettings.value = mutableSettings.value.copy(customDohEndpoints = updated)
    }

    private fun saveStringSet(key: String, values: Collection<String>) {
        preferences.edit().putStringSet(key, values.toSet()).apply()
    }
}
