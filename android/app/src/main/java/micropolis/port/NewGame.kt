package micropolis.port

/**
 * Starting a game: a new generated map, an original scenario, or a sample city.
 * Engine work runs on the sim thread; the city files are assets (the .cty files in assets/cities)
 * staged into cacheDir because the engine reads real paths. `onDone` runs on the UI
 * thread when the new city is in place (use it to resume the sim).
 */

/** The eight original scenarios (engine ids 1..8) and their asset files. */
class ScenarioInfo(val id: Int, val name: String, val year: Int, val asset: String, val challenge: String)

internal val SCENARIOS = listOf(
    ScenarioInfo(1, "Dullsville",     1900, "scenario_dullsville.cty",     "Boredom. Turn a sleepy town into a big city in 30 years."),
    ScenarioInfo(2, "San Francisco",  1906, "scenario_san_francisco.cty",  "Earthquake. Rebuild after the great quake in 5 years."),
    ScenarioInfo(3, "Hamburg",        1944, "scenario_hamburg.cty",        "Fire bombs. Rebuild the city after the war in 5 years."),
    ScenarioInfo(4, "Bern",           1965, "scenario_bern.cty",           "Traffic. End the traffic jams in 10 years."),
    ScenarioInfo(5, "Tokyo",          1957, "scenario_tokyo.cty",          "Monster attack. Rebuild after the monster in 5 years."),
    ScenarioInfo(6, "Detroit",        1972, "scenario_detroit.cty",        "Crime. Bring crime down and the city back in 10 years."),
    ScenarioInfo(7, "Boston",         2010, "scenario_boston.cty",         "Nuclear meltdown. Rebuild after the meltdown in 5 years."),
    ScenarioInfo(8, "Rio de Janeiro", 2047, "scenario_rio_de_janeiro.cty", "Floods. Keep the coastal city alive in 10 years."),
)

/** Sample cities shipped with Micropolis: asset file names (without .cty), sorted. */
internal fun MainActivity.sampleCities(): List<String> =
    (assets.list("cities") ?: emptyArray()).filter { it.endsWith(".cty") && !it.startsWith("scenario_") }
        .map { it.removeSuffix(".cty") }.sorted()

private fun MainActivity.stageAsset(asset: String): String {
    val out = java.io.File(cacheDir, "start.cty")
    assets.open("cities/$asset").use { inp -> out.outputStream().use { inp.copyTo(it) } }
    return out.path
}

private fun MainActivity.adoptCity(name: String, onDone: () -> Unit) {
    cityName = name; prefs.edit().putString("cityName", name).apply(); toolbar.title = name
    resetHistory()                    // undo does not cross cities
    onDone()
}

/**
 * A new generated map. Terrain values as MicropolisNative.setTerrain (-1 = random).
 * Asks for the city name afterwards.
 */
internal fun MainActivity.startNewMap(trees: Int, lakes: Int, river: Int, island: Int, onDone: () -> Unit) {
    cityReady = false
    sim.post {
        MicropolisNative.setTerrain(handle, trees, lakes, river, island)
        MicropolisNative.generateRandomCity(handle)
        MicropolisNative.setFunds(handle, 20_000)          // generating keeps the old city's money; Easy start
        MicropolisNative.saveCity(handle, autosavePath)   // reset autosave to the new city
        cityReady = true
        ui.post {
            resetHistory()
            promptCityName(isFirst = true, onDismiss = onDone)
        }
    }
}

/** Start an original scenario (its win/lose check runs in the engine). */
internal fun MainActivity.startScenario(sc: ScenarioInfo, onDone: () -> Unit) {
    val path = stageAsset(sc.asset)
    cityReady = false
    sim.post {
        MicropolisNative.loadScenario(handle, sc.id, path)
        MicropolisNative.saveCity(handle, autosavePath)
        cityReady = true
        ui.post { adoptCity(sc.name, onDone) }
    }
}

/** Load one of the sample cities by its asset name (from sampleCities()). */
internal fun MainActivity.startSampleCity(name: String, onDone: () -> Unit) {
    val path = stageAsset("$name.cty")
    cityReady = false
    sim.post {
        MicropolisNative.loadCity(handle, path)
        MicropolisNative.saveCity(handle, autosavePath)
        cityReady = true
        ui.post { adoptCity(name.replaceFirstChar { it.uppercase() }, onDone) }
    }
}
