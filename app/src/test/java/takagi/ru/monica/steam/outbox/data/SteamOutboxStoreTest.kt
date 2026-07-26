package takagi.ru.monica.steam.outbox.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.outbox.domain.SteamOutboxEvent
import takagi.ru.monica.steam.outbox.domain.SteamOutboxOperation
import takagi.ru.monica.steam.outbox.domain.SteamOutboxStatus

class SteamOutboxStoreTest {
    @Test
    fun enqueueIsIdempotentAndPayloadIsProtectedAtRest() = runTest {
        val dao = MemoryOutboxDao()
        val store = SteamOutboxStore(
            dao = dao,
            protectPayload = { "encrypted:$it" },
            revealPayload = { it.removePrefix("encrypted:") },
            nowMillis = { 1_000L }
        )
        val first = store.enqueue(command(id = "request-1", dedupe = "same"))
        val duplicate = store.enqueue(command(id = "request-2", dedupe = "same"))

        assertEquals("request-1", first.id)
        assertEquals("request-1", duplicate.id)
        assertEquals("payload-request-1", first.payload)
        val stored = requireNotNull(dao.getById("request-1"))
        assertTrue(stored.payload.startsWith("encrypted:"))
        assertTrue(stored.accountSteamId.startsWith("encrypted:"))
        assertEquals(null, dao.getById("request-2"))
    }

    @Test
    fun claimsInCreationOrderAndRecoversUncertainWrites() = runTest {
        val dao = MemoryOutboxDao()
        val store = SteamOutboxStore(
            dao = dao,
            protectPayload = { it },
            revealPayload = { it },
            nowMillis = { 5_000L }
        )
        store.enqueue(command(id = "first", dedupe = "first", createdAt = 1_000L))
        store.enqueue(command(id = "second", dedupe = "second", createdAt = 2_000L))

        val claimed = requireNotNull(store.claimNext(accountId = 1L, now = 5_000L))
        assertEquals("first", claimed.id)
        assertEquals(SteamOutboxStatus.IN_FLIGHT, claimed.status)
        assertEquals(1, claimed.attemptCount)

        val awaiting = store.transition(
            id = claimed.id,
            event = SteamOutboxEvent.AWAIT_CONFIRMATION,
            now = 6_000L
        )
        assertEquals(SteamOutboxStatus.AWAITING_CONFIRMATION, awaiting.status)
        assertEquals(listOf("first", "second"), store.recoverable(1L).map { it.id })

        val completed = store.transition(
            id = claimed.id,
            event = SteamOutboxEvent.COMPLETE,
            now = 7_000L
        )
        assertEquals(SteamOutboxStatus.COMPLETED, completed.status)
        assertEquals(listOf("second"), store.recoverable(1L).map { it.id })
    }

    private fun command(
        id: String,
        dedupe: String,
        createdAt: Long = 1_000L
    ) = SteamOutboxCommand(
        id = id,
        accountId = 1L,
        accountSteamId = "76561198000000001",
        operation = SteamOutboxOperation.FRIEND_MESSAGE,
        dedupeKey = dedupe,
        payload = "payload-$id",
        createdAtMillis = createdAt
    )
}

private class MemoryOutboxDao : SteamOutboxDao {
    private val items = linkedMapOf<String, SteamOutboxEntity>()

    override suspend fun insert(item: SteamOutboxEntity): Long {
        if (items.containsKey(item.id) || items.values.any { it.dedupeKey == item.dedupeKey }) {
            return -1L
        }
        items[item.id] = item
        return 1L
    }

    override suspend fun getById(id: String): SteamOutboxEntity? = items[id]

    override suspend fun getByDedupeKey(dedupeKey: String): SteamOutboxEntity? =
        items.values.firstOrNull { it.dedupeKey == dedupeKey }

    override suspend fun findReady(
        accountId: Long,
        nowMillis: Long,
        limit: Int
    ): List<SteamOutboxEntity> = items.values
        .filter { it.accountId == accountId }
        .filter { it.status == "QUEUED" || it.status == "RETRYABLE" }
        .filter { it.nextAttemptAtMillis <= nowMillis }
        .sortedWith(compareBy<SteamOutboxEntity> { it.createdAtMillis }.thenBy { it.id })
        .take(limit)

    override suspend fun claim(id: String, nowMillis: Long): Int {
        val current = items[id] ?: return 0
        if (current.status != "QUEUED" && current.status != "RETRYABLE") return 0
        if (current.nextAttemptAtMillis > nowMillis) return 0
        items[id] = current.copy(
            status = "IN_FLIGHT",
            attemptCount = current.attemptCount + 1,
            updatedAtMillis = nowMillis,
            lastError = null
        )
        return 1
    }

    override suspend fun transition(
        id: String,
        expectedStatus: String,
        status: String,
        attemptCount: Int,
        nextAttemptAtMillis: Long,
        updatedAtMillis: Long,
        lastError: String?
    ): Int {
        val current = items[id] ?: return 0
        if (current.status != expectedStatus) return 0
        items[id] = current.copy(
            status = status,
            attemptCount = attemptCount,
            nextAttemptAtMillis = nextAttemptAtMillis,
            updatedAtMillis = updatedAtMillis,
            lastError = lastError
        )
        return 1
    }

    override suspend fun findRecoverable(accountId: Long): List<SteamOutboxEntity> =
        items.values
            .filter { it.accountId == accountId }
            .filter { it.status in setOf("QUEUED", "IN_FLIGHT", "AWAITING_CONFIRMATION", "RETRYABLE") }
            .sortedWith(compareBy<SteamOutboxEntity> { it.createdAtMillis }.thenBy { it.id })

    override suspend fun deleteById(id: String) {
        items.remove(id)
    }

    override suspend fun pruneTerminal(beforeMillis: Long): Int {
        val ids = items.values
            .filter { it.status in setOf("COMPLETED", "CANCELLED", "PERMANENT_FAILURE") }
            .filter { it.updatedAtMillis < beforeMillis }
            .map { it.id }
        ids.forEach(items::remove)
        return ids.size
    }
}
