package takagi.ru.monica.steam.navigation

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.abs

enum class SteamDockTab {
    TOKEN,
    LIBRARY,
    STORE,
    CHAT,
    SETTINGS;

    companion object {
        val DEFAULT_ORDER: List<SteamDockTab> = listOf(STORE, LIBRARY, CHAT, SETTINGS)

        fun sanitizeOrder(order: List<SteamDockTab>): List<SteamDockTab> {
            return order.distinct().filter { it in DEFAULT_ORDER }
        }

        fun completeOrder(order: List<SteamDockTab>): List<SteamDockTab> {
            val result = sanitizeOrder(order).toMutableList()
            DEFAULT_ORDER.forEach { tab -> if (tab !in result) result += tab }
            return result
        }
    }
}

private val LEGACY_DEFAULT_DOCK_ORDER = listOf(
    SteamDockTab.LIBRARY,
    SteamDockTab.STORE,
    SteamDockTab.SETTINGS
)

/** Keeps custom orders while migrating the order used by pre-swipe builds. */
internal fun resolveStoredDockOrder(
    stored: List<SteamDockTab>,
    chatMigrationComplete: Boolean = false
): List<SteamDockTab> {
    val sanitized = SteamDockTab.sanitizeOrder(stored)
    val normalized = if (sanitized == LEGACY_DEFAULT_DOCK_ORDER) {
        listOf(SteamDockTab.STORE, SteamDockTab.LIBRARY, SteamDockTab.SETTINGS)
    } else {
        sanitized
    }
    if (chatMigrationComplete || SteamDockTab.CHAT in normalized) return normalized
    val settingsIndex = normalized.indexOf(SteamDockTab.SETTINGS)
    return normalized.toMutableList().apply {
        add(if (settingsIndex >= 0) settingsIndex else size, SteamDockTab.CHAT)
    }
}

/**
 * Resolves a horizontal swipe made on the Dock to the adjacent content tab.
 * The token action is intentionally kept outside the sortable order; when it
 * is selected, a swipe enters from the corresponding edge of the content
 * Dock.  Returning null keeps short/ambiguous drags inert.
 */
internal fun dockSwipeTarget(
    order: List<SteamDockTab>,
    selected: SteamDockTab,
    totalDragPx: Float,
    thresholdPx: Float
): SteamDockTab? {
    if (thresholdPx <= 0f || abs(totalDragPx) < thresholdPx) return null
    val tabs = SteamDockTab.sanitizeOrder(order)
        .filterNot { it == SteamDockTab.TOKEN }
    if (tabs.isEmpty()) return null

    val selectedIndex = tabs.indexOf(selected)
    val targetIndex = when {
        selectedIndex < 0 && totalDragPx < 0f -> 0
        selectedIndex < 0 -> tabs.lastIndex
        totalDragPx < 0f -> selectedIndex + 1
        else -> selectedIndex - 1
    }
    return tabs.getOrNull(targetIndex)
}

internal fun reorderDockOrder(
    order: List<SteamDockTab>,
    fromIndex: Int,
    toIndex: Int
): List<SteamDockTab> {
    val sanitized = SteamDockTab.sanitizeOrder(order)
    if (fromIndex !in sanitized.indices || toIndex !in sanitized.indices) return sanitized
    if (fromIndex == toIndex) return sanitized
    return sanitized.toMutableList().apply {
        add(toIndex, removeAt(fromIndex))
    }
}

private val Context.steamDockDataStore by preferencesDataStore(name = "monica_steam_dock")

class SteamDockPreferences(context: Context) {
    private val dataStore = context.applicationContext.steamDockDataStore

    val order: Flow<List<SteamDockTab>> = dataStore.data.map { preferences ->
        val storedValue = preferences[ORDER_KEY]
        if (storedValue == null) return@map SteamDockTab.DEFAULT_ORDER
        val parsed = storedValue
            ?.split(',')
            ?.mapNotNull { value -> runCatching { SteamDockTab.valueOf(value) }.getOrNull() }
            .orEmpty()
        resolveStoredDockOrder(
            stored = parsed,
            chatMigrationComplete = preferences[CHAT_MIGRATION_KEY] == true
        )
    }

    suspend fun updateOrder(order: List<SteamDockTab>) {
        val sanitized = SteamDockTab.sanitizeOrder(order)
        dataStore.edit { preferences ->
            preferences[ORDER_KEY] = sanitized.joinToString(",") { it.name }
            preferences[CHAT_MIGRATION_KEY] = true
        }
    }

    private companion object {
        val ORDER_KEY = stringPreferencesKey("dock_order")
        val CHAT_MIGRATION_KEY = booleanPreferencesKey("chat_tab_migrated")
    }
}
