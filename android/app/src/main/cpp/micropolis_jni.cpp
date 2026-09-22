/*
 * micropolis_jni.cpp — JNI bridge from Kotlin (micropolis.port.MicropolisNative)
 * to the C ABI (micropolis_c.h). One thin function per native method.
 *
 * The handle is the MicropolisEngine* boxed in a jlong. The engine is not
 * thread-safe, so all calls must come from one thread (see DESIGN.md).
 */
#include <jni.h>
#include "micropolis_c.h"

static inline MicropolisEngine *eng(jlong h) {
    return reinterpret_cast<MicropolisEngine *>(h);
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_micropolis_port_MicropolisNative_create(JNIEnv *, jobject) {
    return reinterpret_cast<jlong>(micropolis_create());
}

JNIEXPORT void JNICALL
Java_micropolis_port_MicropolisNative_destroy(JNIEnv *, jobject, jlong h) {
    micropolis_destroy(eng(h));
}

JNIEXPORT void JNICALL
Java_micropolis_port_MicropolisNative_init(JNIEnv *, jobject, jlong h) {
    micropolis_init(eng(h));
}

JNIEXPORT void JNICALL
Java_micropolis_port_MicropolisNative_generateRandomCity(JNIEnv *, jobject, jlong h) {
    micropolis_generate_random_city(eng(h));
}

JNIEXPORT void JNICALL
Java_micropolis_port_MicropolisNative_simTick(JNIEnv *, jobject, jlong h) {
    micropolis_sim_tick(eng(h));
}

JNIEXPORT jint JNICALL
Java_micropolis_port_MicropolisNative_doTool(JNIEnv *, jobject, jlong h, jint tool, jint x, jint y) {
    return micropolis_do_tool(eng(h), tool, x, y);
}

JNIEXPORT jint JNICALL
Java_micropolis_port_MicropolisNative_mapWidth(JNIEnv *, jobject) {
    return micropolis_map_width();
}

JNIEXPORT jint JNICALL
Java_micropolis_port_MicropolisNative_mapHeight(JNIEnv *, jobject) {
    return micropolis_map_height();
}

JNIEXPORT jint JNICALL
Java_micropolis_port_MicropolisNative_copyTiles(JNIEnv *env, jobject, jlong h, jshortArray dst) {
    jsize len = env->GetArrayLength(dst);
    // jshort is a signed 16-bit int; the bit pattern matches unsigned short.
    jshort *buf = env->GetShortArrayElements(dst, nullptr);
    int n = micropolis_copy_tiles(eng(h), reinterpret_cast<unsigned short *>(buf), (int) len);
    env->ReleaseShortArrayElements(dst, buf, 0);
    return n;
}

JNIEXPORT void JNICALL
Java_micropolis_port_MicropolisNative_getStats(JNIEnv *env, jobject, jlong h, jintArray dst) {
    if (env->GetArrayLength(dst) < 10) {
        return;
    }
    MicropolisStats s;
    micropolis_get_stats(eng(h), &s);
    jint tmp[10] = {
        s.city_time, s.total_funds, s.city_pop, s.city_score, s.city_year,
        s.city_month, s.res_demand, s.com_demand, s.ind_demand, s.game_level
    };
    env->SetIntArrayRegion(dst, 0, 10, tmp);
}

} /* extern "C" */
