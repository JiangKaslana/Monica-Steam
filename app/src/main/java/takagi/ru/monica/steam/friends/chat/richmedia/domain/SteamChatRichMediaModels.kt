package takagi.ru.monica.steam.friends.chat.richmedia.domain

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class SteamChatEmoticon(
    val name: String,
    val appId: Int = 0,
    val lastUsedAt: Long = 0L,
    val useCount: Int = 0
) {
    val messageCode: String get() = ":$name:"
    val imageUrl: String get() =
        "https://steamcommunity.com/economy/emoticonlarge/${encodeSteamChatPath(name)}"
}

data class SteamChatSticker(
    val name: String,
    val title: String = name,
    val appId: Int = 0,
    val count: Int = 1,
    val lastUsedAt: Long = 0L,
    val useCount: Int = 0,
    /** The non-static Steam CDN variant preserves animated sticker assets. */
    val imageUrl: String = "https://steamcommunity.com/economy/sticker/${encodeSteamChatPath(name)}"
) {
    val messageCode: String get() = "/sticker $name"
}

/** A Steam chat-room effect owned by the account. */
data class SteamChatEffect(
    val name: String,
    val appId: Int = 0,
    val count: Int = 1,
    val infiniteUse: Boolean = false,
    val receivedAt: Long = 0L
) {
    val messageCode: String get() = "/roomeffect $name"
}

data class SteamChatRichMediaCatalog(
    val emoticons: List<SteamChatEmoticon> = emptyList(),
    val stickers: List<SteamChatSticker> = emptyList(),
    val effects: List<SteamChatEffect> = emptyList()
)

enum class SteamChatAttachmentKind {
    IMAGE,
    VIDEO,
    ARCHIVE,
    LINK
}

data class SteamChatPendingAttachment(
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val kind: SteamChatAttachmentKind,
    val width: Int = 0,
    val height: Int = 0
)

data class SteamChatUploadedAttachment(
    val url: String,
    val label: String,
    val kind: SteamChatAttachmentKind,
    val spoiler: Boolean
)

sealed interface SteamChatRichContent {
    data class Text(val body: String) : SteamChatRichContent

    data class Action(val body: String) : SteamChatRichContent

    /** A first-class Steam lobby/game invitation embedded in chat BBCode. */
    data class GameInvite(
        val appId: Int?,
        val lobbyId: String?,
        val inviterSteamId: String?,
        val url: String?,
        val label: String,
        val rawBody: String,
        val inviteKind: String = "gameinvite"
    ) : SteamChatRichContent

    data class OfficialMessage(val message: SteamChatOfficialMessage) : SteamChatRichContent

    data class Sticker(val name: String) : SteamChatRichContent {
        val imageUrl: String get() =
            "https://steamcommunity.com/economy/sticker/${encodeSteamChatPath(name)}"
    }

    data class Attachment(
        val url: String,
        val label: String,
        val kind: SteamChatAttachmentKind,
        val spoiler: Boolean = false
    ) : SteamChatRichContent
}

object SteamChatRichContentParser {
    private val actionPattern = Regex(
        "^/me(?:\\s+)(.+)$",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val stickerPattern = Regex("^/sticker\\s+(.+?)\\s*$", RegexOption.IGNORE_CASE)
    private val spoilerPattern = Regex(
        "^\\[spoiler(?:=[^]]+)?](.*?)\\[/spoiler]$",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    /**
     * Steam's web client receives rich media in both slash-command form and
     * BBCode form.  The latter is what GetRecentMessages returns for older
     * messages, so keep the tag parser separate from the invitation parser.
     */
    private val officialMediaTagPattern = Regex(
        """^\[(sticker|roomeffect|emoticon)(?:\s+([^\]]+))?](.*?)\[/\1\s*]$""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val officialMediaSelfClosingTagPattern = Regex(
        """^\[(sticker|roomeffect|emoticon)(?:\s+([^\]]+))?]\s*$""",
        RegexOption.IGNORE_CASE
    )
    private val officialEmoticonTagPattern = Regex(
        """\[emoticon(?:\s+([^\]]+))?](.*?)\[/emoticon\s*]""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val joinLobbyPattern = Regex(
        "steam://joinlobby/(\\d+)(?:/([^/\\s\\]]+))?(?:/(7656119\\d{10}))?",
        RegexOption.IGNORE_CASE
    )
    private val gameUrlPattern = Regex(
        "steam://(joinlobby|joinparty|rungame|remoteplay/connect)/([^\\s\\]]+)",
        RegexOption.IGNORE_CASE
    )
    private val linkedUrlPattern = Regex(
        "\\[url=(steam://joinlobby/[^]]+)]([^\\[]+)\\[/url]",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val specialTagPattern = Regex(
        "\\[(gameinvite|lobbyinvite|tradeoffer|trade_offer|incomingtradeoffer|broadcastinvite|broadcastviewrequest|playtestinvite|remoteplayinvite|gift|giftreceived|giftnotification|inventoryitem|itemnotification|newitem|friendinvite|friendrequest|claninvite|groupinvite|eventnotification|commentnotification|marketnotification|invite)(?:\\s+([^]]+))?](.*?)\\[/\\1]",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val officialSelfClosingMessagePattern = Regex(
        "^\\[(tradeoffer|trade_offer|incomingtradeoffer|broadcastinvite|broadcastviewrequest|playtestinvite|remoteplayinvite|gift|giftreceived|giftnotification|inventoryitem|itemnotification|newitem|friendinvite|friendrequest|claninvite|groupinvite|eventnotification|commentnotification|marketnotification)(?:\\s+([^]]+))?]\\s*$",
        RegexOption.IGNORE_CASE
    )
    private val unknownOfficialTagPattern = Regex(
        "^\\[((?:steam[_-][A-Za-z0-9_-]+)|(?:[A-Za-z0-9_-]+notification))(?:\\s+([^]]+))?](.*?)\\[/\\1]$",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val unknownOfficialSelfClosingTagPattern = Regex(
        "^\\[((?:steam[_-][A-Za-z0-9_-]+)|(?:[A-Za-z0-9_-]+notification))(?:\\s+([^]]+))?]\\s*$",
        RegexOption.IGNORE_CASE
    )
    private val steamUrlPattern = Regex(
        "steam://(?:joinlobby|joinparty|rungame|remoteplay/connect)/[^\\s\\]]+",
        RegexOption.IGNORE_CASE
    )
    private val httpUrlPattern = Regex("https?://[^\\s\\]]+", RegexOption.IGNORE_CASE)
    private val steamTradeOfferUrlPattern = Regex(
        "https://steamcommunity\\.com/tradeoffer/(?:new/\\?[^\\s\\[\\]]+|\\d+/?[^\\s\\[\\]]*)",
        RegexOption.IGNORE_CASE
    )
    private val imagePattern = Regex("\\[img(?:=[^]]+)?](https?://[^\\[]+)\\[/img]", RegexOption.IGNORE_CASE)
    private val videoPattern = Regex("\\[video(?:=[^]]+)?](https?://[^\\[]+)\\[/video]", RegexOption.IGNORE_CASE)
    private val urlPattern = Regex(
        "\\[url=(https?://[^]]+)]([^\\[]+)\\[/url]",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    fun parse(body: String): SteamChatRichContent {
        actionPattern.matchEntire(body.trim())?.groupValues?.getOrNull(1)
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let { return SteamChatRichContent.Action(it) }

        stickerPattern.matchEntire(body.trim())?.groupValues?.getOrNull(1)
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let { return SteamChatRichContent.Sticker(decodeMediaName(it)) }

        parseOfficialEmoticonSequence(body)?.let { return it }

        officialMediaTagPattern.matchEntire(body.trim())?.let { match ->
            parseOfficialMedia(
                kind = match.groupValues[1],
                rawAttributes = match.groupValues.getOrNull(2).orEmpty(),
                inner = match.groupValues.getOrNull(3).orEmpty(),
                rawBody = body
            )?.let { return it }
        }
        officialMediaSelfClosingTagPattern.matchEntire(body.trim())?.let { match ->
            parseOfficialMedia(
                kind = match.groupValues[1],
                rawAttributes = match.groupValues.getOrNull(2).orEmpty(),
                inner = "",
                rawBody = body
            )?.let { return it }
        }
        officialSelfClosingMessagePattern.matchEntire(body.trim())?.let { match ->
            return SteamChatRichContent.OfficialMessage(
                SteamChatOfficialMessageParser.parse(
                    tag = match.groupValues[1],
                    rawAttributes = match.groupValues.getOrNull(2).orEmpty(),
                    innerText = "",
                    rawBody = body
                )
            )
        }

        val inviteMatch = joinLobbyPattern.find(body)
        if (inviteMatch != null) {
            val url = inviteMatch.value
            val linkedLabel = linkedUrlPattern.find(body)?.groupValues?.getOrNull(2)
                ?.trim()
                .orEmpty()
            return SteamChatRichContent.GameInvite(
                appId = inviteMatch.groupValues.getOrNull(1)?.toIntOrNull(),
                lobbyId = inviteMatch.groupValues.getOrNull(2)?.takeIf(String::isNotBlank),
                inviterSteamId = inviteMatch.groupValues.getOrNull(3)?.takeIf(String::isNotBlank),
                url = url,
                label = linkedLabel.ifBlank { "Steam game invitation" },
                rawBody = body
            )
        }

        gameUrlPattern.find(body)?.let { match ->
            val kind = match.groupValues[1].lowercase()
            val parts = match.groupValues[2].split('/')
            val appId = parts.firstOrNull()?.toIntOrNull()
            val lobbyId = parts.getOrNull(1)?.takeIf { kind == "joinlobby" }
            val inviter = parts.firstOrNull { it.startsWith("7656119") }
            return SteamChatRichContent.GameInvite(
                appId = appId,
                lobbyId = lobbyId,
                inviterSteamId = inviter,
                url = match.value,
                label = linkedUrlPattern.find(body)?.groupValues?.getOrNull(2)?.trim()
                    .orEmpty().ifBlank { "Steam game invitation" },
                rawBody = body,
                inviteKind = kind
            )
        }

        steamTradeOfferUrlPattern.find(body)?.let { match ->
            return SteamChatRichContent.OfficialMessage(
                SteamChatOfficialMessageParser.parse(
                    tag = "tradeoffer",
                    rawAttributes = "",
                    innerText = match.value,
                    rawBody = body
                )
            )
        }

        specialTagPattern.find(body)?.let { match ->
            val kind = match.groupValues[1].lowercase()
            val attributes = parseAttributes(match.groupValues.getOrNull(2).orEmpty())
            val innerText = match.groupValues.getOrNull(3).orEmpty().trim()
            val appId = attributes["appid"]?.toIntOrNull()
            val lobbyId = attributes["lobbyid"] ?: attributes["lobby_id"]
            val inviter = attributes["steamid"] ?: attributes["steamid64"]
            val url = steamUrlPattern.find(innerText)?.value
                ?: httpUrlPattern.find(innerText)?.value
                ?: buildInviteUrl(kind, appId, lobbyId, inviter, attributes)
            if (kind == "gameinvite" || kind == "lobbyinvite") {
                return SteamChatRichContent.GameInvite(
                    appId = appId,
                    lobbyId = lobbyId,
                    inviterSteamId = inviter,
                    url = url,
                    label = innerText.ifBlank { "Steam game invitation" },
                    rawBody = body,
                    inviteKind = kind
                )
            }
            return SteamChatRichContent.OfficialMessage(
                SteamChatOfficialMessageParser.parse(
                    tag = kind,
                    rawAttributes = match.groupValues.getOrNull(2).orEmpty(),
                    innerText = innerText,
                    rawBody = body
                ).let { official ->
                    if (official.url == null && url != null) official.copy(url = url) else official
                }
            )
        }

        unknownOfficialTagPattern.matchEntire(body.trim())?.let { match ->
            return SteamChatRichContent.OfficialMessage(
                SteamChatOfficialMessageParser.parse(
                    tag = match.groupValues[1],
                    rawAttributes = match.groupValues.getOrNull(2).orEmpty(),
                    innerText = match.groupValues.getOrNull(3).orEmpty(),
                    rawBody = body
                )
            )
        }
        unknownOfficialSelfClosingTagPattern.matchEntire(body.trim())?.let { match ->
            return SteamChatRichContent.OfficialMessage(
                SteamChatOfficialMessageParser.parse(
                    tag = match.groupValues[1],
                    rawAttributes = match.groupValues.getOrNull(2).orEmpty(),
                    innerText = "",
                    rawBody = body
                )
            )
        }

        val spoiler = spoilerPattern.matchEntire(body.trim())
        parseAttachment(spoiler?.groupValues?.getOrNull(1) ?: body, spoiler != null)?.let {
            return it
        }
        return SteamChatRichContent.Text(body)
    }

    private fun parseOfficialEmoticonSequence(body: String): SteamChatRichContent.Text? {
        val trimmed = body.trim()
        val matches = officialEmoticonTagPattern.findAll(trimmed).toList()
        if (matches.isEmpty()) return null
        var cursor = 0
        val codes = mutableListOf<String>()
        matches.forEach { match ->
            if (trimmed.substring(cursor, match.range.first).isNotBlank()) return null
            val attributes = parseAttributes(match.groupValues.getOrNull(1).orEmpty())
            val name = decodeMediaName(
                attributes["type"] ?: attributes["name"]
                ?: match.groupValues.getOrNull(2).orEmpty().trim()
            ).trim()
            if (name.isBlank()) return null
            codes += ":$name:"
            cursor = match.range.last + 1
        }
        if (trimmed.substring(cursor).isNotBlank()) return null
        return SteamChatRichContent.Text(codes.joinToString(" "))
    }

    private fun parseAttachment(body: String, spoiler: Boolean): SteamChatRichContent.Attachment? {
        imagePattern.find(body)?.let { match ->
            val url = normalizeAttachmentUrl(match.groupValues[1])
            return SteamChatRichContent.Attachment(
                url = url,
                label = fileLabel(url),
                kind = SteamChatAttachmentKind.IMAGE,
                spoiler = spoiler
            )
        }
        videoPattern.find(body)?.let { match ->
            val url = normalizeAttachmentUrl(match.groupValues[1])
            return SteamChatRichContent.Attachment(
                url = url,
                label = fileLabel(url),
                kind = SteamChatAttachmentKind.VIDEO,
                spoiler = spoiler
            )
        }
        urlPattern.find(body)?.let { match ->
            val url = normalizeAttachmentUrl(match.groupValues[1])
            val label = match.groupValues[2].trim().ifBlank { fileLabel(url) }
            return SteamChatRichContent.Attachment(
                url = url,
                label = label,
                kind = attachmentKind(url),
                spoiler = spoiler
            )
        }
        return null
    }

    private fun normalizeAttachmentUrl(value: String): String = value
        .trim()
        .replace("&amp;", "&", ignoreCase = true)

    private fun parseAttributes(raw: String): Map<String, String> =
        Regex("""([A-Za-z0-9_]+)=(?:"([^"]*)"|'([^']*)'|([^\s]+))""")
            .findAll(raw)
            .associate { match ->
                val value = match.groupValues.drop(2).firstOrNull(String::isNotEmpty).orEmpty()
                match.groupValues[1].lowercase() to value
            }

    private fun parseOfficialMedia(
        kind: String,
        rawAttributes: String,
        inner: String,
        rawBody: String
    ): SteamChatRichContent? {
        val normalizedKind = kind.lowercase()
        val attributes = parseAttributes(rawAttributes)
        val name = decodeMediaName(
            attributes["type"]
                ?: attributes["name"]
                ?: inner.trim()
        ).trim()
        if (name.isBlank()) return null
        return when (normalizedKind) {
            "sticker" -> SteamChatRichContent.Sticker(name)
            "emoticon" -> SteamChatRichContent.Text(":$name:")
            "roomeffect" -> SteamChatRichContent.OfficialMessage(
                SteamChatOfficialMessageParser.parse(
                    tag = normalizedKind,
                    rawAttributes = rawAttributes,
                    innerText = name,
                    rawBody = rawBody
                )
            )
            else -> null
        }
    }

    private fun decodeMediaName(value: String): String = runCatching {
        URLDecoder.decode(value.trim().trim('"', '\''), StandardCharsets.UTF_8.name())
    }.getOrDefault(value.trim().trim('"', '\''))

    private fun buildInviteUrl(
        kind: String,
        appId: Int?,
        lobbyId: String?,
        inviterSteamId: String?,
        attributes: Map<String, String>
    ): String? {
        if (appId == null) return null
        return when {
            (kind == "lobbyinvite" || kind == "gameinvite") && !lobbyId.isNullOrBlank() ->
                "steam://joinlobby/$appId/$lobbyId" +
                    (inviterSteamId?.let { "/$it" } ?: "")
            !attributes["remoteplay"].isNullOrBlank() ->
                "steam://remoteplay/connect/${inviterSteamId.orEmpty()}?appid=$appId&${attributes["remoteplay"]}"
            !(attributes["connect"] ?: attributes["connectstring"]).isNullOrBlank() &&
                !inviterSteamId.isNullOrBlank() -> {
                val connect = attributes["connect"] ?: attributes["connectstring"]!!
                "steam://rungame/$appId/$inviterSteamId/${encodeUriComponent(connect)}"
            }
            else -> null
        }
    }

    private fun encodeUriComponent(value: String): String = URLEncoder.encode(
        value,
        StandardCharsets.UTF_8.name()
    ).replace("+", "%20")

    private fun steamSpecialLabel(kind: String): String = when (kind) {
        "tradeoffer" -> "Steam trade offer"
        "broadcastinvite" -> "Steam broadcast invitation"
        "broadcastviewrequest" -> "Steam broadcast request"
        "playtestinvite" -> "Steam playtest invitation"
        "invite" -> "Steam invitation"
        else -> "Steam notification"
    }

    private fun fileLabel(url: String): String = runCatching {
        URLDecoder.decode(
            url.substringBefore('?').substringAfterLast('/').ifBlank { "Steam attachment" },
            StandardCharsets.UTF_8.name()
        )
    }.getOrDefault("Steam attachment")

    private fun attachmentKind(url: String): SteamChatAttachmentKind =
        when (url.substringBefore('?').substringAfterLast('.').lowercase()) {
            "jpg", "jpeg", "png", "gif", "webp", "avif" -> SteamChatAttachmentKind.IMAGE
            "webm", "mpg", "mpeg", "mp4", "ogv" -> SteamChatAttachmentKind.VIDEO
            "zip" -> SteamChatAttachmentKind.ARCHIVE
            else -> SteamChatAttachmentKind.LINK
        }
}

object SteamChatUnicodeEmojiCatalog {
    val items: List<String> = listOf(
        "😀", "😃", "😄", "😁", "😆", "🥹", "😂", "🤣",
        "😊", "🙂", "🙃", "😉", "😍", "🥰", "😘", "😎",
        "🤔", "🫡", "🤨", "😐", "😑", "😶", "🙄", "😬",
        "😴", "🥳", "😢", "😭", "😤", "😡", "🤯", "😱",
        "👍", "👎", "👌", "✌️", "🤝", "👏", "🙌", "🙏",
        "💪", "👀", "❤️", "🧡", "💛", "💚", "💙", "💜",
        "🔥", "✨", "🎉", "🎮", "🏆", "🚀", "💯", "✅"
    )
}

private fun encodeSteamChatPath(value: String): String = URLEncoder.encode(
    value,
    StandardCharsets.UTF_8.name()
).replace("+", "%20")
