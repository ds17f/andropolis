# Task 070: Keep accented letters in city names

## Goal
A city name with letters such as é, ě, á, ď stays the same after save, load and autosave.

## Context
- Tester report: a Czech city name loses its accented letters. The letters are
  removed, not corrupted.
- Cause: `MainActivity.kt` line 60, `sanitize(name)`, removes every character that is
  not in `[A-Za-z0-9 _-]`. "Save city" (`Panels.kt` line 188) suggests the file name
  `sanitize(cityName).cty`. After the save, `CitySaves.cityNameFor` reads the city
  name back from that file name. So the accented letters are gone.
- The autosave folder name also uses `sanitize` (`SimLoop.kt` line 33).
  `CitySaves.autosavePattern` uses `Regex.escape(city)`, so Unicode names are safe there.
- Android file names accept Unicode letters. The characters that are not safe in a file
  name are `/ \ : * ? " < > |` and control characters. The allow-list below removes them.

## Interface (copy exactly)
Replace the one line at `MainActivity.kt` line 60 with:
```kotlin
    internal fun sanitize(name: String) = name.trim().replace(Regex("[^\\p{L}\\p{M}\\p{N} _-]"), "").ifEmpty { "City" }
```
(`\p{L}` = any letter, `\p{M}` = combining accent marks, `\p{N}` = any digit.)

## Files in scope
- `android/app/src/main/java/micropolis/port/MainActivity.kt`: edit line 60 only.

## Definition of done
- Build: `make build`. It must succeed.
- Check: `grep -n 'p{L}' android/app/src/main/java/micropolis/port/MainActivity.kt`
  prints the new line.

## Constraints
- Change only the one line. Do not change any other file.
- Do not add a new dependency.
