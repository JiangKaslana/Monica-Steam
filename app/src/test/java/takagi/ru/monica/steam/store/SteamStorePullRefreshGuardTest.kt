package takagi.ru.monica.steam.store

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamStorePullRefreshGuardTest {
    @Test
    fun homeUsesRealRefreshStateExpressiveIndicatorAndSettingsMenu() {
        val store = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreScreen.kt"
        ).readText()
        val controls = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/foundation/ui/SteamPageRefreshControls.kt"
        ).readText()

        assertTrue(store.contains("SteamPageOverflowMenu("))
        assertTrue(store.contains("onOpenSettings = onOpenSettings"))
        assertTrue(store.contains("SteamExpressivePullToRefresh("))
        assertTrue(store.contains("refreshing = state.loadingHome"))
        assertTrue(store.contains("viewModel.loadHome(force = true)"))
        assertTrue(controls.contains("LoadingIndicator("))
        assertTrue(controls.contains("progress = { state.distanceFraction.coerceIn(0f, 1f) }"))
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (directory.parentFile != null && !File(directory, "settings.gradle").exists()) {
            directory = directory.parentFile!!.canonicalFile
        }
        return File(directory, path)
    }
}
