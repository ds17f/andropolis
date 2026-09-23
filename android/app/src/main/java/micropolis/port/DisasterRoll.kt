package micropolis.port

/**
 * The app's monthly random-disaster roll (task 051), made deterministic: the result
 * depends only on (seed, monthKey, freq). Background play (DESIGN.md 12.12a) needs the
 * probe and the replay of a timeline to roll the same disasters.
 */
object DisasterRoll {
    private val yearsPer = intArrayOf(0, 10, 5, 1)          // average years between disasters
    private val kinds = intArrayOf(0, 0, 1, 2, 3, 4, 0, 5)   // fires most common, meltdown rarest

    /** The disaster kind for this game month (MicropolisNative.makeDisaster), or -1 for none. */
    fun roll(seed: Long, monthKey: Int, freq: Int): Int {
        if (freq <= 0 || freq >= yearsPer.size) return -1
        val r = java.util.Random(seed * 1_000_003L + monthKey)
        if (r.nextInt(yearsPer[freq] * 12) != 0) return -1
        return kinds[r.nextInt(kinds.size)]
    }
}
