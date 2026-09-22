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

    /** Save the city to `path`. Returns 1 on success, 0 on failure. */
    external fun saveCity(handle: Long, path: String): Int
    /** Load a city from `path`. Returns 1 on success, 0 on failure. */
    external fun loadCity(handle: Long, path: String): Int
}
