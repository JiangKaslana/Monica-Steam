package takagi.ru.monica.steam.friends.chat.richmedia.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import com.github.penfeizhou.animation.apng.APNGDrawable
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import takagi.ru.monica.steam.foundation.ui.loadSteamRemoteBytes
import takagi.ru.monica.steam.foundation.ui.steamRemoteImageCacheFile

/** Rendering policy for the small, fixed-size assets served by Steam. */
internal enum class SteamChatRemoteImageMode {
    CONTENT,
    EMOTICON,
    STICKER
}

/**
 * Steam emoticons are deliberately pixel-art assets (54×54).  Filtering them
 * while enlarging a picker cell blends their hard edges into the dark
 * background, so nearest-neighbour sampling is the only readable policy.
 */
internal fun staticSteamImageFilterQuality(mode: SteamChatRemoteImageMode): FilterQuality =
    if (mode == SteamChatRemoteImageMode.EMOTICON) {
        FilterQuality.None
    } else {
        FilterQuality.High
    }

@Composable
internal fun SteamChatRemoteImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    playAnimation: Boolean = true,
    mode: SteamChatRemoteImageMode = SteamChatRemoteImageMode.CONTENT
) {
    val context = LocalContext.current
    var drawable by remember(url) { mutableStateOf<Drawable?>(null) }
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(url) {
        drawable = null
        bitmap = null
        val payload = loadSteamRemoteBytes(context, url) ?: return@LaunchedEffect
        if (isAnimatedPng(payload)) {
            drawable = runCatching {
                withContext(Dispatchers.Default) {
                    val cacheFile = steamRemoteImageCacheFile(context, url)
                    if (!cacheFile.isFile) return@withContext null
                    APNGDrawable.fromFile(cacheFile.absolutePath).apply {
                        // Starting is deferred until ImageView has attached the
                        // drawable and assigned non-empty bounds. Starting from
                        // the composition coroutine can leave APNG on frame one.
                        setAutoPlay(false)
                        setLoopLimit(0)
                    }
                }
            }.getOrNull()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isAnimatedSteamImage(payload)) {
            drawable = runCatching {
                withContext(Dispatchers.Default) {
                    ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(payload)))
                }
            }.getOrNull()
        } else {
            bitmap = decodeStaticSteamBitmap(payload)
        }
        // A malformed animation should never leave a permanent placeholder.
        if (drawable == null && bitmap == null) bitmap = decodeStaticSteamBitmap(payload)
    }
    val currentDrawable = drawable
    val platformAnimated = currentDrawable as? Animatable
    val apngAnimated = currentDrawable as? APNGDrawable
    val animated = platformAnimated ?: apngAnimated
    DisposableEffect(currentDrawable) {
        onDispose {
            stopSteamAnimation(currentDrawable)
        }
    }
    when {
        animated != null -> AndroidView(
            factory = {
                ImageView(it).apply {
                    scaleType = imageScaleType(mode)
                    this.contentDescription = contentDescription
                }
            },
            modifier = modifier,
            update = { view ->
                val drawableChanged = view.drawable !== currentDrawable
                if (drawableChanged) {
                    stopSteamAnimation(view.drawable)
                    // setImageDrawable installs the callback and applies the
                    // first bounds before the layout callback below runs.
                    view.setImageDrawable(currentDrawable)
                }
                view.scaleType = imageScaleType(mode)
                view.contentDescription = contentDescription
                if (playAnimation) {
                    // APNG4Android refuses to render while bounds are empty.
                    // The callback must also be installed before start().
                    if (drawableChanged || !isSteamAnimationRunning(currentDrawable)) {
                        view.doOnLayout {
                            if (view.drawable === currentDrawable) {
                                startSteamAnimation(currentDrawable, restart = drawableChanged)
                            }
                        }
                    }
                } else {
                    stopSteamAnimation(currentDrawable)
                }
            }
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && drawable != null -> AndroidView(
            factory = {
                ImageView(it).apply {
                    scaleType = imageScaleType(mode)
                    this.contentDescription = contentDescription
                }
            },
            modifier = modifier,
            update = { view ->
                val drawableChanged = view.drawable !== drawable
                if (drawableChanged) {
                    stopSteamAnimation(view.drawable)
                    view.setImageDrawable(drawable)
                }
                view.scaleType = imageScaleType(mode)
                view.contentDescription = contentDescription
                if (playAnimation) {
                    if (drawableChanged || !isSteamAnimationRunning(drawable)) {
                        view.doOnLayout {
                            if (view.drawable === drawable) {
                                startSteamAnimation(drawable, restart = drawableChanged)
                            }
                        }
                    }
                } else {
                    stopSteamAnimation(drawable)
                }
            }
        )
        bitmap != null && mode == SteamChatRemoteImageMode.EMOTICON ->
            SteamPixelEmoticonImage(
                bitmap = requireNotNull(bitmap),
                contentDescription = contentDescription,
                modifier = modifier
            )
        bitmap != null -> Image(
            painter = BitmapPainter(
                image = requireNotNull(bitmap).asImageBitmap(),
                filterQuality = staticSteamImageFilterQuality(mode)
            ),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = if (mode == SteamChatRemoteImageMode.STICKER) {
                // A Steam sticker is only 150px wide. Never invent pixels by
                // scaling it to a multi-density dp box.
                ContentScale.Inside
            } else {
                ContentScale.Fit
            }
        )
        else -> Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Icon(Icons.Default.EmojiEmotions, contentDescription = contentDescription)
        }
    }
}

/**
 * Android's BitmapDrawable exposes the filter flag used by the hardware canvas.
 * Compose's filter quality alone is insufficient on a few GPU drivers when a
 * 54px Steam pixel asset is enlarged into a picker cell.
 */
@Composable
private fun SteamPixelEmoticonImage(
    bitmap: Bitmap,
    contentDescription: String?,
    modifier: Modifier
) {
    val context = LocalContext.current
    AndroidView(
        factory = {
            ImageView(it).apply {
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                this.contentDescription = contentDescription
            }
        },
        modifier = modifier,
        update = { view ->
            view.scaleType = ImageView.ScaleType.CENTER_INSIDE
            view.contentDescription = contentDescription
            val current = view.drawable as? BitmapDrawable
            if (current?.bitmap !== bitmap) {
                view.setImageDrawable(BitmapDrawable(context.resources, bitmap))
            }
            (view.drawable as? BitmapDrawable)?.paint?.apply {
                // Preserve the source's pixel edges while Android scales the
                // 54px asset to the density-aware picker size.
                isFilterBitmap = false
                isAntiAlias = false
            }
        }
    )
}

/** APNG4Android implements Animatable2Compat rather than android.graphics.Animatable. */
private fun startSteamAnimation(drawable: Drawable?, restart: Boolean = false) {
    drawable ?: return
    // Register the ImageView callback before starting.  APNGDrawable uses the
    // callback to invalidate each decoded frame; starting it before visibility
    // is established can leave the first frame permanently on screen.
    if (restart) stopSteamAnimation(drawable)
    drawable.setVisible(true, true)
    when (drawable) {
        is APNGDrawable -> {
            if (!drawable.isRunning) drawable.start()
        }
        is Animatable -> if (!drawable.isRunning) drawable.start()
    }
}

private fun isSteamAnimationRunning(drawable: Drawable?): Boolean = when (drawable) {
    is APNGDrawable -> drawable.isRunning
    is Animatable -> drawable.isRunning
    else -> false
}

private fun stopSteamAnimation(drawable: Drawable?) {
    drawable ?: return
    // Keep the Android Animatable stop path explicit; APNGDrawable is handled
    // separately because it implements Animatable2Compat instead.
    when (drawable) {
        is APNGDrawable -> {
            if (drawable.isRunning) drawable.stop()
            drawable.setVisible(false, false)
        }
        is Animatable -> {
            if (drawable.isRunning) drawable.stop()
            drawable.setVisible(false, false)
        }
    }
}

private suspend fun decodeStaticSteamBitmap(payload: ByteArray): Bitmap? =
    withContext(Dispatchers.Default) {
        val options = BitmapFactory.Options().apply {
            // Keep the CDN's actual pixels; density-based decoding would make
            // the already-small Steam assets even less predictable.
            inScaled = false
            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
        }
        BitmapFactory.decodeByteArray(payload, 0, payload.size, options)?.also {
            // Treat the 54px Steam asset as a density-independent source. The
            // Android view then enlarges it with nearest-neighbour sampling.
            it.density = android.util.DisplayMetrics.DENSITY_DEFAULT
        }
    }

private fun imageScaleType(mode: SteamChatRemoteImageMode): ImageView.ScaleType =
    if (mode == SteamChatRemoteImageMode.STICKER) {
        ImageView.ScaleType.CENTER_INSIDE
    } else {
        ImageView.ScaleType.FIT_CENTER
    }
