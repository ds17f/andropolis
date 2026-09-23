# Task 060: "How to play" — tips page + the original Micropolis manual, in the app

## Goal
Add **⋮ → How to play**. It opens a full-screen help view that starts on a short
"Tips for this app" page. That page links to the original **Micropolis manual**
(GPL, bundled in MicropolisCore), shown offline from the app's assets.

## Context (read only these parts)
- The manual: `MicropolisCore/documentation/manual/*.html` (8 plain HTML files, relative
  links, no images). Copy them unchanged.
- `Panels.kt` line 166 `showOverflowMenu(anchor)`: line 174 `pm.menu.add("Settings")`,
  line 180 `"Settings" -> { handedOff = true; showSettingsPanel(resume) }`. `resume` is a
  `() -> Unit` that restarts the sim; call it when the help view closes.
- Do NOT read all of `MainActivity.kt`; you do not need it.

## Interface

### 1. Assets (legwork)
Copy every `*.html` from `MicropolisCore/documentation/manual/` into
`android/app/src/main/assets/manual/` (same file names, unchanged). Do not copy README.md.

### 2. New file `android/app/src/main/assets/manual/tips.html` (copy exactly)
```html
<!doctype html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Tips</title>
<style>
 body { font-family: sans-serif; background:#12161C; color:#EEF2F6; margin:16px; line-height:1.45; }
 h1 { font-size:1.4em; } h2 { font-size:1.1em; color:#F5A623; margin-top:1.4em; }
 a { color:#F5A623; } li { margin:0.3em 0; } .muted { color:#9AA7B4; }
</style></head><body>
<h1>How to play</h1>
<p>You are the mayor. Zone land, connect it, power it, and keep the people happy.
The city grows by itself when it has what it needs.</p>

<h2>First city in five steps</h2>
<ol>
 <li><b>Power:</b> build a Coal power plant (Services &amp; Power).</li>
 <li><b>Zones:</b> put Residential, Commercial and Industrial zones near it. Start with
   more Residential than the others.</li>
 <li><b>Wire:</b> connect every zone to the plant. Zones that touch each other share power.</li>
 <li><b>Roads:</b> connect the zones with roads. People must be able to travel between
   homes, shops and jobs.</li>
 <li><b>Run:</b> press ▶ and watch. Add zones where the demand is high.</li>
</ol>

<h2>Controls</h2>
<ul>
 <li><b>Move</b> (no tool): one finger moves the map; pinch to zoom.</li>
 <li>Tap <b>Current tool</b> to pick a tool. Tap <b>✕</b> to go back to Move.
   After 15 s without touching the map, the tool goes back to Move by itself
   (Settings → Drop tool when idle).</li>
 <li><b>Roads, rail, wire:</b> drag to draw a straight line.</li>
 <li><b>Buildings:</b> drag the outline to the place you want, then lift your finger.</li>
 <li><b>Undo</b> (↶) removes your last build and gives the money back. Redo is in ⋮.</li>
 <li><b>Query</b>: tap a tile to see its density, land value, crime and pollution.</li>
</ul>

<h2>Reading your city</h2>
<ul>
 <li><b>City → Overview / Opinion / Conditions</b>: score, what citizens want, and the
   worst problems.</li>
 <li><b>City → Budget</b>: tax rate and spending on roads, fire and police.</li>
 <li><b>Overlay</b>: color the map by power, crime, pollution, traffic and more.</li>
 <li>Messages and the annual report tell you what to fix next.</li>
</ul>

<h2>Good habits</h2>
<ul>
 <li>Keep industry away from homes: it makes pollution.</li>
 <li>Police lower crime; fire stations stop fires from spreading. Parks raise land value.</li>
 <li>Too many roads cost a lot to maintain. Rail carries traffic for less pollution.</li>
 <li>Taxes above about 9% slow growth. Keep some money for emergencies.</li>
</ul>

<h2>While the app is closed</h2>
<p>Settings → Background: the city can keep going while the app is closed, at the pace
you choose. You get a notification when something happens; tap it to go there.</p>

<h2>The original manual</h2>
<p>The full Micropolis manual (GPL). Its tutorial describes the old desktop version, but the
rules are the same.</p>
<ul>
 <li><a href="intro.html">Introduction and rules of play</a></li>
 <li><a href="reference.html">Reference: tools, budget, graphs, disasters</a></li>
 <li><a href="inside.html">Inside the simulator: how the city thinks</a></li>
 <li><a href="tutorial.html">Tutorial (desktop version)</a></li>
 <li><a href="history.html">History of cities and city planning</a></li>
 <li><a href="index.html">Manual contents</a></li>
</ul>
<p class="muted">Micropolis is the GPL release of the original SimCity.</p>
</body></html>
```

### 3. New file `HelpScreen.kt`
```kotlin
package micropolis.port

/** Full-screen help: tips.html first, then the bundled manual. onClose runs when it closes. */
internal fun MainActivity.showHelp(onClose: () -> Unit)
```
Behaviour:
- Use an `android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)`.
- Content: a vertical `LinearLayout` with background `0xFF12161C`:
  - a top row (horizontal, padding 12dp, center-vertical): a `TextView` "How to play"
    (color `0xFFEEF2F6`, 18sp, bold, weight 1) and a `TextView` "✕" (18sp, color
    `0xFF9AA7B4`, padding 12dp) that dismisses the dialog;
  - an `android.webkit.WebView` filling the rest (height 0, weight 1), with
    `setBackgroundColor(0xFF12161C.toInt())`, loading
    `"file:///android_asset/manual/tips.html"`. Leave JavaScript off.
- Back button: if the WebView `canGoBack()`, go back; otherwise dismiss
  (`setOnKeyListener` on the dialog for `KeyEvent.KEYCODE_BACK` on `ACTION_UP`).
- Links that start with `http`, `https` or `mailto` open outside the app with
  `Intent(Intent.ACTION_VIEW, uri)` (use a `WebViewClient` with
  `shouldOverrideUrlLoading(view, request)`; return `true` for those, `false` otherwise).
- `setOnDismissListener { onClose() }`.

### 4. `Panels.kt` — two small additions only
- After line 174 (`pm.menu.add("Settings")`), add `pm.menu.add("How to play")`.
- In the `when (item.title)` block, next to the `"Settings"` line (180), add
  `"How to play" -> { handedOff = true; showHelp(resume) }`.

## Files in scope
- `android/app/src/main/assets/manual/` (8 copied HTML files + new `tips.html`): new.
- `android/app/src/main/java/micropolis/port/HelpScreen.kt`: new.
- `android/app/src/main/java/micropolis/port/Panels.kt`: the two additions above.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- Planner verifies on device: ⋮ → How to play shows the tips page; its manual links open
  the chapters; Back goes back, then closes; the sim resumes after closing.

## Constraints
- **Copy the code blocks above exactly.** Everything you need is in this spec. Do not read
  engine sources or git history; read only the line ranges named above.
- Only ADD code. Do not delete, re-indent or restructure existing code. Do not edit this spec.
- No new dependencies. Do not change C/C++ code, CMake, `build.gradle.kts`, or files in `MicropolisCore/`.
- Commit when green; stage only your files by name. Do not use `git checkout`, `git reset`, or `git add -A`.
