package takagi.ru.monica.steam.store

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamStorePurchaseContextUiGuardTest {
    @Test
    fun purchaseContextLivesInAFocusedModuleAndIsWiredIntoDetail() {
        val root = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/purchase"
        )
        val screen = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreScreen.kt"
        ).readText()
        val viewModel = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/presentation/SteamStoreViewModel.kt"
        ).readText()
        val component = root.resolve("ui/SteamStorePurchaseContextSection.kt")
        val componentSource = component.readText()
        val packageOptionsSource = componentSource
            .substringAfter("private fun PackageOptionsCard(")
            .substringBefore("private fun RelatedAppsCard(")

        assertTrue(root.resolve("domain").isDirectory)
        assertTrue(root.resolve("data").isDirectory)
        assertTrue(root.resolve("ui").isDirectory)
        assertTrue(component.readLines().size <= 400)
        assertTrue(screen.contains("SteamStorePurchaseContextSection("))
        assertTrue(screen.contains("selectedPackageId"))
        assertTrue(screen.contains("onOpenRelatedApp"))
        assertTrue(screen.contains("viewModel.addDetailToCart(detail, packageOption)"))
        assertTrue(viewModel.contains("purchaseContextCache?.load(account.steamId, appId)"))
        assertTrue(viewModel.contains("steamStorePurchaseContextRequestIsCurrent"))
        assertTrue(componentSource.contains("PackageOptionsCard("))
        assertTrue(componentSource.contains("RelatedAppsCard("))
        assertTrue(componentSource.contains("SteamStoreOwnershipStatus.FAMILY_SHARED"))
        assertTrue(packageOptionsSource.contains("verticalAlignment = Alignment.Top"))
        assertTrue(packageOptionsSource.contains("formatSteamPrice(option.priceCents, currency)"))
        assertFalse(packageOptionsSource.contains("maxLines ="))
        assertFalse(packageOptionsSource.contains("TextOverflow.Ellipsis"))
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = directory.parentFile!!
        }
        return File(directory, path)
    }
}
