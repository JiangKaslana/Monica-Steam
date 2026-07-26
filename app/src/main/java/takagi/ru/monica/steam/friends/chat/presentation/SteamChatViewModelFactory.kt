package takagi.ru.monica.steam.friends.chat.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.data.SteamAccountSourceRepository
import takagi.ru.monica.steam.friends.chat.data.SteamChatPreferencesCache
import takagi.ru.monica.steam.friends.chat.data.SteamChatRoomOutbox
import takagi.ru.monica.steam.friends.chat.data.SteamChatSessionStore
import takagi.ru.monica.steam.friends.chat.data.SteamFriendChatService
import takagi.ru.monica.steam.friends.chat.data.SteamFriendChatRealtimeService
import takagi.ru.monica.steam.network.cm.SteamCmClient
import takagi.ru.monica.steam.network.cm.steamCmAccountKey

internal object SteamChatViewModelFactory {
    fun create(context: Context): ViewModelProvider.Factory {
        val appContext = context.applicationContext
        val sessionStore = lazy { SteamChatSessionStore.from(appContext) }
        val accountSourceRepository = SteamAccountSourceRepository.get(appContext)
        val accountKeyResolver = { account: SteamAccount ->
            accountSourceRepository.sessionHandle(account)?.stableKey
                ?: steamCmAccountKey(account)
        }
        val cm = SteamCmClient(accountKeyResolver)
        return object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SteamChatViewModel(
                    gateway = SteamFriendChatService(cm = cm),
                    cache = SteamChatPreferencesCache(appContext),
                    persistSession = { account -> sessionStore.value.persist(account) },
                    outbox = SteamChatRoomOutbox.from(appContext),
                    realtime = SteamFriendChatRealtimeService(cm),
                    accountKeyResolver = accountKeyResolver
                ) as T
        }
    }
}
