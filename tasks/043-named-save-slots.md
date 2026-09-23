# Task 043: Named save slots — multiple cities & forks

## Goal
Let the player save and load cities by name, keeping many cities at once and forking one
by saving it under a new name. Replace the single fixed save slot.

## Context (MainActivity.kt)
- Today the ⋮ menu's `"Save city"` calls `saveCity(handle, savePath)` and `"Load city"`
  calls `loadCity(handle, savePath)` with `savePath = filesDir/city.cty` (one slot).
- `MicropolisNative.saveCity/loadCity(handle, path): Int` (1 ok). Engine calls run on `sim`.
- `cityName` (String, shown in `cityTitle`), `prefs` (SharedPreferences, key "cityName"),
  `resetHistory()` and `commitSnapshot()` (undo history), `autosavePath`, `styledDialog(...)`,
  `dp`, `roundedBg`, `showBanner(text)` (from 038) all exist.
- The manual `savePath` slot can stay as-is; this adds a named-file store beside it.

## Interface

### Fields / helpers
```kotlin
private val citiesDir by lazy { java.io.File(filesDir, "cities").apply { mkdirs() } }
private fun sanitize(name: String) = name.trim().replace(Regex("[^A-Za-z0-9 _-]"), "").ifEmpty { "City" }
private fun cityFile(name: String) = java.io.File(citiesDir, "$name.cty")
```

### Save dialog (prompt for a name; save = fork under that name)
```kotlin
private fun showSaveDialog() {
    val input = android.widget.EditText(this).apply { setText(cityName); setSingleLine() }
    androidx.appcompat.app.AlertDialog.Builder(this)
        .setTitle("Save city as")
        .setView(input)
        .setPositiveButton("Save") { _, _ ->
            val name = sanitize(input.text.toString())
            val path = cityFile(name).absolutePath
            cityName = name; prefs.edit().putString("cityName", name).apply(); cityTitle.text = name
            sim.post { MicropolisNative.saveCity(handle, path); MicropolisNative.saveCity(handle, autosavePath) }
            showBanner("Saved “$name”")
        }
        .setNegativeButton("Cancel", null)
        .show()
}
```

### Load dialog (list of saved cities; tap to load, ✕ to delete)
```kotlin
private fun showLoadDialog() {
    val files = citiesDir.listFiles { f -> f.name.endsWith(".cty") }?.sortedBy { it.name } ?: emptyArray()
    val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
    val col = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(16), dp(20), dp(24))
        setBackgroundColor(0xFF12161C.toInt())
    }
    col.addView(TextView(this).apply {
        text = "Load city"; setTextColor(0xFFEEF2F6.toInt()); textSize = 18f
        setTypeface(null, android.graphics.Typeface.BOLD); setPadding(0, 0, 0, dp(8))
    })
    if (files.isEmpty()) {
        col.addView(TextView(this).apply { text = "No saved cities yet."; setTextColor(0xFF9AA7B4.toInt()); setPadding(0, dp(8), 0, dp(8)) })
    }
    for (f in files) {
        val name = f.name.removeSuffix(".cty")
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
            background = roundedBg(0x0DFFFFFF, 12); setPadding(dp(14), dp(12), dp(8), dp(12))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
            addView(TextView(this@MainActivity).apply {
                text = name; setTextColor(0xFFEEF2F6.toInt()); textSize = 15f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(this@MainActivity).apply {
                text = "✕"; setTextColor(0xFF9AA7B4.toInt()); textSize = 16f; setPadding(dp(12), 0, dp(12), 0)
                setOnClickListener { f.delete(); sheet.dismiss(); showLoadDialog() }   // refresh
            })
            setOnClickListener {
                val path = f.absolutePath
                cityName = name; prefs.edit().putString("cityName", name).apply(); cityTitle.text = name
                sim.post {
                    MicropolisNative.loadCity(handle, path)
                    MicropolisNative.saveCity(handle, autosavePath)   // make restore-on-launch match
                    ui.post { resetHistory(); commitSnapshot() }       // fresh undo history for the loaded city
                }
                showBanner("Loaded “$name”")
                sheet.dismiss()
            }
        })
    }
    val sv = ScrollView(this); sv.addView(col); sheet.setContentView(sv); sheet.show()
}
```

### Wire the menu
Change the ⋮ handler branches:
- `"Save city" -> showSaveDialog()`
- `"Load city" -> showLoadDialog()`

## Files in scope
- `android/app/src/main/java/micropolis/port/MainActivity.kt`: edit.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- The planner verifies on device: Save prompts for a name and stores it; Load lists saved
  cities and loads the chosen one (title updates); saving under a new name forks; ✕ deletes.

## Constraints
- Change only `MainActivity.kt`. Do not change the JNI, C ABI, engine, `MapView`, drawables,
  or `build.gradle.kts`. Keep autosave and the rest working.
- No new dependencies. Commit when green; stage only your file by name. Do not use
  `git checkout`, `git reset`, or `git add -A`.
