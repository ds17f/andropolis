# Task 014: Tax rate control

## Goal
Add a tax-rate control. Bind the engine's `setCityTax` to Kotlin, and add a button
that cycles the tax rate and applies it.

## Context
The C ABI already has `void micropolis_set_city_tax(MicropolisEngine *e, int tax)`,
but it is not bound to Kotlin yet (like save/load was). Add the JNI + Kotlin
binding, then a UI button. All engine calls run on the `sim` thread.

## Interface (apply exactly)

### 1. `android/app/src/main/cpp/micropolis_jni.cpp`
Add inside the existing `extern "C"` block:
```cpp
JNIEXPORT void JNICALL
Java_micropolis_port_MicropolisNative_setCityTax(JNIEnv *, jobject, jlong h, jint tax) {
    micropolis_set_city_tax(eng(h), tax);
}
```

### 2. `android/app/src/main/java/micropolis/port/MicropolisNative.kt`
Add:
```kotlin
/** Set the city tax rate (percent, 0..20). */
external fun setCityTax(handle: Long, tax: Int)
```

### 3. `android/app/src/main/java/micropolis/port/MainActivity.kt`
Add fields and a cycling button (add the button to `barLayout`, after the speed
button):
```kotlin
private val taxRates = intArrayOf(0, 5, 7, 9, 12, 15, 20)
private var taxIdx = 2   // start at 7%

val taxBtn = Button(this).apply {
    text = "Tax: ${taxRates[taxIdx]}%"
    setOnClickListener {
        taxIdx = (taxIdx + 1) % taxRates.size
        text = "Tax: ${taxRates[taxIdx]}%"
        val t = taxRates[taxIdx]
        sim.post { MicropolisNative.setCityTax(handle, t) }
    }
}
barLayout.addView(taxBtn)
```

## Files in scope
- `android/app/src/main/cpp/micropolis_jni.cpp`
- `android/app/src/main/java/micropolis/port/MicropolisNative.kt`
- `android/app/src/main/java/micropolis/port/MainActivity.kt`

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`
  (recompiles the native lib for the new JNI symbol).

## Constraints
- Change only the three files above. Do not change the C ABI header, the CMake
  files, the engine, or `MapView`.
- No new dependencies. Commit when green; stage only your files by name. Do not use
  `git checkout`, `git reset`, or `git add -A`.
