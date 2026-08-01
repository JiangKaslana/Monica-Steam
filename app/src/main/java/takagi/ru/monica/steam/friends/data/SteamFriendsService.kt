package takagi.ru.monica.steam.friends.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.friends.domain.SteamFriendActionResult
import takagi.ru.monica.steam.friends.domain.SteamFriendRelationshipAction
import takagi.ru.monica.steam.friends.domain.SteamFriendsGateway
import takagi.ru.monica.steam.friends.domain.SteamFriendsSnapshot
import takagi.ru.monica.steam.friends.nickname.data.SteamFriendNicknameService
import takagi.ru.monica.steam.friends.nickname.domain.SteamFriendNicknameGateway
import takagi.ru.monica.steam.market.SteamInventoryService
import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.network.cm.SteamCmClient
import takagi.ru.monica.steam.network.cm.SteamCmGateway
import takagi.ru.monica.steam.network.cm.SteamCmProtocol

class SteamFriendsService(
    private val api: SteamApiClient = SteamApiClient(),
    private val cm: SteamCmGateway = SteamCmClient(),
    private val nicknameGateway: SteamFriendNicknameGateway = SteamFriendNicknameService(cm)
) : SteamFriendsGateway {
    override fun fetch(account: SteamAccount, fetchedAt: Long): SteamFriendsSnapshot {
        require(account.hasRealSteamId) { "real Steam ID required" }
        val accessToken = account.accessToken?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("Steam access token required")
        val relationshipsPayload = api.steamApiGetJson(
            path = "/ISteamUserOAuth/GetFriendList/v1/",
            query = linkedMapOf(
                "steamid" to account.steamId,
                "relationship" to "all"
            ),
            accessToken = accessToken
        )
        val relationships = SteamFriendsParser.parseRelationships(relationshipsPayload)
        if (relationships.isEmpty()) return SteamFriendsSnapshot(fetchedAt = fetchedAt)

        val profiles = relationships.keys.chunked(MAX_PROFILE_BATCH).flatMap { steamIds ->
            runCatching {
                val profilePayload = api.steamApiGetJson(
                    path = "/ISteamUserOAuth/GetUserSummaries/v1/",
                    query = mapOf("steamids" to steamIds.joinToString(",")),
                    accessToken = accessToken
                )
                SteamFriendsParser.parseProfiles(profilePayload)
            }.getOrDefault(emptyList())
        }.associateBy(SteamFriendProfile::steamId)

        val nicknames = runCatching { nicknameGateway.fetch(account) }
            .onFailure { error ->
                SteamDiagLogger.append(
                    "friends nickname_sync failed type=${error.javaClass.simpleName}"
                )
            }
            .getOrDefault(emptyMap())

        return SteamFriendsSnapshot(
            friends = SteamFriendsParser.merge(relationships, profiles, nicknames),
            fetchedAt = fetchedAt
        )
    }

    override fun respondToInvite(
        account: SteamAccount,
        friendSteamId: String,
        accept: Boolean
    ): SteamFriendActionResult {
        require(account.hasRealSteamId) { "real Steam ID required" }
        require(friendSteamId.matches(Regex("7656119\\d{10}"))) { "valid friend Steam ID required" }
        require(
            !account.steamLoginSecure.isNullOrBlank() || !account.accessToken.isNullOrBlank()
        ) { "Steam community session required" }
        val sessionId = SteamInventoryService.newSessionId()
        val path = if (accept) "/actions/AddFriendAjax" else "/actions/IgnoreFriendInviteAjax"
        val form = linkedMapOf(
            "sessionID" to listOf(sessionId),
            "sessionid" to listOf(sessionId),
            "steamid" to listOf(friendSteamId)
        )
        if (accept) form["accept_invite"] = listOf("1")
        val payload = api.communityPostJson(
            path = path,
            form = form,
            cookies = SteamInventoryService.marketCookies(account, sessionId),
            referer = "https://steamcommunity.com/my/friends/pending"
        )
        val success = payload.successCode() == 1
        return SteamFriendActionResult(
            success = success,
            message = payload.text("error")
                .ifBlank { payload.text("message") }
                .ifBlank { payload.text("results") }
                .takeIf(String::isNotBlank)
        )
    }

    override fun changeRelationship(
        account: SteamAccount,
        friendSteamId: String,
        action: SteamFriendRelationshipAction
    ): SteamFriendActionResult {
        require(account.hasRealSteamId) { "real Steam ID required" }
        val friendId = friendSteamId.toSteamId64()
        val accessToken = account.accessToken?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("Steam access token required")
        val request = when (action) {
            SteamFriendRelationshipAction.ADD -> SteamCmFriendAction(
                SteamCmProtocol.EMSG_CLIENT_ADD_FRIEND,
                SteamCmProtocol.EMSG_CLIENT_ADD_FRIEND_RESPONSE,
                SteamProtoWriter().apply { writeFixed64(1, friendId) }.toByteArray()
            )
            SteamFriendRelationshipAction.REMOVE -> SteamCmFriendAction(
                SteamCmProtocol.EMSG_CLIENT_REMOVE_FRIEND,
                SteamCmProtocol.EMSG_CLIENT_FRIENDS_LIST,
                SteamProtoWriter().apply { writeFixed64(1, friendId) }.toByteArray()
            )
            SteamFriendRelationshipAction.BLOCK,
            SteamFriendRelationshipAction.UNBLOCK -> SteamCmFriendAction(
                SteamCmProtocol.EMSG_CLIENT_HIDE_FRIEND,
                SteamCmProtocol.EMSG_CLIENT_FRIENDS_LIST,
                SteamProtoWriter().apply {
                    writeFixed64(1, friendId)
                    writeBool(2, action == SteamFriendRelationshipAction.BLOCK)
                }.toByteArray()
            )
        }
        val body = cm.exchangeClientMessage(
            account,
            requestEMsg = request.requestEMsg,
            responseEMsg = request.responseEMsg,
            request = request.body
        )
        val eresult = if (action == SteamFriendRelationshipAction.ADD) {
            SteamProtoReader(body).parse()[1]?.asInt ?: 2
        } else {
            null
        }
        return SteamFriendActionResult(
            success = eresult == null || eresult == 1,
            message = eresult?.takeIf { it != 1 }?.let { "Steam result $it" }
        )
    }

    private fun String.toSteamId64(): Long {
        require(matches(Regex("7656119\\d{10}"))) { "valid friend Steam ID required" }
        return toLong()
    }

    private data class SteamCmFriendAction(
        val requestEMsg: Int,
        val responseEMsg: Int,
        val body: ByteArray
    )

    private fun JsonObject.successCode(): Int {
        val primitive = this["success"] as? JsonPrimitive ?: return 0
        return primitive.intOrNull
            ?: primitive.contentOrNull?.toIntOrNull()
            ?: if (primitive.booleanOrNull == true) 1 else 0
    }

    private fun JsonObject.text(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

    private companion object {
        const val MAX_PROFILE_BATCH = 100
    }
}
