package takagi.ru.monica.steam.friends.chat.richmedia.ui

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import takagi.ru.monica.steam.foundation.ui.loadSteamRemoteBytes

@Composable
internal fun SteamChatRemoteImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    playAnimation: Boolean = true
) {
    val context = LocalContext.current
    var drawable by remember(url) { mutableStateOf<Drawable?>(null) }
    var image by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        val payload = loadSteamRemoteBytes(context, url) ?: return@LaunchedEffect
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            drawable = runCatching {
                withContext(Dispatchers.Default) {
                    ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(payload)))
                }
            }.getOrNull()
        } else {
            image = withContext(Dispatchers.Default) {
                android.graphics.BitmapFactory.decodeByteArray(payload, 0, payload.size)?.asImageBitmap()
            }
        }
    }
    val animated = drawable as? AnimatedImageDrawable
    DisposableEffect(animated, playAnimation) {
        if (playAnimation) animated?.start() else animated?.stop()
        onDispose { animated?.stop() }
    }
    when {
        animated != null -> AndroidView(
            factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.CENTER_INSIDE } },
            modifier = modifier,
            update = { view ->
                if (view.drawable !== animated) view.setImageDrawable(animated)
                if (playAnimation && !animated.isRunning) animated.start()
                if (!playAnimation && animated.isRunning) animated.stop()
            }
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && drawable != null -> AndroidView(
            factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.CENTER_INSIDE } },
            modifier = modifier,
            update = { view -> if (view.drawable !== drawable) view.setImageDrawable(drawable) }
        )
        image != null -> Image(
            bitmap = requireNotNull(image),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
        else -> Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Icon(Icons.Default.EmojiEmotions, contentDescription = contentDescription)
        }
    }
}
