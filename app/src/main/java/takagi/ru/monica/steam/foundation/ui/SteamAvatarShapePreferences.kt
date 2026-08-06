package takagi.ru.monica.steam.foundation.ui

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

enum class SteamAvatarShapeOption(val storedValue: String) {
    SQUARE("square"),
    ROUNDED("rounded"),
    CIRCLE("circle");

    companion object {
        fun fromStoredValue(value: String?): SteamAvatarShapeOption {
            return entries.firstOrNull { it.storedValue == value } ?: SQUARE
        }
    }
}
private val Context.steamAvatarShapeDataStore by preferencesDataStore(
    name = "monica_steam_avatar_shape"
)

class SteamAvatarShapePreferences(context: Context) {
    private val dataStore = context.applicationContext.steamAvatarShapeDataStore

    val shape: Flow<SteamAvatarShapeOption> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            SteamAvatarShapeOption.fromStoredValue(preferences[AVATAR_SHAPE_KEY])
        }
        .distinctUntilChanged()

    suspend fun updateShape(shape: SteamAvatarShapeOption) {
        dataStore.edit { preferences ->
            preferences[AVATAR_SHAPE_KEY] = shape.storedValue
        }
    }

    private companion object {
        val AVATAR_SHAPE_KEY = stringPreferencesKey("avatar_shape")
    }
}
