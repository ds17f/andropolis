# Andropolis

Andropolis is an open-source Android port of **Micropolis**, the GPL release of the
original SimCity city simulator by Will Wright. The simulation is the
**[MicropolisCore](https://github.com/SimHacker/MicropolisCore)** C++ engine. The Android
NDK compiles the engine, and Kotlin drives it through a small C ABI and JNI. The port
does not rewrite the simulation.

## Features

- The full classic simulation: zones, traffic, pollution, crime, land value, budget,
  and the city evaluation.
- The 8 original scenarios and 23 sample cities.
- New maps with terrain choices: trees, lakes, rivers, and islands.
- Touch controls: draw roads and power lines, place buildings with a preview, and undo
  each build.
- Overlays, graphs, messages, the opinion poll, and the annual report.
- Background play (optional): the city continues while the app is closed. The app
  sends a notification when an event occurs.
- Saves are plain `.cty` files in `Documents/Andropolis`.
- No ads, no tracking, and no network access.

## Install

Download the APK from the [Releases](../../releases) page, then open it on the phone.
Android asks for permission to install apps from this source.

Releases on Google Play and F-Droid will follow. `RELEASING.md` gives the procedure.

## Build from the source

You need the Android SDK and NDK (the versions are in `android/app/build.gradle.kts`).
You also need `android/local.properties` with `sdk.dir=...`.

```
git clone --recursive https://github.com/ds17f/andropolis.git
make run           # build, start the emulator (headless), install, and start the app
make run-headful   # the same, with the emulator window
make build         # build the debug APK only
```

The engine tests run on the host computer:

```
cmake -S android -B android/build-host && cmake --build android/build-host -j
./android/build-host/engine/micropolis_abi_smoke
./android/build-host/engine/micropolis_determinism
./android/build-host/engine/micropolis_sprites
```

## How the project is made

Claude (Opus) plans and reviews. A local open-weights model (qwen) writes most of the
code, one task at a time. `DESIGN.md` describes the method, the architecture, and the C
ABI. `CLAUDE.md` has the working rules.

## License and trademarks

Andropolis is free software under the GNU General Public License version 3 (`LICENSE`),
with the additional terms of the Micropolis GPL release (`LICENSE-MICROPOLIS.md`). The
engine comes from MicropolisCore (Electronic Arts, Don Hopkins, and the One Laptop per
Child project).

Micropolis is a registered trademark of Micropolis Corporation (Micropolis GmbH), named
here only to describe where this port comes from, under the Micropolis Public Name
License. SimCity is a trademark of Electronic Arts. Andropolis is not affiliated with or
endorsed by Micropolis GmbH or Electronic Arts.
