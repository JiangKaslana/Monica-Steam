package takagi.ru.monica.steam.store.ui.gallery

import takagi.ru.monica.steam.profile.SteamRemoteImageCache

internal object SteamScreenshotDownloadPolicy {
    private const val MAX_FILE_STEM_LENGTH = 56
    private val invalidFilenameCharacters = Regex("""[\\/:*?"<>|\p{Cc}]""")
    private val repeatedSeparators = Regex("[_\\s]+")

    fun isAllowedUrl(rawUrl: String): Boolean {
        return SteamRemoteImageCache.isAllowedSteamImageUrl(rawUrl)
    }

    fun normalizeMimeType(rawMimeType: String?): String? {
        return when (rawMimeType.orEmpty().substringBefore(';').trim().lowercase()) {
            "image/jpeg", "image/jpg", "image/pjpeg" -> "image/jpeg"
            "image/png" -> "image/png"
            "image/webp" -> "image/webp"
            "image/gif" -> "image/gif"
            "image/avif" -> "image/avif"
            else -> null
        }
    }

    fun buildDisplayName(
        gameName: String,
        screenshotIndex: Int,
        mimeType: String,
        timestampMillis: Long
    ): String {
        val extension = when (normalizeMimeType(mimeType)) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/avif" -> "avif"
            else -> "jpg"
        }
        return buildString {
            append(safeFileStem(gameName))
            append("_screenshot_")
            append(screenshotIndex.coerceAtLeast(0) + 1)
            append('_')
            append(timestampMillis.coerceAtLeast(0L))
            append('.')
            append(extension)
        }
    }

    fun safeFileStem(rawName: String): String {
        return rawName
            .replace(invalidFilenameCharacters, "_")
            .replace(repeatedSeparators, "_")
            .trim(' ', '.', '_')
            .take(MAX_FILE_STEM_LENGTH)
            .trim(' ', '.', '_')
            .ifBlank { "steam_game" }
    }
}
