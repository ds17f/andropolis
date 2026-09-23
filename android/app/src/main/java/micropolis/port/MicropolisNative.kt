package micropolis.port

/**
 * The JNI surface over the C ABI (micropolis_c.h). One external fun per native
 * function. The handle is the native MicropolisEngine* as a Long. Not
 * thread-safe: call all methods for one handle from one thread.
 */
object MicropolisNative {
    init { System.loadLibrary("micropolis") }

    external fun create(): Long
    external fun destroy(handle: Long)
    external fun init(handle: Long)
    external fun generateRandomCity(handle: Long)
    external fun simTick(handle: Long)
    external fun doTool(handle: Long, tool: Int, x: Int, y: Int): Int
    external fun mapWidth(): Int
    external fun mapHeight(): Int

    /** Fills dst (length >= W*H) with tiles, column-major dst[x*H + y]. */
    external fun copyTiles(handle: Long, dst: ShortArray): Int

    /**
     * Fills dst (length >= 10) with:
     * [cityTime, totalFunds, cityPop, cityScore, cityYear, cityMonth,
     *  resDemand, comDemand, indDemand, gameLevel].
     */
    external fun getStats(handle: Long, dst: IntArray)

    /**
     * Fills dst (length >= 12): [totalFunds, taxRate, taxIncome, roadFund, roadSpend, roadPct,
     *  policeFund, policeSpend, policePct, fireFund, fireSpend, firePct].
     */
    external fun getBudget(handle: Long, dst: IntArray)

    /**
     * Fills dst (length >= 7): [score, scoreDelta, cityClass, pop, popDelta, assessedValue, approval].
     */
    external fun getEvaluation(handle: Long, dst: IntArray)

    /** Save the city to `path`. Returns 1 on success, 0 on failure. */
    external fun saveCity(handle: Long, path: String): Int
    /** Load a city from `path`. Returns 1 on success, 0 on failure. */
    external fun loadCity(handle: Long, path: String): Int

    /** Set the city tax rate (percent, 0..20). */
    external fun setCityTax(handle: Long, tax: Int)

    /**
     * Fill dst (ByteArray, len >= 12000) column-major dst[x*H+y] with 0..255
     * intensity for the overlay (see MicropolisOverlay). Returns tiles written.
     */
    external fun copyOverlay(handle: Long, overlay: Int, dst: ByteArray): Int
    /** Fill dst (len >= 120) oldest-first (dst[119] newest). Returns samples. */
    external fun getHistory(handle: Long, history: Int, scale: Int, dst: IntArray): Int
    external fun makeDisaster(handle: Long, disaster: Int)
    external fun setFunding(handle: Long, roadPct: Int, firePct: Int, policePct: Int)
    external fun setAutoBudget(handle: Long, on: Int)
}
