/*
 * micropolis_c.cpp — STUB. Task 002 replaces every body below with a real bridge
 * to the C++ Micropolis engine (see micropolis_c.h for the contract).
 *
 * This stub exists only so the tree builds and the C-ABI test links and RUNS.
 * With these default returns the test (test/abi_smoke.c) FAILS on purpose; the
 * task is to make it pass. Do NOT ship this stub.
 *
 * Implementation notes for the executor:
 *   - Wrap a Micropolis* (and a NullCallback) behind the opaque MicropolisEngine.
 *     Include "micropolis.h" and "micropolis_null_callback.h".
 *   - Mirror task 001's sequence: create -> setCallback(nullCb, emscripten::val())
 *     -> the caller then calls init(), generate..., simTick(), etc.
 *   - Add static_assert()s that MICROPOLIS_TOOL_* == the engine's EditingTool and
 *     MICROPOLIS_TOOL_{NO_MONEY,NEED_BULLDOZE,FAILED,OK} == ToolResult, so the C
 *     enums can never drift from the C++ ones.
 *   - copy_tiles: column-major, dst[x*H + y]; return W*H, or 0 on bad args.
 *   - get_stats: fill every field (cityTime, totalFunds, cityPop, cityScore,
 *     cityYear, cityMonth, resValve, comValve, indValve, gameLevel).
 */
#include "micropolis_c.h"
#include <cstddef>

extern "C" {

MicropolisEngine *micropolis_create(void) { return NULL; }
void micropolis_destroy(MicropolisEngine *e) { (void)e; }

void micropolis_init(MicropolisEngine *e) { (void)e; }
void micropolis_generate_random_city(MicropolisEngine *e) { (void)e; }
void micropolis_generate_city_seed(MicropolisEngine *e, int seed) { (void)e; (void)seed; }
int  micropolis_load_city(MicropolisEngine *e, const char *path) { (void)e; (void)path; return 0; }

void micropolis_sim_tick(MicropolisEngine *e) { (void)e; }
void micropolis_sim_update(MicropolisEngine *e) { (void)e; }
void micropolis_set_speed(MicropolisEngine *e, int speed) { (void)e; (void)speed; }
void micropolis_set_passes(MicropolisEngine *e, int passes) { (void)e; (void)passes; }
void micropolis_set_city_tax(MicropolisEngine *e, int tax) { (void)e; (void)tax; }
void micropolis_set_game_level(MicropolisEngine *e, int level) { (void)e; (void)level; }

int micropolis_map_width(void) { return MICROPOLIS_MAP_W; }
int micropolis_map_height(void) { return MICROPOLIS_MAP_H; }
unsigned short micropolis_get_tile(const MicropolisEngine *e, int x, int y) { (void)e; (void)x; (void)y; return 0; }
int micropolis_copy_tiles(const MicropolisEngine *e, unsigned short *dst, int dst_len) { (void)e; (void)dst; (void)dst_len; return 0; }

void micropolis_get_stats(const MicropolisEngine *e, MicropolisStats *out) {
    (void)e;
    if (out) { MicropolisStats z = {0,0,0,0,0,0,0,0,0,0}; *out = z; }
}

int micropolis_do_tool(MicropolisEngine *e, int tool, int x, int y) { (void)e; (void)tool; (void)x; (void)y; return 0; }

} /* extern "C" */
