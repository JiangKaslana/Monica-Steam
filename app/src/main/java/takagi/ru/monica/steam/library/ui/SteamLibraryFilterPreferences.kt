package takagi.ru.monica.steam.library.ui

import android.content.Context

internal class SteamLibraryFilterPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun load(): SteamLibraryGameFilter {
        val saved = preferences.getString(KEY_FILTER, null)
        return SteamLibraryGameFilter.entries.firstOrNull { it.name == saved }
            ?: SteamLibraryGameFilter.ALL
    }

    fun save(filter: SteamLibraryGameFilter) {
        preferences.edit().putString(KEY_FILTER, filter.name).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "steam_library_preferences"
        const val KEY_FILTER = "game_filter"
    }
}
