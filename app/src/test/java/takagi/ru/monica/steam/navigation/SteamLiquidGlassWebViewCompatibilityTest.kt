package takagi.ru.monica.steam.navigation

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamLiquidGlassWebViewCompatibilityTest {
    @Test
    fun officialStoreWebViewDisablesRuntimeBackdropCaptureWithoutHidingDock() {
        val activity = projectFile(
            "app/src/main/java/takagi/ru/monica/MonicaSteamActivity.kt"
        ).readText()
        val store = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreScreen.kt"
        ).readText()
        val web = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreWebScreen.kt"
        ).readText()
        val dock = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/navigation/liquidglass/ui/SteamLiquidGlassDock.kt"
        ).readText()

        assertTrue(store.contains("onPlatformViewVisibilityChanged: (Boolean) -> Unit"))
        assertTrue(
            store.contains(
                "onPlatformViewVisibilityChanged = onPlatformViewVisibilityChanged"
            )
        )
        assertTrue(web.contains("platformViewVisibilityCallback(true)"))
        assertTrue(web.contains("platformViewVisibilityCallback(false)"))
        assertTrue(web.contains("withFrameNanos { }"))
        assertTrue(web.contains("else if (!platformViewReady)"))
        assertTrue(activity.contains("isPlatformViewActive"))
        assertTrue(activity.contains("platformViewActive = isPlatformViewActive"))
        assertTrue(activity.contains("enabled = liquidGlassEffectsEnabled"))
        assertTrue(activity.contains("runtimeEffectsEnabled = liquidGlassEffectsEnabled"))
        assertTrue(dock.contains("runtimeEffectsEnabled: Boolean"))
        assertTrue(
            dock.contains(
                "runtimeSupported = remember(runtimeEffectsEnabled)"
            )
        )
    }

    @Test
    fun renderingPolicyKeepsDockButRejectsRuntimeEffectsForPlatformViews() {
        assertTrue(
            shouldEnableSteamLiquidGlassRuntimeEffects(
                dockStyle = SteamDockStyle.LIQUID_GLASS,
                dockVisible = true,
                platformViewActive = false
            )
        )
        assertFalse(
            shouldEnableSteamLiquidGlassRuntimeEffects(
                dockStyle = SteamDockStyle.LIQUID_GLASS,
                dockVisible = true,
                platformViewActive = true
            )
        )
        assertFalse(
            shouldEnableSteamLiquidGlassRuntimeEffects(
                dockStyle = SteamDockStyle.M3E,
                dockVisible = true,
                platformViewActive = false
            )
        )
        assertFalse(
            shouldEnableSteamLiquidGlassRuntimeEffects(
                dockStyle = SteamDockStyle.LIQUID_GLASS,
                dockVisible = false,
                platformViewActive = false
            )
        )
    }

    private fun projectFile(path: String): File {
        var dir = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            dir.parentFile != null &&
            !File(dir, "settings.gradle").exists() &&
            !File(dir, "settings.gradle.kts").exists()
        ) {
            dir = dir.parentFile!!.canonicalFile
        }
        return File(dir, path)
    }
}
