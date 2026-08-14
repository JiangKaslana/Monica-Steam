package takagi.ru.monica.steam.network.optimization

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Protocol
import takagi.ru.monica.steam.network.optimization.domain.SteamHostsRuleParser
import takagi.ru.monica.steam.network.optimization.domain.SteamNetworkTargetCatalog

/**
 * Feeds real connection outcomes back into [SteamRouteHealthRuntime].
 *
 * Only direct Steam connections are observed. Proxy endpoints are deliberately ignored so their
 * address can never be mistaken for a Steam origin route.
 */
internal class SteamRouteHealthEventListener : EventListener() {
    override fun connectEnd(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?
    ) {
        if (proxy.type() != Proxy.Type.DIRECT) return
        val address = inetSocketAddress.address ?: return
        val hostname = normalizedSteamHostname(call) ?: return
        if (!SteamHostsRuleParser.isUsableAddress(address)) return
        SteamRouteHealthRuntime.recordSuccess(hostname, address)
    }

    override fun connectFailed(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?,
        ioe: IOException
    ) {
        if (proxy.type() != Proxy.Type.DIRECT) return
        val address = inetSocketAddress.address ?: return
        val hostname = normalizedSteamHostname(call) ?: return
        if (!SteamHostsRuleParser.isUsableAddress(address)) return
        SteamRouteHealthRuntime.recordFailure(hostname, address)
    }

    private fun normalizedSteamHostname(call: Call): String? {
        val hostname = SteamHostsRuleParser.normalizeHostname(call.request().url.host)
        return hostname.takeIf(SteamNetworkTargetCatalog::isSteamHostname)
    }
}
