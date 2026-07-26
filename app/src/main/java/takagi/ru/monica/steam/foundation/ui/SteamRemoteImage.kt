package takagi.ru.monica.steam.foundation.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val STEAM_IMAGE_TIMEOUT_MS = 4_000
private const val STEAM_IMAGE_CACHE_TTL_MS = 3L * 24L * 60L * 60L * 1000L
// v2 intentionally bypasses bytes cached by the old bitmap/thumbnail path.
// Chat stickers must retain their original APNG container, not a first-frame
// derivative that may already be present on an upgraded installation.
private const val STEAM_IMAGE_CACHE_VERSION = "v2"
private val steamImageCacheLock = Any()

internal suspend fun loadSteamRemoteImage(context: Context, imageUrl: String): ImageBitmap? =
    withContext(Dispatchers.IO) {
        loadSteamRemoteBytesBlocking(context, imageUrl)
            ?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
    }

/** Loads the original CDN bytes so animated WebP/GIF stickers are not flattened. */
internal suspend fun loadSteamRemoteBytes(context: Context, imageUrl: String): ByteArray? =
    withContext(Dispatchers.IO) { loadSteamRemoteBytesBlocking(context, imageUrl) }

private fun loadSteamRemoteBytesBlocking(context: Context, imageUrl: String): ByteArray? {
    val normalizedUrl = normalizeSteamImageUrl(imageUrl)
    if (!normalizedUrl.startsWith("https://") && !normalizedUrl.startsWith("http://")) return null

    val cacheFile = steamRemoteImageCacheFileForNormalizedUrl(context, normalizedUrl)
    val cachedBytes = synchronized(steamImageCacheLock) {
        cacheFile.takeIf(File::isFile)?.let { runCatching { it.readBytes() }.getOrNull() }
    }
    if (cachedBytes != null && !isSteamRemoteImageCacheExpired(cacheFile)) return cachedBytes

    return runCatching {
        downloadSteamRemoteImageBytes(normalizedUrl)?.also { bytes ->
            synchronized(steamImageCacheLock) {
                cacheFile.parentFile?.mkdirs()
                val temporaryFile = File(
                    requireNotNull(cacheFile.parentFile),
                    "${cacheFile.name}.${System.nanoTime()}.tmp"
                )
                try {
                    temporaryFile.writeBytes(bytes)
                    // renameTo stays on the same filesystem, so readers never
                    // observe a partially-written APNG/PNG payload.
                    if (cacheFile.exists()) cacheFile.delete()
                    if (!temporaryFile.renameTo(cacheFile)) {
                        // Extremely unusual filesystems may reject rename;
                        // retain the old behavior as a last-resort fallback.
                        cacheFile.writeBytes(bytes)
                    }
                } finally {
                    temporaryFile.delete()
                }
            }
        } ?: cachedBytes
    }.getOrNull() ?: cachedBytes
}

internal fun normalizeSteamImageUrl(imageUrl: String): String {
    val trimmed = imageUrl.trim()
    val normalized = when {
        trimmed.startsWith("//") -> "https:$trimmed"
        trimmed.startsWith("/") -> "https://steamcommunity.com$trimmed"
        else -> trimmed
    }
    // Steam's community host intermittently serves a resized/empty response
    // after its redirect.  The static CDN is the same asset origin used by
    // Steam Web and preserves the original APNG bytes.
    return normalized.replace(
        oldValue = "https://steamcommunity.com/economy/",
        newValue = "https://community.cloudflare.steamstatic.com/economy/",
        ignoreCase = true
    )
}

private fun downloadSteamRemoteImageBytes(imageUrl: String): ByteArray? {
    val connection = (URL(imageUrl).openConnection() as HttpURLConnection).apply {
        connectTimeout = STEAM_IMAGE_TIMEOUT_MS
        readTimeout = STEAM_IMAGE_TIMEOUT_MS
        requestMethod = "GET"
        instanceFollowRedirects = true
        setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/png,image/*;q=0.8")
        setRequestProperty("User-Agent", "MonicaSteam/Android")
    }
    return try {
        if (connection.responseCode !in 200..299) return null
        connection.inputStream.use { stream ->
            stream.readBytes().takeIf { bytes -> bytes.isNotEmpty() }
        }
    } finally {
        connection.disconnect()
    }
}

internal fun steamRemoteImageCacheFile(context: Context, imageUrl: String): File {
    val normalizedUrl = normalizeSteamImageUrl(imageUrl)
    return steamRemoteImageCacheFileForNormalizedUrl(context, normalizedUrl)
}

private fun steamRemoteImageCacheFileForNormalizedUrl(context: Context, imageUrl: String): File {
    val safeName = imageUrl.hashCode().toUInt().toString(16)
    return File(
        File(context.cacheDir, "steam_confirmation_images_$STEAM_IMAGE_CACHE_VERSION"),
        "$safeName.bin"
    )
}

private fun isSteamRemoteImageCacheExpired(cacheFile: File): Boolean {
    if (!cacheFile.isFile) return true
    return System.currentTimeMillis() - cacheFile.lastModified() > STEAM_IMAGE_CACHE_TTL_MS
}
