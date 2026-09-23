package micropolis.port

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * First light: create the engine, generate a random city, then tick and redraw
 * on a timer. The sim runs on a dedicated thread; touch events are marshaled
 * to that thread as messages.
 */
class MainActivity : AppCompatActivity() {

    internal var handle: Long = 0L
    internal var currentTool = MOVE_TOOL      // start with no tool: one finger moves the map
    internal var lastToolUseMs = 0L           // for the idle fallback to Move (ui thread)
    internal var idleToMove = true            // Settings: drop the tool after IDLE_TO_MOVE_MS
    internal val moveItem by lazy { ToolItem("Move", MOVE_TOOL, R.drawable.ic_move) }
    /** Once a second: drop an unused tool back to Move (Settings: idleToMove). */
    internal val idleCheck: Runnable = object : Runnable {
        override fun run() {
            if (idleToMove && currentTool != MOVE_TOOL &&
                android.os.SystemClock.uptimeMillis() - lastToolUseMs > IDLE_TO_MOVE_MS) selectTool(MOVE_TOOL)
            ui.postDelayed(this, 1000)
        }
    }
    internal lateinit var mapView: MapView
    internal lateinit var buf: ShortArray
    internal lateinit var sim: Handler
    internal lateinit var ui: Handler
    internal lateinit var sfx: SoundFx
    internal val statsBuf = IntArray(10)
    @Volatile internal var speed = 2   // 0=Pause 1=Slow 2=Med 3=Fast 4=Turbo
    internal var lastRunSpeed = 2
    internal val speedNames = arrayOf("Pause", "Slow", "Med", "Fast", "Turbo")
    // Engine ticks per second per UI speed. The engine always runs at its speed 3 (one
    // simulate() per tick), so the tick rate sets both the sim pace and how fast sprites
    // move, as in the original game. Sim pace matches the engine's own Slow/Med/Fast
    // frame-skip (every 5th / 3rd / every tick at 30 fps); Turbo ≈ 210 steps/s.
    internal val ticksPerSecond = doubleArrayOf(0.0, 6.0, 10.0, 30.0, 210.0)
    internal var tickDebt = 0.0   // fractional ticks carried to the next frame (sim thread)
    internal val taxRates = intArrayOf(0, 5, 7, 9, 12, 15, 20)
    internal var taxIdx = 2   // start at 7%
    internal val autosavePath by lazy { java.io.File(filesDir, "autosave.cty").absolutePath }
    internal val prefs by lazy { getSharedPreferences("micropolis", MODE_PRIVATE) }
    internal fun sanitize(name: String) = name.trim().replace(Regex("[^A-Za-z0-9 _-]"), "").ifEmpty { "City" }
    internal var cityName: String = "My City"
    @Volatile internal var cityReady = false      // true once a city exists (guard autosave)
    internal var lastAutosaveMs = 0L          // internal resume file (autosave.cty), every 30 s
    internal var lastPublicAutosaveMs = 0L    // timestamped autosave in Documents/Andropolis, every 5 min
    internal var pickerResume: () -> Unit = {}
    internal var disasterFreq = 2                                  // 0 Off, 1 Rare, 2 Normal, 3 Frequent
    internal val disasterFreqNames = arrayOf("Off", "Rare", "Normal", "Frequent")
    internal val disasterYearsPer = intArrayOf(0, 10, 5, 1)        // average years between disasters
    internal var lastDisasterMonth = -1
    internal val random = java.util.Random()

    // Load / Save-as go through the system file picker (see CitySaves).
    internal val loadPicker = registerForActivityResult(CitySaves.OpenCity()) { uri ->
        if (uri != null) {
            val name = CitySaves.cityNameFor(this, uri)
            cityName = name; prefs.edit().putString("cityName", name).apply(); toolbar.title = name
            sim.post {
                val tmp = CitySaves.tempFile(this)
                CitySaves.copyFromUri(this, uri, tmp)
                MicropolisNative.loadCity(handle, tmp.absolutePath)
                MicropolisNative.saveCity(handle, autosavePath)   // make restore-on-launch match
                ui.post { resetHistory() }                         // undo does not cross cities
            }
            showBanner("Loaded “$name”")
        }
        pickerResume()
    }
    internal val savePicker = registerForActivityResult(CitySaves.SaveCity()) { uri ->
        if (uri != null) {
            val name = CitySaves.cityNameFor(this, uri)
            cityName = name; prefs.edit().putString("cityName", name).apply(); toolbar.title = name
            sim.post {
                val tmp = CitySaves.tempFile(this)
                MicropolisNative.saveCity(handle, tmp.absolutePath)
                CitySaves.copyToUri(this, tmp, uri)
                MicropolisNative.saveCity(handle, autosavePath)
            }
            showBanner("Saved “$name”")
        }
        pickerResume()
    }

    // Per-build undo: each stroke records the tiles it changed and what it cost.
    internal val undoStack = ArrayDeque<BuildEdit>()   // newest last (ui thread)
    internal val redoStack = ArrayDeque<BuildEdit>()
    internal val undoCap = 24
    internal val strokeBefore = ShortArray(120 * 100)  // map at stroke start (sim thread)
    internal var strokeFundsBefore = 0                 // sim thread
    internal var strokeMinX = Int.MAX_VALUE; internal var strokeMinY = Int.MAX_VALUE
    internal var strokeMaxX = -1; internal var strokeMaxY = -1
    internal var currentOverlay = 0
    internal val overlayBuf = ByteArray(120 * 100)
    internal val overlayNames = arrayOf("Off","Population","Traffic","Pollution","Land value","Crime","Growth","Power")
    internal var annualReportEnabled = true
    internal var lastReportYear = -1
    internal var lastReportFunds = -1        // for the annual-report funds delta
    internal var lastReportApproval = -1     // for the annual-report approval delta
    internal var minimapNav = true           // Settings (default on): minimap navigation mode
    internal var autoGoto = true             // Settings: jump the map to events as they happen
    internal val navZoom = 5f                 // fixed zoom when minimap navigation is on
    internal val eventBuf = IntArray(9)
    internal lateinit var messageBanner: TextView
    internal val bannerHide = Runnable { messageBanner.visibility = View.GONE; viewBeforeJump = null }
    internal var lastEventTile: Pair<Int, Int>? = null
    internal var viewBeforeJump: FloatArray? = null
    internal val messageLog = ArrayDeque<LogEntry>()
    internal val messageText = arrayOf(
        "", "More residential zones needed", "More commercial zones needed",
        "More industrial zones needed", "More roads required", "Inadequate rail system",
        "Build a power plant", "Residents demand a stadium", "Industry requires a seaport",
        "Commerce requires an airport", "Pollution very high", "Crime very high",
        "Frequent traffic jams reported", "Citizens demand a fire department",
        "Citizens demand a police department", "Blackouts reported — check the power map",
        "Citizens upset: taxes too high", "Roads deteriorating — underfunded",
        "Fire departments need funding", "Police departments need funding", "Fire reported!",
        "A monster has been sighted!", "Tornado reported!", "Major earthquake reported!",
        "A plane has crashed!", "Shipwreck reported!", "A train crashed!",
        "A helicopter crashed!", "Unemployment is high", "YOUR CITY HAS GONE BROKE!",
        "Firebombing reported!", "Need more parks", "Explosion detected!",
        "Insufficient funds to build that", "Area must be bulldozed first",
        "Population has reached 2,000", "Population has reached 10,000",
        "Population has reached 50,000", "Population has reached 100,000",
        "Population has reached 500,000", "Brownouts — build another power plant",
        "Heavy traffic reported", "Flooding reported!", "A nuclear meltdown has occurred!",
        "They're rioting in the streets!", "Started a new city", "Restored a saved city",
        "You won the scenario!", "You lost the scenario", "About Micropolis"
    )
    // engine gToolSize, index = tool value; default 1 for anything past the table
    internal val toolFootprints = intArrayOf(3,3,3,3, 3,1,1,1, 1,1,4,1, 4,4,4,6, 1,1,1,1)
    internal fun footprintOf(tool: Int) = toolFootprints.getOrElse(tool) { 1 }
    // engine gCostOf, index = tool value
    internal val toolCosts = intArrayOf(100,100,100,500, 500,0,5,1, 20,10,5000,10, 3000,3000,5000,10000, 100,0,0,0)
    internal fun costOf(tool: Int) = toolCosts.getOrElse(tool) { 0 }
    // history graph colors and labels (res, com, ind, money, crime, poll)
    internal val histColors = intArrayOf(0xFF4CAF50.toInt(), 0xFF42A5F5.toInt(), 0xFFF5A623.toInt(),
        0xFFEEF2F6.toInt(), 0xFFE5533D.toInt(), 0xFF9C6ADE.toInt())   // money = white (distinct from residential green)
    internal val histVisible = BooleanArray(6) { true }   // graph line toggles (persist while app runs)
    internal val histNames = arrayOf("Residential", "Commercial", "Industrial", "Money", "Crime", "Pollution")
    internal lateinit var topBar: LinearLayout
    internal lateinit var toolbar: com.google.android.material.appbar.MaterialToolbar
    @Volatile internal var simSuspended = false   // true while background play owns the city
    internal var dateText = ""                 // "Nov 2186", shown in the toolbar subtitle
    internal lateinit var fundsValue: android.widget.TextView
    internal lateinit var popValue: android.widget.TextView
    internal lateinit var scoreValue: android.widget.TextView
    internal lateinit var minimap: MinimapView
    internal lateinit var undoBtn: View                 // BottomBar.kt
    internal lateinit var toolFab: com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
    internal lateinit var dropToolFab: View             // small ✕ above the tool FAB; hidden in Move
    internal lateinit var simState: TextView
    internal lateinit var overlayState: TextView
    internal lateinit var mapContainer: android.widget.FrameLayout

    // Road / rail / wire draw straight axis-locked lines when dragged.
    internal fun isStraightLineTool(tool: Int) = tool == 6 || tool == 8 || tool == 9
    internal val toolCategories = linkedMapOf(
        "Zones" to listOf(ToolItem("Residential", 0, R.drawable.ic_residential), ToolItem("Commercial", 1, R.drawable.ic_commercial), ToolItem("Industrial", 2, R.drawable.ic_industrial), ToolItem("Park", 11, R.drawable.ic_park)),
        "Transport" to listOf(ToolItem("Road", 9, R.drawable.ic_road), ToolItem("Rail", 8, R.drawable.ic_rail), ToolItem("Wire", 6, R.drawable.ic_wire), ToolItem("Bulldozer", 7, R.drawable.ic_bulldozer)),
        "Services & Power" to listOf(ToolItem("Police", 4, R.drawable.ic_police), ToolItem("Fire", 3, R.drawable.ic_fire), ToolItem("Coal", 13, R.drawable.ic_coal), ToolItem("Nuclear", 14, R.drawable.ic_nuclear)),
        "Special" to listOf(ToolItem("Stadium", 10, R.drawable.ic_stadium), ToolItem("Seaport", 12, R.drawable.ic_seaport), ToolItem("Airport", 15, R.drawable.ic_airport), ToolItem("Query", 5, R.drawable.ic_query))
    )
    internal val allTools by lazy { toolCategories.values.flatten() }




    internal val cityClassNames = arrayOf("Village","Town","City","Capital","Metropolis","Megalopolis")

    internal val problemNames = arrayOf("Crime", "Pollution", "Housing", "Taxes", "Traffic", "Unemployment", "Fire")
    internal val problemIcons = arrayOf("🚨", "☁", "🏠", "💰", "🚗", "👷", "🔥")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        java.io.File(filesDir, "undo").deleteRecursively()   // old whole-city undo snapshots (pre-052)

        // Saves used to live in private storage (filesDir/city.cty, then filesDir/cities/).
        // Copy them once into the public Documents/Andropolis folder; leave the originals.
        if (!prefs.getBoolean("savesInDocuments", false)) {
            try {
                val legacy = java.io.File(filesDir, "city.cty")
                val alreadyCopied = java.io.File(filesDir, "cities/Saved city.cty").exists()   // task 050 did it
                if (legacy.exists() && !alreadyCopied) CitySaves.writePublic(this, legacy, "Saved city.cty")
                java.io.File(filesDir, "cities").listFiles { f: java.io.File -> f.name.endsWith(".cty") }
                    ?.forEach { CitySaves.writePublic(this, it, it.name) }
                prefs.edit().putBoolean("savesInDocuments", true).apply()
            } catch (e: Exception) { android.util.Log.w("Andropolis", "save migration failed", e) }
        }

        // Create sim thread with handler
        val simThread = HandlerThread("sim")
        simThread.start()
        sim = Handler(simThread.looper)
        ui = Handler(Looper.getMainLooper())

        buf = ShortArray(MicropolisNative.mapWidth() * MicropolisNative.mapHeight())
        buildLayout()
        mapView.zoomToFill()

        mapView.toolFootprint = footprintOf(currentTool)
        mapView.straightLineTool = isStraightLineTool(currentTool)
        updatePill()
        updateUndoButtons()

        // Set up annual report state
        annualReportEnabled = prefs.getBoolean("annualReport", true)
        disasterFreq = prefs.getInt("disasterFreq", 2)

        // Set up tap listener
        mapView.onStrokeStart = {
            sim.post {
                MicropolisNative.copyTiles(handle, strokeBefore)
                val s = IntArray(10); MicropolisNative.getStats(handle, s); strokeFundsBefore = s[1]
            }
            strokeMinX = Int.MAX_VALUE; strokeMinY = Int.MAX_VALUE; strokeMaxX = -1; strokeMaxY = -1
        }
        mapView.onTileTap = { tileX, tileY ->
            val tool = currentTool
            // grow the stroke's box by what this tool covers (footprint n > 1 anchors at x-1, y-1)
            val n = footprintOf(tool)
            val x0 = if (n > 1) tileX - 1 else tileX; val y0 = if (n > 1) tileY - 1 else tileY
            strokeMinX = minOf(strokeMinX, x0); strokeMinY = minOf(strokeMinY, y0)
            strokeMaxX = maxOf(strokeMaxX, x0 + n - 1); strokeMaxY = maxOf(strokeMaxY, y0 + n - 1)
            sim.post {
                val r = MicropolisNative.doTool(handle, tool, tileX, tileY)
                if (r == 1) ui.post { if (tool == 7) sfx.bulldoze() else sfx.build() }
            }
        }
        // One BuildEdit per build stroke drives Undo/Redo.
        mapView.onStrokeEnd = { built -> lastToolUseMs = android.os.SystemClock.uptimeMillis(); if (built) commitBuild() }
        // Wire minimap
        minimap.onTileSelected = { tx, ty ->
            mapView.centerOnTile(tx, ty)
            viewBeforeJump = null
        }
        mapView.onViewportChanged = { l, t, r, b -> minimap.setViewport(l, t, r, b) }
        mapView.onUserNavigate = { viewBeforeJump = null }
        idleToMove = prefs.getBoolean("idleToMove", true)
        ui.postDelayed(idleCheck, 1000)
        minimapNav = prefs.getBoolean("minimapNav", true)       // minimap navigation is the default
        autoGoto = prefs.getBoolean("autoGoto", true)
        applyMinimapMode()

        // Setup on sim thread
        sim.post {
            handle = MicropolisNative.create()
            MicropolisNative.init(handle)
            val hasSave = java.io.File(autosavePath).exists()
            if (hasSave) {
                MicropolisNative.loadCity(handle, autosavePath)
                cityReady = true
                ui.post {
                    cityName = prefs.getString("cityName", "My City") ?: "My City"
                    toolbar.title = cityName
                }
            } else {
                MicropolisNative.generateRandomCity(handle)
                cityReady = true
                ui.post {
                    promptCityName(isFirst = true)   // name a brand-new city
                }
            }
        }

        // Start tick loop on sim thread
        sfx = SoundFx(this)
        sfx.enabled = prefs.getBoolean("sound", true)
        sim.post({ tickLoop() })
        Notifier.ensureChannels(this)
        handleEventIntent(intent)
    }

    // The engine's zone-status values are 1-based indices into these tables (engine data
    // files stri.202 / stri.219): density 1-4, land value 5-8, crime 9-12,
    // pollution 13-16, growth 17-20.
    internal val zoneStatusWords = arrayOf(
        "Low", "Medium", "High", "Very High",
        "Slum", "Lower Class", "Middle Class", "High",
        "Safe", "Light", "Moderate", "Dangerous",
        "None", "Moderate", "Heavy", "Very Heavy",
        "Declining", "Stable", "Slow Growth", "Fast Growth")
    internal val tileCategoryNames = arrayOf(
        "Clear", "Water", "Trees", "Rubble", "Flood", "Radioactive Waste", "Fire", "Road",
        "Power", "Rail", "Residential", "Commercial", "Industrial", "Seaport", "Airport",
        "Coal Power", "Fire Department", "Police Department", "Stadium", "Nuclear Power",
        "Draw Bridge", "Radar Dish", "Fountain", "Industrial", "Stadium", "Draw Bridge",
        "Nuclear Waste")
    override fun onPause() {
        super.onPause()
        if (cityReady && handle != 0L) sim.post {
            MicropolisNative.saveCity(handle, autosavePath)
            // leaving the app: also keep a timestamped autosave (at most one a minute)
            if (android.os.SystemClock.uptimeMillis() - lastPublicAutosaveMs > 60_000L) publicAutosave()
        }
    }

    override fun onStop() {
        super.onStop()
        startBackgroundPlay()
    }

    override fun onStart() {
        super.onStart()
        resumeFromBackgroundPlay()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleEventIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        sfx.release()
        ui.removeCallbacksAndMessages(null)
        sim.post {
            if (handle != 0L) {
                MicropolisNative.destroy(handle)
                handle = 0L
            }
        }
        sim.looper.quitSafely()
    }

}


// Small UI/data types shared by the MainActivity extension files.
internal class BuildEdit(val idx: IntArray, val before: ShortArray, val after: ShortArray, val cost: Int)
internal class LogEntry(val date: String, val text: String, val x: Int, val y: Int)
data class ToolItem(val label: String, val value: Int, val icon: Int)
class PanelTab(val title: String, val glyph: String = "", val build: () -> View)
internal class CardHandle(val view: LinearLayout, val setSelected: (Boolean) -> Unit)

/** "No tool": one finger moves the map. */
const val MOVE_TOOL = -1
/** With a tool selected and the map untouched this long, fall back to Move. */
const val IDLE_TO_MOVE_MS = 15_000L
