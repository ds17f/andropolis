# Micropolis Port (Android)

A native **Android** port of Micropolis (the open-source SimCity Classic), built on
the modern **[MicropolisCore](https://github.com/SimHacker/MicropolisCore)** C++
engine. The engine is compiled with the Android NDK and driven from Kotlin over a
thin C ABI + JNI — no rewrite of the simulation.

Current state: generates a city and runs the sim, real tile graphics, pinch-zoom /
pan, a tool bar (roads, zones, bulldozer…), and a funds/date/pop/score HUD.

## Install on a phone

1. Open the [**Releases**](../../releases) page on your phone.
2. Download the `.apk` from the latest release.
3. Open it; allow "install from unknown sources" if prompted.

The APK is a debug build (signed with the Android debug key), which is fine for
sideloading.

## Build from source

Requires the Android SDK + NDK (see `DESIGN.md` for exact versions) and a
`android/local.properties` with `sdk.dir=...`.

```
make run           # build, boot the emulator (headless), install, launch
make run-headful   # same, but show the emulator window
make build         # just build the debug APK
```

## How this is built

Opus (Claude) plans and reviews; a local open-weights model (qwen) does the coding,
task by task. The method, the architecture, and the C-ABI boundary are documented
in **`DESIGN.md`**; the working rules are in `CLAUDE.md`.

## License

The Micropolis engine is GPL-3.0 (Electronic Arts / Don Hopkins, via the One Laptop
Per Child project). See the `MicropolisCore` submodule for its license and terms.
