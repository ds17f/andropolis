# Task 061: Finer event groups for background notify / pause

## Goal
The player wants to pause on natural disasters but not on pollution or traffic. Split
the four event groups into eight, each with its own Notify / Pause setting.

## Context (read only these files; all are small)
- `BackgroundPrefs.kt`: `val GROUPS = listOf(Group(...), ...)` — 4 groups today.
- `Notifier.kt`: `fun groupForMessage(msg: Int)` maps engine message numbers to a group id;
  `fun ensureChannels(ctx)` creates one notification channel per group.
- `BackgroundSettings.kt` builds one row per group automatically — no change needed there.

## Interface

### 1. `BackgroundPrefs.kt` — replace the `GROUPS` list with exactly:
```kotlin
    val GROUPS = listOf(
        Group("disasters",  "Natural disasters", "Fire, flood, tornado, earthquake, monster.",                 true, true),
        Group("accidents",  "Accidents",         "Plane, ship, train or helicopter crash; explosion; meltdown; riots.", true, true),
        Group("newYear",    "New year",          "The annual report is ready.",                                 true, false),
        Group("pollution",  "Pollution & crime", "Pollution or crime is very high.",                            true, false),
        Group("traffic",    "Traffic",           "Traffic jams.",                                               true, false),
        Group("power",      "Power",             "Blackouts or brownouts: build more power.",                   true, false),
        Group("money",      "Money & jobs",      "Taxes too high, unemployment, or the city is broke.",         true, false),
        Group("milestones", "Milestones",        "Your city reaches a new size: town, city, capital, …",       true, false),
    )
```

### 2. `Notifier.kt` — replace the `when (msg)` inside `groupForMessage` with exactly:
```kotlin
        val id = when (msg) {
            20, 21, 22, 23, 42 -> "disasters"
            24, 25, 26, 27, 30, 32, 43, 44 -> "accidents"
            10, 11 -> "pollution"
            12, 41 -> "traffic"
            15, 40 -> "power"
            16, 28, 29 -> "money"
            in 35..39 -> "milestones"
            else -> null
        }
```

### 3. `Notifier.kt` — in `ensureChannels`, after `val nm = …`, add:
```kotlin
        nm.deleteNotificationChannel("bg_problems")   // replaced by pollution / traffic / power / money (task 061)
```
and make the high-importance check `if (g.id == "disasters" || g.id == "accidents")`.

## Files in scope
- `android/app/src/main/java/micropolis/port/BackgroundPrefs.kt`
- `android/app/src/main/java/micropolis/port/Notifier.kt`

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`.
- Planner verifies on device: Settings → Background lists 8 event rows.

## Constraints
- **Copy the code blocks above exactly.** Change nothing else. Do not edit this spec.
- Do not read engine sources or git history.
- Commit when green; stage only your files by name. Do not use `git checkout`, `git reset`, or `git add -A`.
