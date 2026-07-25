package takagi.ru.monica.steam.friends.chat.richmedia.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamChatRichMediaModelsTest {
    @Test
    fun parsesOfficialStickerSlashCommand() {
        val content = SteamChatRichContentParser.parse("/sticker Mesmer spin")

        assertTrue(content is SteamChatRichContent.Sticker)
        assertEquals("Mesmer spin", (content as SteamChatRichContent.Sticker).name)
        assertEquals("/sticker Mesmer spin", SteamChatSticker("Mesmer spin", "Mesmer").messageCode)
    }

    @Test
    fun parsesOfficialStickerBbcodeTag() {
        val content = SteamChatRichContentParser.parse(
            "[sticker type=Mesmer%20spin][/sticker]"
        )

        assertTrue(content is SteamChatRichContent.Sticker)
        assertEquals("Mesmer spin", (content as SteamChatRichContent.Sticker).name)
    }

    @Test
    fun parsesOfficialRoomEffectBbcodeTag() {
        val content = SteamChatRichContentParser.parse(
            "[roomeffect type=confetti][/roomeffect]"
        )

        assertTrue(content is SteamChatRichContent.OfficialMessage)
        val effect = (content as SteamChatRichContent.OfficialMessage).message
        assertEquals(SteamChatOfficialMessageKind.ROOM_EFFECT, effect.kind)
        assertEquals("confetti", effect.description)
    }

    @Test
    fun normalizesOfficialEmoticonBbcodeTagForInlineRendering() {
        val content = SteamChatRichContentParser.parse(
            "[emoticon]steamthumbsup[/emoticon]"
        )

        assertEquals(":steamthumbsup:", (content as SteamChatRichContent.Text).body)
    }

    @Test
    fun parsesOfficialSelfClosingStickerAndRoomEffectTags() {
        val sticker = SteamChatRichContentParser.parse(
            "[sticker type=Mesmer%20spin]"
        ) as SteamChatRichContent.Sticker
        assertEquals("Mesmer spin", sticker.name)

        val effect = SteamChatRichContentParser.parse(
            "[roomeffect type=confetti]"
        ) as SteamChatRichContent.OfficialMessage
        assertEquals(SteamChatOfficialMessageKind.ROOM_EFFECT, effect.message.kind)
        assertEquals("confetti", effect.message.description)
    }

    @Test
    fun richMediaCommandsUseSteamOfficialSyntax() {
        assertEquals("/sticker Mesmer spin", SteamChatSticker("Mesmer spin").messageCode)
        assertEquals("/roomeffect confetti", SteamChatEffect("confetti").messageCode)
    }

    @Test
    fun parsesSteamImageAndLinkedArchiveBbcode() {
        val image = SteamChatRichContentParser.parse(
            "[img]https://steamusercontent.com/chat/photo.png[/img]"
        ) as SteamChatRichContent.Attachment
        val archive = SteamChatRichContentParser.parse(
            "[url=https://steamusercontent.com/chat/files/export.zip]export.zip[/url]"
        ) as SteamChatRichContent.Attachment

        assertEquals(SteamChatAttachmentKind.IMAGE, image.kind)
        assertEquals("photo.png", image.label)
        assertEquals(SteamChatAttachmentKind.ARCHIVE, archive.kind)
        assertEquals("export.zip", archive.label)
    }

    @Test
    fun keepsOrdinaryTextUntouched() {
        val body = "hello :steamthumbsup:"
        assertEquals(SteamChatRichContent.Text(body), SteamChatRichContentParser.parse(body))
    }

    @Test
    fun parsesSteamJoinLobbyInviteBeforeGenericLinks() {
        val content = SteamChatRichContentParser.parse(
            "[url=steam://joinlobby/730/123456789/76561198000000001]Join game[/url]"
        )

        assertTrue(content is SteamChatRichContent.GameInvite)
        val invite = content as SteamChatRichContent.GameInvite
        assertEquals(730, invite.appId)
        assertEquals("123456789", invite.lobbyId)
        assertEquals("76561198000000001", invite.inviterSteamId)
        assertEquals("steam://joinlobby/730/123456789/76561198000000001", invite.url)
    }

    @Test
    fun keepsUnknownSteamSpecialMessageReadable() {
        val body = "[steam_unknown type=42]payload[/steam_unknown]"
        val unknown = SteamChatRichContentParser.parse(body) as SteamChatRichContent.OfficialMessage
        assertEquals(SteamChatOfficialMessageKind.UNKNOWN, unknown.message.kind)
        assertEquals(body, unknown.message.rawBody)
    }

    @Test
    fun parsesSteamBbcodeGameInviteArgumentsAndOtherSystemTags() {
        val invite = SteamChatRichContentParser.parse(
            "[gameinvite appid=440 lobbyid=123456789]Team Fortress 2[/gameinvite]"
        ) as SteamChatRichContent.GameInvite
        assertEquals(440, invite.appId)
        assertEquals("123456789", invite.lobbyId)
        assertEquals("steam://joinlobby/440/123456789", invite.url)

        val trade = SteamChatRichContentParser.parse(
            "[tradeoffer]https://steamcommunity.com/tradeoffer/123[/tradeoffer]"
        ) as SteamChatRichContent.OfficialMessage
        assertEquals(SteamChatOfficialMessageKind.TRADE_OFFER, trade.message.kind)
        assertEquals("123", trade.message.tradeOfferId)
        assertEquals("https://steamcommunity.com/tradeoffer/123", trade.message.url)
    }

    @Test
    fun buildsOfficialGameInviteUrlsForConnectAndRemotePlayArguments() {
        val connect = SteamChatRichContentParser.parse(
            "[gameinvite appid=440 steamid=76561198000000001 connect=+connect][/gameinvite]"
        ) as SteamChatRichContent.GameInvite
        assertEquals(
            "steam://rungame/440/76561198000000001/%2Bconnect",
            connect.url
        )

        val remotePlay = SteamChatRichContentParser.parse(
            "[gameinvite appid=440 steamid=76561198000000001 remoteplay=restricted_countries=CN][/gameinvite]"
        ) as SteamChatRichContent.GameInvite
        assertEquals(
            "steam://remoteplay/connect/76561198000000001?appid=440&restricted_countries=CN",
            remotePlay.url
        )
    }

    @Test
    fun parsesGiftInventoryRemotePlayAndBroadcastNotificationsAsTypedMessages() {
        val samples = mapOf(
            "[gift appid=730]A gift is waiting[/gift]" to SteamChatOfficialMessageKind.GIFT,
            "[inventoryitem appid=440 name=Hat]New item[/inventoryitem]" to
                SteamChatOfficialMessageKind.INVENTORY_ITEM,
            "[remoteplayinvite appid=570 steamid=76561198000000001]Join[/remoteplayinvite]" to
                SteamChatOfficialMessageKind.REMOTE_PLAY_INVITE,
            "[broadcastinvite steamid=76561198000000001]Watch now[/broadcastinvite]" to
                SteamChatOfficialMessageKind.BROADCAST_INVITE
        )

        samples.forEach { (body, expectedKind) ->
            val parsed = SteamChatRichContentParser.parse(body) as SteamChatRichContent.OfficialMessage
            assertEquals(expectedKind, parsed.message.kind)
            assertEquals(body, parsed.message.rawBody)
        }
    }

    @Test
    fun parsesSelfClosingTradeAndAdditionalSteamNotificationFamilies() {
        val trade = SteamChatRichContentParser.parse(
            "[incomingtradeoffer tradeofferid=987654 steamid=76561198000000001]"
        ) as SteamChatRichContent.OfficialMessage
        assertEquals(SteamChatOfficialMessageKind.TRADE_OFFER, trade.message.kind)
        assertEquals("987654", trade.message.tradeOfferId)

        val samples = mapOf(
            "[groupinvite]Community[/groupinvite]" to SteamChatOfficialMessageKind.GROUP_INVITE,
            "[eventnotification]Weekend event[/eventnotification]" to SteamChatOfficialMessageKind.EVENT,
            "[commentnotification]New comment[/commentnotification]" to SteamChatOfficialMessageKind.COMMENT,
            "[marketnotification]Item sold[/marketnotification]" to SteamChatOfficialMessageKind.MARKET
        )
        samples.forEach { (body, kind) ->
            val parsed = SteamChatRichContentParser.parse(body) as SteamChatRichContent.OfficialMessage
            assertEquals(kind, parsed.message.kind)
        }
    }
}
