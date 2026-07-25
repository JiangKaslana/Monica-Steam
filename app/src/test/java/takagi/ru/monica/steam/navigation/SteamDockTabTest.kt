package takagi.ru.monica.steam.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class SteamDockTabTest {
    @Test
    fun defaultOrderContainsChatWithTheSortableContentTabs() {
        assertEquals(
            listOf(
                SteamDockTab.STORE,
                SteamDockTab.LIBRARY,
                SteamDockTab.CHAT,
                SteamDockTab.SETTINGS
            ),
            SteamDockTab.DEFAULT_ORDER
        )
    }

    @Test
    fun sanitizeKeepsOnlyEnabledContentTabs() {
        assertEquals(
            listOf(SteamDockTab.SETTINGS),
            SteamDockTab.sanitizeOrder(
                listOf(SteamDockTab.SETTINGS, SteamDockTab.TOKEN, SteamDockTab.SETTINGS)
            )
        )
        assertEquals(
            listOf(
                SteamDockTab.SETTINGS,
                SteamDockTab.STORE,
                SteamDockTab.LIBRARY,
                SteamDockTab.CHAT
            ),
            SteamDockTab.completeOrder(listOf(SteamDockTab.SETTINGS))
        )
    }

    @Test
    fun legacyDefaultOrderMigratesButCustomOrderIsPreserved() {
        assertEquals(
            SteamDockTab.DEFAULT_ORDER,
            resolveStoredDockOrder(
                listOf(SteamDockTab.LIBRARY, SteamDockTab.STORE, SteamDockTab.SETTINGS)
            )
        )
        assertEquals(
            listOf(
                SteamDockTab.CHAT,
                SteamDockTab.SETTINGS,
                SteamDockTab.STORE,
                SteamDockTab.LIBRARY
            ),
            resolveStoredDockOrder(
                listOf(SteamDockTab.SETTINGS, SteamDockTab.STORE, SteamDockTab.LIBRARY)
            )
        )
        assertEquals(
            listOf(SteamDockTab.SETTINGS, SteamDockTab.STORE),
            resolveStoredDockOrder(
                stored = listOf(SteamDockTab.SETTINGS, SteamDockTab.STORE),
                chatMigrationComplete = true
            )
        )
    }

    @Test
    fun reorderHandlesFirstAndLastItemsWithoutIndexErrors() {
        assertEquals(
            listOf(
                SteamDockTab.LIBRARY,
                SteamDockTab.CHAT,
                SteamDockTab.SETTINGS,
                SteamDockTab.STORE
            ),
            reorderDockOrder(SteamDockTab.DEFAULT_ORDER, fromIndex = 0, toIndex = 3)
        )
        assertEquals(
            listOf(
                SteamDockTab.SETTINGS,
                SteamDockTab.STORE,
                SteamDockTab.LIBRARY,
                SteamDockTab.CHAT
            ),
            reorderDockOrder(SteamDockTab.DEFAULT_ORDER, fromIndex = 3, toIndex = 0)
        )
    }

    @Test
    fun reorderIgnoresLazyListHeaderIndicesInsteadOfThrowing() {
        assertEquals(
            SteamDockTab.DEFAULT_ORDER,
            reorderDockOrder(SteamDockTab.DEFAULT_ORDER, fromIndex = 4, toIndex = 1)
        )
        assertEquals(
            SteamDockTab.DEFAULT_ORDER,
            reorderDockOrder(SteamDockTab.DEFAULT_ORDER, fromIndex = 1, toIndex = 4)
        )
    }

    @Test
    fun dockSwipeMovesOnlyToAdjacentContentTab() {
        val order = SteamDockTab.DEFAULT_ORDER

        assertEquals(
            SteamDockTab.LIBRARY,
            dockSwipeTarget(order, SteamDockTab.STORE, totalDragPx = -80f, thresholdPx = 56f)
        )
        assertEquals(
            SteamDockTab.STORE,
            dockSwipeTarget(order, SteamDockTab.LIBRARY, totalDragPx = 80f, thresholdPx = 56f)
        )
        assertEquals(
            null,
            dockSwipeTarget(order, SteamDockTab.LIBRARY, totalDragPx = 20f, thresholdPx = 56f)
        )
    }

    @Test
    fun tokenSwipeEntersTheNearestEdgeOfTheContentDock() {
        val order = SteamDockTab.DEFAULT_ORDER

        assertEquals(
            SteamDockTab.STORE,
            dockSwipeTarget(order, SteamDockTab.TOKEN, totalDragPx = -80f, thresholdPx = 56f)
        )
        assertEquals(
            SteamDockTab.SETTINGS,
            dockSwipeTarget(order, SteamDockTab.TOKEN, totalDragPx = 80f, thresholdPx = 56f)
        )
    }
}
