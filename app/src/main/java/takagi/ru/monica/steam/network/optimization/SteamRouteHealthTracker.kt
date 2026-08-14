package takagi.ru.monica.steam.network.optimization

import java.net.InetAddress
import takagi.ru.monica.steam.network.optimization.domain.SteamHostsRuleParser

/**
 * Session-scoped health memory for Steam routes.
 *
 * DNS/DoH remains the source of addresses. This tracker only changes the order in which already
 * resolved addresses are tried: a recently successful route becomes sticky for a short period,
 * while failed routes are temporarily moved behind healthy alternatives. No address is removed,
 * persisted, or trusted without the normal TLS verification performed by the caller.
 */
internal class SteamRouteHealthTracker(
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val stickyTtlMillis: Long = DEFAULT_STICKY_TTL_MILLIS,
    private val baseCooldownMillis: Long = DEFAULT_BASE_COOLDOWN_MILLIS,
    private val maxCooldownMillis: Long = DEFAULT_MAX_COOLDOWN_MILLIS
) {
    private data class RouteKey(
        val hostname: String,
        val address: String
    )

    private data class RouteState(
        var consecutiveFailures: Int = 0,
        var cooldownUntilMillis: Long = 0L,
        var lastSuccessAtMillis: Long = Long.MIN_VALUE,
        var lastFailureAtMillis: Long = Long.MIN_VALUE,
        var lastTouchedAtMillis: Long = 0L
    )

    private val states = LinkedHashMap<RouteKey, RouteState>()

    @Synchronized
    fun recordSuccess(hostname: String, address: InetAddress) {
        val now = clockMillis()
        val key = key(hostname, address)
        val state = states.getOrPut(key) { RouteState() }
        state.consecutiveFailures = 0
        state.cooldownUntilMillis = 0L
        state.lastSuccessAtMillis = now
        state.lastTouchedAtMillis = now
        pruneIfNeeded()
    }

    @Synchronized
    fun recordFailure(hostname: String, address: InetAddress) {
        val now = clockMillis()
        val key = key(hostname, address)
        val state = states.getOrPut(key) { RouteState() }
        if (
            state.lastFailureAtMillis != Long.MIN_VALUE &&
            now - state.lastFailureAtMillis > FAILURE_COUNT_RESET_MILLIS
        ) {
            state.consecutiveFailures = 0
        }
        state.consecutiveFailures = (state.consecutiveFailures + 1)
            .coerceAtMost(MAX_FAILURE_EXPONENT + 1)
        val exponent = (state.consecutiveFailures - 1).coerceAtMost(MAX_FAILURE_EXPONENT)
        val cooldown = (baseCooldownMillis * (1L shl exponent))
            .coerceAtMost(maxCooldownMillis)
        state.cooldownUntilMillis = now + cooldown
        state.lastFailureAtMillis = now
        state.lastTouchedAtMillis = now
        pruneIfNeeded()
    }

    @Synchronized
    fun rank(hostname: String, addresses: List<InetAddress>): List<InetAddress> {
        if (addresses.size <= 1) return addresses
        val normalizedHost = normalizeHostname(hostname)
        val unique = addresses.distinctBy(InetAddress::getHostAddress)
        if (unique.size <= 1) return unique

        val now = clockMillis()
        val indexed = unique.mapIndexed { index, address ->
            val state = states[RouteKey(normalizedHost, address.hostAddress.orEmpty())]
            RankedRoute(
                address = address,
                originalIndex = index,
                state = state,
                coolingDown = state != null && now < state.cooldownUntilMillis
            )
        }

        val sticky = indexed
            .asSequence()
            .filter { !it.coolingDown }
            .filter { route ->
                val lastSuccess = route.state?.lastSuccessAtMillis ?: Long.MIN_VALUE
                lastSuccess != Long.MIN_VALUE && now - lastSuccess <= stickyTtlMillis
            }
            .maxByOrNull { it.state?.lastSuccessAtMillis ?: Long.MIN_VALUE }

        val healthy = indexed.filter { !it.coolingDown && it !== sticky }
        val cooling = indexed
            .filter { it.coolingDown }
            .sortedWith(
                compareBy<RankedRoute> { it.state?.cooldownUntilMillis ?: Long.MAX_VALUE }
                    .thenBy { it.originalIndex }
            )

        return buildList(unique.size) {
            sticky?.let { add(it.address) }
            healthy.forEach { add(it.address) }
            cooling.forEach { add(it.address) }
        }
    }

    @Synchronized
    fun clear() {
        states.clear()
    }

    private fun key(hostname: String, address: InetAddress): RouteKey = RouteKey(
        hostname = normalizeHostname(hostname),
        address = address.hostAddress.orEmpty()
    )

    private fun normalizeHostname(hostname: String): String =
        SteamHostsRuleParser.normalizeHostname(hostname)

    private fun pruneIfNeeded() {
        if (states.size <= MAX_TRACKED_ROUTES) return
        val removeCount = states.size - MAX_TRACKED_ROUTES
        states.entries
            .sortedBy { it.value.lastTouchedAtMillis }
            .take(removeCount)
            .forEach { states.remove(it.key) }
    }

    private data class RankedRoute(
        val address: InetAddress,
        val originalIndex: Int,
        val state: RouteState?,
        val coolingDown: Boolean
    )

    private companion object {
        const val MAX_TRACKED_ROUTES = 512
        const val MAX_FAILURE_EXPONENT = 4
        const val DEFAULT_STICKY_TTL_MILLIS = 10 * 60 * 1_000L
        const val DEFAULT_BASE_COOLDOWN_MILLIS = 30 * 1_000L
        const val DEFAULT_MAX_COOLDOWN_MILLIS = 5 * 60 * 1_000L
        const val FAILURE_COUNT_RESET_MILLIS = 10 * 60 * 1_000L
    }
}

/** Shared only for the current app process; intentionally never persisted. */
internal object SteamRouteHealthRuntime {
    private val tracker = SteamRouteHealthTracker()

    fun rank(hostname: String, addresses: List<InetAddress>): List<InetAddress> =
        tracker.rank(hostname, addresses)

    fun recordSuccess(hostname: String, address: InetAddress) {
        tracker.recordSuccess(hostname, address)
    }

    fun recordFailure(hostname: String, address: InetAddress) {
        tracker.recordFailure(hostname, address)
    }

    fun clear() {
        tracker.clear()
    }
}
