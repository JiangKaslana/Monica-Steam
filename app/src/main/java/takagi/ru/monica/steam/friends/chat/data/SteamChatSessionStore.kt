package takagi.ru.monica.steam.friends.chat.data

import android.content.Context
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.data.SteamAccountRepository
import takagi.ru.monica.steam.data.SteamDatabase

/** Persists rotated Steam session tokens so chat recovery survives the next request. */
class SteamChatSessionStore(
    private val repository: SteamAccountRepository
) {
    suspend fun persist(account: SteamAccount) {
        val accessToken = account.accessToken?.takeIf(String::isNotBlank) ?: return
        repository.updateSessionTokens(
            id = account.id,
            accessToken = accessToken,
            refreshToken = account.refreshToken,
            steamLoginSecure = account.steamLoginSecure
        )
    }

    companion object {
        fun from(context: Context): SteamChatSessionStore {
            val appContext = context.applicationContext
            val database = SteamDatabase.getDatabase(appContext)
            return SteamChatSessionStore(
                SteamAccountRepository(database.steamAccountDao(), SecurityManager(appContext))
            )
        }
    }
}
