import com.lonelychromosome.parkinglab.gate.*

fun main() {
    val time = 1_760_000_000_000L
    val watcher = GateUpdateReconciler()
    val old = GateRecord("entry-old", GateDirection.ENTER, time - 360_000)
    check(watcher.observe(listOf(old), time).isEmpty())
    val fresh = GateRecord("entry-new", GateDirection.ENTER, time + 5_000)
    val exit = GateRecord("exit-new", GateDirection.EXIT, time + 6_000)
    val at = time + 9_000
    val emitted = watcher.observe(listOf(exit, fresh, old), at)
    check(emitted.size == 1 && emitted.single().event == fresh)
    check(emitted.single().sourceLagMs == 4_000L)
    check(watcher.observe(listOf(fresh, exit), at + 1_000).isEmpty())
    check(watcher.observe(listOf(GateRecord("stale", GateDirection.ENTER, time - 200_000)), at).isEmpty())
    check(watcher.observe(listOf(GateRecord("bad", GateDirection.UNKNOWN, at)), at).isEmpty())
    check(GateLatencyComparison(4_000, 121_000).advantageMs == 117_000L)
    watcher.reset()
    check(watcher.observe(listOf(fresh), at).isEmpty())
    println("OK: baseline, entry, exit, dedup, stale, latency, reset")
}
