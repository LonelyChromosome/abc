package com.lonelychromosome.parkinglab.gate

/**
 * Read-only event reconciliation for a future authorized gate-history source.
 * No authentication, network requests, Firebase interception or hidden-session access.
 */
enum class GateDirection { ENTER, EXIT, UNKNOWN }

data class GateRecord(
    val id: String,
    val direction: GateDirection,
    val occurredAtMs: Long
)

data class GateArrival(
    val event: GateRecord,
    val firstObservedAtMs: Long,
    val sourceLagMs: Long
)

class GateUpdateReconciler(
    private val maxSeen: Int = 256,
    private val freshWindowMs: Long = 120_000L
) {
    init {
        require(maxSeen > 0)
        require(freshWindowMs >= 0L)
    }

    private val seen = LinkedHashSet<String>()
    private var initialized = false

    /**
     * First snapshot is treated as existing history and emits no new events.
     * Later snapshots emit only fresh ENTER events not observed previously.
     * This is NOT a live API connection or a timer-based polling loop.
     */
    @Synchronized fun observe(snapshot: List<GateRecord>, observedAtMs: Long): List<GateArrival> {
        val valid = snapshot.filter { it.id.isNotBlank() && it.occurredAtMs > 0L }
            .distinctBy { it.id }
        if (!initialized) {
            for (event in valid) remember(event.id)
            initialized = true
            return emptyList()
        }
        val newEvents = mutableListOf<GateArrival>()
        for (event in valid.sortedBy { it.occurredAtMs }) {
            if (!seen.add(event.id)) continue
            trimSeen()
            val lag = observedAtMs - event.occurredAtMs
            if (event.direction == GateDirection.ENTER && lag in 0L..freshWindowMs) {
                newEvents.add(GateArrival(event, observedAtMs, lag))
            }
        }
        return newEvents
    }

    @Synchronized fun reset() {
        seen.clear()
        initialized = false
    }

    private fun remember(id: String) {
        seen.add(id)
        trimSeen()
    }

    private fun trimSeen() {
        while (seen.size > maxSeen) {
            val oldest = seen.iterator().next()
            seen.remove(oldest)
        }
    }
}

data class GateLatencyComparison(
    val sourceLagMs: Long,
    val notificationLagMs: Long?
) {
    val advantageMs: Long? get() = notificationLagMs?.minus(sourceLagMs)
}
