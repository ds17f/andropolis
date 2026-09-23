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

    /**
     * Citizens' worst problems from the last evaluation, ranked. Fills dst (length >= 8):
     * dst[0..3] = problem ids (0 Crime, 1 Pollution, 2 Housing, 3 Taxes, 4 Traffic,
     * 5 Unemployment, 6 Fire), dst[4..7] = % of citizens naming each. Returns the count (0..4).
     */
    external fun getProblems(handle: Long, dst: IntArray): Int

    /** Save the city to `path`. Returns 1 on success, 0 on failure. */
    external fun saveCity(handle: Long, path: String): Int
    /** Load a city from `path`. Returns 1 on success, 0 on failure. */
    external fun loadCity(handle: Long, path: String): Int

    /** Set the city tax rate (percent, 0..20). */
    external fun setCityTax(handle: Long, tax: Int)

    /** Set the engine frame-skip mode: 1=Slow (every 5th tick), 2=Medium (every 3rd), 3=Fast (every tick). */
    external fun setSpeed(handle: Long, speed: Int)

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

    /** Overwrite one map tile (raw 16-bit value incl. flags, passed as 0..65535). Used by undo. */
    external fun setTile(handle: Long, x: Int, y: Int, value: Int)

    /** Set the city treasury. Used by undo to refund / re-charge a build. */
    external fun setFunds(handle: Long, funds: Int)

    /**
     * Active moving objects (train, helicopter, plane, ship, monster, tornado, explosion,
     * bus), 4 ints each in dst: [type 1..8, frame 1.., left, top]; left/top are world
     * pixels (16 per tile) of the image's top-left. Image asset:
     * "sprites/sprite_<type>_<frame-1>.png". Returns the count (<= dst.size / 4).
     */
    external fun copySprites(handle: Long, dst: IntArray): Int

    /**
     * Start an original scenario from its .cty file at `path` (a real file; copy the asset
     * first): 1 Dullsville, 2 San Francisco, 3 Hamburg, 4 Bern, 5 Tokyo, 6 Detroit,
     * 7 Boston, 8 Rio. Returns 1 on success.
     */
    external fun loadScenario(handle: Long, scenario: Int, path: String): Int

    /** Terrain for the next generateRandomCity. -1 = random. See micropolis_c.h. */
    external fun setTerrain(handle: Long, trees: Int, lakes: Int, river: Int, island: Int)

    /** The engine's PRNG state (saved with a background-play anchor; see DESIGN.md 12.5). */
    external fun getRng(handle: Long): Long

    /**
     * Load a city for the background timeline: the post-load init uses PRNG state `rng`
     * instead of a clock seed, so loadCitySeeded + N ticks is the same on every run.
     * Returns 1 on success.
     */
    external fun loadCitySeeded(handle: Long, path: String, rng: Long): Int

    /** Turn the engine's own random disasters on (1) or off (0); manual makeDisaster still works. */
    external fun setEnableDisasters(handle: Long, on: Int)

    /**
     * Dequeue one engine event into out (length >= 9):
     * [type, x, y, a, b, c, d, e, f]. Returns true if an event was written, false
     * when the queue is empty. Call in a loop after each tick to drain. Event types:
     * 0 MESSAGE (a=messageIndex 1..57, b=picture, c=important), 1 ZONE_STATUS
     * (a=tileCat b=popDensity c=landValue d=crime e=pollution f=growth), 2 AUTO_GOTO,
     * 3 EARTHQUAKE (a=strength), 4 LOSE, 5 WIN.
     */
    external fun pollEvent(handle: Long, out: IntArray): Boolean
}
