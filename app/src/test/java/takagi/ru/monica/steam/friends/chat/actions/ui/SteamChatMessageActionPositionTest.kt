package takagi.ru.monica.steam.friends.chat.actions.ui

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class SteamChatMessageActionPositionTest {
    @Test
    fun centersPopupOnTouchAndClampsItInsideWindow() {
        val centered = TouchCenteredPositionProvider(IntOffset(500, 800), edgeMargin = 16)
            .calculatePosition(
                anchorBounds = IntRect.Zero,
                windowSize = IntSize(1080, 1920),
                layoutDirection = LayoutDirection.Ltr,
                popupContentSize = IntSize(300, 400)
            )
        assertEquals(IntOffset(350, 600), centered)

        val clamped = TouchCenteredPositionProvider(IntOffset(10, 20), edgeMargin = 16)
            .calculatePosition(
                anchorBounds = IntRect.Zero,
                windowSize = IntSize(1080, 1920),
                layoutDirection = LayoutDirection.Ltr,
                popupContentSize = IntSize(300, 400)
            )
        assertEquals(IntOffset(16, 16), clamped)
    }
}
