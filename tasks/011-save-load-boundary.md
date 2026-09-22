# Task 011: Save/load boundary (C ABI → JNI → Kotlin)

## Goal
Expose **save** and **load** of a city all the way to Kotlin. The C ABI already
has `micropolis_load_city`; add a save function, and wire both across JNI so
Kotlin can call them.

## Context
The engine saves with `bool Micropolis::saveFile(const std::string &filename)`
(the counterpart to the load path). The C ABI has `micropolis_load_city` but no
save, and neither load nor save is bound to Kotlin yet. Add the pieces below.
This is the exact design — apply it; do not redesign.

## Interface (apply exactly)

### 1. `android/engine/include/micropolis_c.h`
Right after the `micropolis_load_city` declaration, add:
```c
/* Save the current city to a file. Returns 1 on success, 0 on failure. */
int  micropolis_save_city(MicropolisEngine *e, const char *path);
```

### 2. `android/engine/src/micropolis_c.cpp`
Right after the `micropolis_load_city` implementation, add:
```cpp
int micropolis_save_city(MicropolisEngine *e, const char *path) {
    if (e && path) {
        return e->sim->saveFile(std::string(path)) ? 1 : 0;
    }
    return 0;
}
```

### 3. `android/app/src/main/cpp/micropolis_jni.cpp`
Add both JNI functions (inside the existing `extern "C"` block):
```cpp
JNIEXPORT jint JNICALL
Java_micropolis_port_MicropolisNative_saveCity(JNIEnv *env, jobject, jlong h, jstring jpath) {
    const char *path = env->GetStringUTFChars(jpath, nullptr);
    int r = micropolis_save_city(eng(h), path);
    env->ReleaseStringUTFChars(jpath, path);
    return r;
}

JNIEXPORT jint JNICALL
Java_micropolis_port_MicropolisNative_loadCity(JNIEnv *env, jobject, jlong h, jstring jpath) {
    const char *path = env->GetStringUTFChars(jpath, nullptr);
    int r = micropolis_load_city(eng(h), path);
    env->ReleaseStringUTFChars(jpath, path);
    return r;
}
```

### 4. `android/app/src/main/java/micropolis/port/MicropolisNative.kt`
Add two external functions:
```kotlin
/** Save the city to `path`. Returns 1 on success, 0 on failure. */
external fun saveCity(handle: Long, path: String): Int
/** Load a city from `path`. Returns 1 on success, 0 on failure. */
external fun loadCity(handle: Long, path: String): Int
```

## Files in scope
- `android/engine/include/micropolis_c.h`
- `android/engine/src/micropolis_c.cpp`
- `android/app/src/main/cpp/micropolis_jni.cpp`
- `android/app/src/main/java/micropolis/port/MicropolisNative.kt`

## Definition of done
- Build: `./android/gradlew -p android :app:assembleDebug` → `BUILD SUCCESSFUL`
  (this recompiles the native lib and links the new JNI symbols).

## Constraints
- Change only the four files above. Do not change the engine, the CMake files,
  the tests, or other Kotlin.
- No new dependencies. Commit when green; stage only your files by name. Do not use
  `git checkout`, `git reset`, or `git add -A`.
