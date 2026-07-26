package takagi.ru.monica.steam.friends.chat.richmedia.ui

import android.graphics.ImageDecoder
import android.graphics.BitmapFactory
import android.graphics.drawable.Animatable
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.github.penfeizhou.animation.apng.APNGDrawable
import com.github.penfeizhou.animation.loader.ByteBufferLoader
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import takagi.ru.monica.steam.foundation.ui.loadSteamRemoteBytes

/** Rendering policy for the small, fixed-size assets served by Steam. */
internal enum class SteamChatRemoteImageMode {
    CONTENT,
    EMOTICON,
    STICKER
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
    var image by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        drawable = null
        image = null
        val payload = loadSteamRemoteBytes(context, url) ?: return@LaunchedEffect
        if (isAnimatedPng(payload)) {
            drawable = runCatching {
                withContext(Dispatchers.Default) {
                    APNGDrawable(SteamChatByteBufferLoader(payload)).apply {
                        // Let ImageView visibility start/stop the decoder. Explicitly
                        // calling start() below also covers Compose re-attachment.
                        setAutoPlay(true)
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
            image = decodeStaticSteamImage(payload)
        }
        // A malformed animation should never leave a permanent placeholder.
        if (drawable == null && image == null) image = decodeStaticSteamImage(payload)
    }
    val currentDrawable = drawable
    val animated = currentDrawable as? Animatable
    DisposableEffect(animated) {
        onDispose {
            animated?.stop()
        }
    }
    LaunchedEffect(animated, playAnimation) {
        if (playAnimation) animated?.start() else animated?.stop()
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
                if (view.drawable !== currentDrawable) view.setImageDrawable(currentDrawable)
                view.scaleType = imageScaleType(mode)
                view.contentDescription = contentDescription
                if (playAnimation && !animated.isRunning) animated.start()
                if (!playAnimation && animated.isRunning) animated.stop()
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
                if (view.drawable !== drawable) view.setImageDrawable(drawable)
                view.scaleType = imageScaleType(mode)
                view.contentDescription = contentDescription
            }
        )
        image != null -> Image(
            painter = BitmapPainter(
                image = requireNotNull(image),
                filterQuality = if (mode == SteamChatRemoteImageMode.EMOTICON) {
                    // Steam emoticons are pixel art. Bilinear filtering is the
                    // reason they looked like blurred coloured blobs when enlarged.
                    FilterQuality.None
                } else {
                    FilterQuality.High
                }
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

private suspend fun decodeStaticSteamImage(payload: ByteArray): ImageBitmap? =
    withContext(Dispatchers.Default) {
        val options = BitmapFactory.Options().apply {
            // Keep the CDN's actual pixels; density-based decoding would make
            // the already-small Steam assets even less predictable.
            inScaled = false
            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
        }
        BitmapFactory.decodeByteArray(payload, 0, payload.size, options)?.asImageBitmap()
    }

private fun imageScaleType(mode: SteamChatRemoteImageMode): ImageView.ScaleType =
    if (mode == SteamChatRemoteImageMode.STICKER) {
        ImageView.ScaleType.CENTER_INSIDE
    } else {
        ImageView.ScaleType.FIT_CENTER
    }

private class SteamChatByteBufferLoader(
    private val payload: ByteArray
) : ByteBufferLoader() {
    override fun getByteBuffer(): ByteBuffer = ByteBuffer.wrap(payload)
}
