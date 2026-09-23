# Task 021: Tool icons (vector drawables)

## Goal
Create an Android vector drawable for each build tool, converted from the icon
SVGs in the approved mockup.

## Source
`build/mockups/project/Palette.dc.html` contains one tool card per tool, each with
an inline `<svg>` icon. Convert each card's SVG into a vector drawable. Match the
card by its label text. SVG `<path d="...">` maps directly to `android:pathData`
(the path syntax is the same). Most icons are stroke-only; a few have filled bits.

## Interface
Create these files under `android/app/src/main/res/drawable/`, one per tool
(filename ← tool). Extract the path(s) from the matching card in the mockup:

| file | mockup card label |
|---|---|
| ic_residential.xml | Resid. |
| ic_commercial.xml | Comm. |
| ic_industrial.xml | Indust. |
| ic_park.xml | Park |
| ic_road.xml | Road |
| ic_rail.xml | Rail |
| ic_wire.xml | Wire |
| ic_bulldozer.xml | Dozer |
| ic_police.xml | Police |
| ic_fire.xml | Fire |
| ic_coal.xml | Coal |
| ic_nuclear.xml | Nuclear |
| ic_stadium.xml | Stadium |
| ic_seaport.xml | Seaport |
| ic_airport.xml | Airport |
| ic_query.xml | Query |

Template (stroke icon — one `<path>` per `<path>` in the SVG; `viewportWidth`/
`Height` = the SVG `viewBox` size, 24):
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:pathData="M4 11l8-6 8 6"
        android:strokeColor="#FFFFFFFF" android:strokeWidth="2"
        android:strokeLineCap="round" android:strokeLineJoin="round"
        android:fillColor="#00000000"/>
    <!-- one more <path> per additional path in the card's svg -->
</vector>
```
Rules:
- A card's `<svg>` may hold SEVERAL `<path>` elements — include one drawable
  `<path>` for each, in order.
- If a mockup path has `fill="currentColor"` (a solid dot, e.g. Query), that path
  uses `android:fillColor="#FFFFFFFF"` and no stroke.
- Use `#FFFFFFFF` for stroke/fill — the app tints these at use time.
- `stroke-dasharray` on the road center line has no vector-drawable equivalent;
  for `ic_road`, just draw that path solid (drop the dash).

## Files in scope
- `android/app/src/main/res/drawable/ic_*.xml` (the 16 files above): create.

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`
  (vector drawables compile as part of resource processing).

## Constraints
- Create only the 16 drawable files. Do not change Kotlin, the engine, JNI, CMake,
  or `build.gradle.kts`.
- No new dependencies. Commit when green; stage only your files by name. Do not use
  `git checkout`, `git reset`, or `git add -A`.
