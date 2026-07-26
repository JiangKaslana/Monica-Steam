package takagi.ru.monica.steam.friends.chat.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import takagi.ru.monica.steam.friends.chat.data.SteamChatPreferencesCache
import takagi.ru.monica.steam.friends.chat.data.SteamChatRoomOutbox
import takagi.ru.monica.steam.friends.chat.data.SteamChatSessionStore
import takagi.ru.monica.steam.friends.chat.data.SteamFriendChatService

internal object SteamChatViewModelFactory {
    fun create(context: Context): ViewModelProvider.Factory {
        val appContext = context.applicationContext
        val sessionStore = lazy { SteamChatSessionStore.from(appContext) }
        return object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SteamChatViewModel(
                    gateway = SteamFriendChatService(),
                    cache = SteamChatPreferencesCache(appContext),
                    persistSession = { account -> sessionStore.value.persist(account) },
                    outbox = SteamChatRoomOutbox.from(appContext)
                ) as T
        }
    }
}
