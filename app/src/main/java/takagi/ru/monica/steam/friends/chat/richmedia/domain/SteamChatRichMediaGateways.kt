package takagi.ru.monica.steam.friends.chat.richmedia.domain

import takagi.ru.monica.steam.data.SteamAccount

fun interface SteamChatCatalogGateway {
    fun loadCatalog(account: SteamAccount): SteamChatRichMediaCatalog
}

interface SteamChatAttachmentGateway {
    suspend fun inspect(rawUri: String): SteamChatPendingAttachment

    suspend fun upload(
        account: SteamAccount,
        partnerSteamId: String,
        attachment: SteamChatPendingAttachment,
        spoiler: Boolean,
        onProgress: (Float) -> Unit = {}
    ): SteamChatUploadedAttachment
}
