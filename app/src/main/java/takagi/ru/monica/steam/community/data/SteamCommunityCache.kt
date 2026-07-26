package takagi.ru.monica.steam.community.data

import android.content.Context
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import takagi.ru.monica.steam.community.domain.SteamCommunitySnapshot

interface SteamCommunityCache {
    fun load(accountSteamId: String): SteamCommunitySnapshot?
    fun save(snapshot: SteamCommunitySnapshot)
}

internal interface SteamCommunityKeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

class SteamCommunityPreferencesCache internal constructor(
    private val store: SteamCommunityKeyValueStore
) : SteamCommunityCache {
    constructor(context: Context) : this(
        SteamCommunityPreferencesStore(context.applicationContext)
    )

    override fun load(accountSteamId: String): SteamCommunitySnapshot? =
        store.get(key(accountSteamId))
            ?.let(SteamCommunityCacheCodec::decode)
            ?.takeIf { it.accountSteamId == accountSteamId }

    override fun save(snapshot: SteamCommunitySnapshot) {
        store.put(key(snapshot.accountSteamId), SteamCommunityCacheCodec.encode(snapshot))
    }

    private fun key(value: String): String = "community_" + MessageDigest.getInstance("SHA-256")
        .digest(value.trim().toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

private class SteamCommunityPreferencesStore(context: Context) : SteamCommunityKeyValueStore {
    private val preferences = context.getSharedPreferences(
        "steam_community_cache", Context.MODE_PRIVATE
    )

    override fun get(key: String): String? = preferences.getString(key, null)

    override fun put(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }
}

internal object SteamCommunityCacheCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun encode(snapshot: SteamCommunitySnapshot) =
        json.encodeToString(SteamCommunitySnapshot.serializer(), snapshot)
    fun decode(raw: String): SteamCommunitySnapshot? = runCatching {
        json.decodeFromString(SteamCommunitySnapshot.serializer(), raw)
    }.getOrNull()
}
