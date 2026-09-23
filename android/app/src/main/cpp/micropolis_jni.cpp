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

JNIEXPORT void JNICALL
Java_micropolis_port_MicropolisNative_getBudget(JNIEnv *env, jobject, jlong h, jintArray dst) {
    if (env->GetArrayLength(dst) < 12) return;
    MicropolisBudget b; micropolis_get_budget(eng(h), &b);
    jint t[12] = { b.total_funds, b.tax_rate, b.tax_income,
                   b.road_fund, b.road_spend, b.road_percent,
                   b.police_fund, b.police_spend, b.police_percent,
                   b.fire_fund, b.fire_spend, b.fire_percent };
    env->SetIntArrayRegion(dst, 0, 12, t);
}

JNIEXPORT void JNICALL
Java_micropolis_port_MicropolisNative_getEvaluation(JNIEnv *env, jobject, jlong h, jintArray dst) {
    if (env->GetArrayLength(dst) < 7) return;
    MicropolisEvaluation ev; micropolis_get_evaluation(eng(h), &ev);
    jint t[7] = { ev.city_score, ev.score_delta, ev.city_class,
                  ev.city_pop, ev.pop_delta, ev.assessed_value, ev.approval };
    env->SetIntArrayRegion(dst, 0, 7, t);
}

JNIEXPORT void JNICALL
Java_micropolis_port_MicropolisNative_setCityTax(JNIEnv *, jobject, jlong h, jint tax) {
    micropolis_set_city_tax(eng(h), tax);
}

JNIEXPORT void JNICALL
Java_micropolis_port_MicropolisNative_setSpeed(JNIEnv *, jobject, jlong h, jint speed) {
    micropolis_set_speed(eng(h), speed);
}

JNIEXPORT jint JNICALL
Java_micropolis_port_MicropolisNative_copyOverlay(JNIEnv *env, jobject, jlong h, jint overlay, jbyteArray dst) {
    jsize len = env->GetArrayLength(dst);
    jbyte *buf = env->GetByteArrayElements(dst, nullptr);
    int n = micropolis_copy_overlay(eng(h), overlay, reinterpret_cast<unsigned char *>(buf), (int) len);
    env->ReleaseByteArrayElements(dst, buf, 0);
    return n;
}

JNIEXPORT jint JNICALL
Java_micropolis_port_MicropolisNative_getHistory(JNIEnv *env, jobject, jlong h, jint history, jint scale, jintArray dst) {
    jsize len = env->GetArrayLength(dst);
    jint *buf = env->GetIntArrayElements(dst, nullptr);
    int n = micropolis_get_history(eng(h), history, scale, buf, (int) len);
    env->ReleaseIntArrayElements(dst, buf, 0);
    return n;
}

JNIEXPORT void JNICALL
Java_micropolis_port_MicropolisNative_makeDisaster(JNIEnv *, jobject, jlong h, jint disaster) {
    micropolis_make_disaster(eng(h), disaster);
}

JNIEXPORT void JNICALL
Java_micropolis_port_MicropolisNative_setFunding(JNIEnv *, jobject, jlong h, jint roadPct, jint firePct, jint policePct) {
    micropolis_set_funding(eng(h), roadPct, firePct, policePct);
}

JNIEXPORT void JNICALL
Java_micropolis_port_MicropolisNative_setAutoBudget(JNIEnv *, jobject, jlong h, jint on) {
    micropolis_set_auto_budget(eng(h), on);
}

JNIEXPORT jboolean JNICALL
Java_micropolis_port_MicropolisNative_pollEvent(JNIEnv *env, jobject, jlong h, jintArray out) {
    if (env->GetArrayLength(out) < 9) return JNI_FALSE;
    MicropolisEvent ev;
    if (!micropolis_poll_event(eng(h), &ev)) return JNI_FALSE;
    jint t[9] = { ev.type, ev.x, ev.y, ev.a, ev.b, ev.c, ev.d, ev.e, ev.f };
    env->SetIntArrayRegion(out, 0, 9, t);
    return JNI_TRUE;
}

} /* extern "C" */
