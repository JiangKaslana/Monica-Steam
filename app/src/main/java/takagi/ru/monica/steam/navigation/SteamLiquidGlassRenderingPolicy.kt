package takagi.ru.monica.steam.navigation

/**
 * Runtime backdrop shaders cannot safely sample Android platform surfaces
 * such as WebView on every GPU. Keep the dock visible, but use its stable
 * material fallback while a platform surface participates in composition.
 */
internal fun shouldEnableSteamLiquidGlassRuntimeEffects(
    dockStyle: SteamDockStyle,
    dockVisible: Boolean,
    platformViewActive: Boolean
): Boolean = dockStyle == SteamDockStyle.LIQUID_GLASS &&
    dockVisible &&
    !platformViewActive
