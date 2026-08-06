package takagi.ru.monica.steam.foundation.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

internal val LocalSteamAvatarShape = staticCompositionLocalOf<Shape> {
    RectangleShape
}
@Composable
internal fun ProvideSteamAvatarShape(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context) { SteamAvatarShapePreferences(context) }
    val option by preferences.shape.collectAsState(initial = SteamAvatarShapeOption.SQUARE)

    CompositionLocalProvider(
        LocalSteamAvatarShape provides option.steamAvatarShape(),
        content = content
    )
}

internal fun SteamAvatarShapeOption.steamAvatarShape(): Shape = when (this) {
    SteamAvatarShapeOption.SQUARE -> RectangleShape
    SteamAvatarShapeOption.ROUNDED -> RoundedCornerShape(12.dp)
    SteamAvatarShapeOption.CIRCLE -> CircleShape
}
