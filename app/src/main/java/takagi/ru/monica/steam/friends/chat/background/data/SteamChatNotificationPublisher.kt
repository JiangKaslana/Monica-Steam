package takagi.ru.monica.steam.friends.chat.background.data

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import takagi.ru.monica.MonicaSteamActivity
import takagi.ru.monica.R
import takagi.ru.monica.steam.friends.chat.background.domain.SteamChatNotificationPreview
import takagi.ru.monica.steam.friends.chat.background.domain.SteamChatNotificationPreviewKind
import takagi.ru.monica.steam.friends.chat.domain.SteamChatMessage
import takagi.ru.monica.steam.friends.data.SteamFriendsPreferencesCache
import takagi.ru.monica.steam.session.domain.SteamAccountSessionHandle

internal enum class SteamChatBackgroundConnectionState {
    WAITING_FOR_ACCOUNT,
    CONNECTING,
    CONNECTED,
    RECONNECTING
}
internal class SteamChatNotificationPublisher(context: Context) {
    private val appContext = context.applicationContext
    private val notificationManager = NotificationManagerCompat.from(appContext)
    private val friendsCache = SteamFriendsPreferencesCache(appContext)

    fun canPostMessageNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    fun foregroundNotification(
        handle: SteamAccountSessionHandle?,
        state: SteamChatBackgroundConnectionState
    ): Notification {
        createChannels()
        val accountName = handle?.account?.displayName
            ?.ifBlank { handle.account.accountName }
            ?.ifBlank { handle.account.steamId }
        val text = when (state) {
            SteamChatBackgroundConnectionState.WAITING_FOR_ACCOUNT ->
                appContext.getString(R.string.steam_chat_background_waiting)
            SteamChatBackgroundConnectionState.CONNECTING ->
                appContext.getString(R.string.steam_chat_background_connecting, accountName.orEmpty())
            SteamChatBackgroundConnectionState.CONNECTED ->
                appContext.getString(R.string.steam_chat_background_connected, accountName.orEmpty())
            SteamChatBackgroundConnectionState.RECONNECTING ->
                appContext.getString(R.string.steam_chat_background_reconnecting, accountName.orEmpty())
        }
        return NotificationCompat.Builder(appContext, SERVICE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_steam_chat_notification)
            .setContentTitle(appContext.getString(R.string.steam_chat_background_service_title))
            .setContentText(text)
            .setContentIntent(handle?.let { chatListPendingIntent(it) } ?: launcherPendingIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
    }

    fun updateForeground(
        handle: SteamAccountSessionHandle?,
        state: SteamChatBackgroundConnectionState
    ) {
        notificationManager.notify(
            SERVICE_NOTIFICATION_ID,
            foregroundNotification(handle, state)
        )
    }

    fun publishIncomingMessage(
        handle: SteamAccountSessionHandle,
        message: SteamChatMessage,
        preview: SteamChatNotificationPreview
    ): Boolean {
        if (!canPostMessageNotifications()) return false
        createChannels()
        val accountName = handle.account.displayName
            .ifBlank { handle.account.accountName }
            .ifBlank { handle.account.steamId }
        val friendName = friendsCache.load(handle.account.steamId)
            ?.friends
            ?.firstOrNull { friend -> friend.steamId == message.partnerSteamId }
            ?.displayName
            .orEmpty()
            .ifBlank { message.partnerSteamId }
        val previewText = preview.displayText(appContext)
        val accountPerson = Person.Builder().setName(accountName).build()
        val friendPerson = Person.Builder()
            .setName(friendName)
            .setKey(message.partnerSteamId)
            .build()
        val timestampMillis = message.timestamp
            .coerceAtMost(Long.MAX_VALUE / 1_000L) * 1_000L
        val publicVersion = NotificationCompat.Builder(appContext, MESSAGE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_steam_chat_notification)
            .setContentTitle(appContext.getString(R.string.steam_chat_notification_public_title))
            .setContentText(appContext.getString(R.string.steam_chat_notification_public_text))
            .build()
        val notification = NotificationCompat.Builder(appContext, MESSAGE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_steam_chat_notification)
            .setContentTitle(friendName)
            .setContentText(previewText)
            .setSubText(accountName)
            .setStyle(
                NotificationCompat.MessagingStyle(accountPerson)
                    .addMessage(previewText, timestampMillis, friendPerson)
            )
            .setContentIntent(
                SteamChatNotificationContract.openConversationPendingIntent(
                    appContext,
                    handle,
                    message.partnerSteamId
                )
            )
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setGroup("steam_chat_${handle.stableKey.hashCode()}")
            .build()
        return runCatching {
            notificationManager.notify(
                messageNotificationId(handle.stableKey, message.partnerSteamId),
                notification
            )
        }.isSuccess
    }

    fun cancelForeground() {
        notificationManager.cancel(SERVICE_NOTIFICATION_ID)
    }

    private fun chatListPendingIntent(handle: SteamAccountSessionHandle): PendingIntent =
        SteamChatNotificationContract.openChatListPendingIntent(appContext, handle)

    private fun launcherPendingIntent(): PendingIntent = PendingIntent.getActivity(
        appContext,
        SERVICE_NOTIFICATION_ID,
        Intent(appContext, MonicaSteamActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                SERVICE_CHANNEL_ID,
                appContext.getString(R.string.steam_chat_background_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = appContext.getString(R.string.steam_chat_background_channel_description)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                setShowBadge(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                MESSAGE_CHANNEL_ID,
                appContext.getString(R.string.steam_chat_message_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = appContext.getString(R.string.steam_chat_message_channel_description)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
        )
    }

    private fun SteamChatNotificationPreview.displayText(context: Context): String = when (kind) {
        SteamChatNotificationPreviewKind.TEXT -> text
        SteamChatNotificationPreviewKind.STICKER -> context.getString(
            R.string.steam_chat_notification_sticker,
            text
        )
        SteamChatNotificationPreviewKind.IMAGE -> context.getString(
            R.string.steam_chat_notification_image,
            text
        )
        SteamChatNotificationPreviewKind.VIDEO -> context.getString(
            R.string.steam_chat_notification_video,
            text
        )
        SteamChatNotificationPreviewKind.FILE -> context.getString(
            R.string.steam_chat_notification_file,
            text
        )
        SteamChatNotificationPreviewKind.GAME_INVITE -> text.ifBlank {
            context.getString(R.string.steam_chat_notification_game_invite)
        }
        SteamChatNotificationPreviewKind.STEAM_EVENT -> text.ifBlank {
            context.getString(R.string.steam_chat_notification_steam_event)
        }
    }

    private fun messageNotificationId(accountKey: String, partnerSteamId: String): Int =
        MESSAGE_NOTIFICATION_ID_BASE +
            "$accountKey|$partnerSteamId".hashCode().and(MESSAGE_NOTIFICATION_ID_MASK)

    companion object {
        const val SERVICE_NOTIFICATION_ID = 887_001
        private const val MESSAGE_NOTIFICATION_ID_BASE = 888_000
        private const val MESSAGE_NOTIFICATION_ID_MASK = 0x7fff
        private const val SERVICE_CHANNEL_ID = "steam_chat_background_service"
        private const val MESSAGE_CHANNEL_ID = "steam_chat_messages"
    }
}
