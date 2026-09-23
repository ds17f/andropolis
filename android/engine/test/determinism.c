/*
 * determinism.c — background play depends on replay being exact (DESIGN.md 12.5).
 *
 * Grow a city, snapshot it (.cty + PRNG state), then replay the snapshot twice in
 * fresh engines with the same recipe. Both replays must produce the same events at
 * the same ticks and the same map and funds. A third replay with a different PRNG
 * state must diverge (proves the state we capture is the one that matters).
 * Prints "OK: determinism passed" and exits 0 on success.
 *
 * Diagnostics (env vars): ONE=1 build a snapshot, replay once, print its rng;
 * REPLAY=<rng> replay the existing snapshot only (run it in several processes);
 * TRACE=<n> print rng + map hash for the first n ticks; DIFF=1 list differing
 * tiles and the first differing event. Try MALLOC_PERTURB_=<byte> too.
 */
#include "micropolis_c.h"
#include <stdio.h>
#include <string.h>
#include <stdlib.h>

#define TICKS 6000   /* ~ several game years */
#define MAXEV 4096

typedef struct { int tick; MicropolisEvent ev; } Rec;
typedef struct { Rec recs[MAXEV]; int n; unsigned long long mapHash; int funds, time; unsigned short map[120*100]; } Run;

static unsigned long long hash_map(MicropolisEngine *e) {
    unsigned long long h = 1469598103934665603ULL;
    for (int x = 0; x < micropolis_map_width(); x++)
        for (int y = 0; y < micropolis_map_height(); y++) { h ^= micropolis_get_tile(e, x, y); h *= 1099511628211ULL; }
    return h;
}

static void replay(const char *path, long long rng, Run *r) {
    MicropolisEngine *e = micropolis_create();
    micropolis_init(e);
    micropolis_load_city_seeded(e, path, rng);
    micropolis_set_speed(e, 3);
    MicropolisEvent ev;
    while (micropolis_poll_event(e, &ev)) {}          /* drop load-time events */
    r->n = 0;
    for (int t = 0; t < TICKS; t++) {
        if (getenv("TRACE") && t < atoi(getenv("TRACE"))) printf("t%d rng=%lld map=%016llx\n", t, micropolis_get_rng(e), hash_map(e));
        micropolis_sim_tick(e);
        while (micropolis_poll_event(e, &ev))
            if (r->n < MAXEV) { r->recs[r->n].tick = t; r->recs[r->n].ev = ev; r->n++; }
    }
    MicropolisStats s; micropolis_get_stats(e, &s);
    for (int x = 0; x < 120; x++) for (int y = 0; y < 100; y++) r->map[x*100+y] = micropolis_get_tile(e, x, y);
    r->mapHash = hash_map(e); r->funds = s.total_funds; r->time = s.city_time;
    micropolis_destroy(e);
}

static int same(const Run *a, const Run *b) {
    if (a->n != b->n || a->mapHash != b->mapHash || a->funds != b->funds || a->time != b->time) return 0;
    return memcmp(a->recs, b->recs, sizeof(Rec) * a->n) == 0;
}

static Run A, B, C;

int main(void) {
    const char *path = "/tmp/micropolis_determinism.cty";
    if (getenv("REPLAY")) {                    /* replay an existing snapshot only */
        long long r = atoll(getenv("REPLAY"));
        replay(path, r, &A);
        printf("replay-only: %d events funds=%d map=%016llx\n", A.n, A.funds, A.mapHash);
        return 0;
    }
    MicropolisEngine *e = micropolis_create();
    micropolis_init(e);
    micropolis_generate_city_seed(e, 1234);
    micropolis_set_speed(e, 3);
    /* a little city so zones grow, traffic moves and messages fire */
    for (int x = 30; x < 70; x++) micropolis_do_tool(e, 9 /* road */, x, 50);
    micropolis_do_tool(e, MICROPOLIS_TOOL_COALPOWER, 32, 46);
    for (int i = 0; i < 8; i++) {
        micropolis_do_tool(e, 0 /* res */, 36 + 4 * i, 53);
        micropolis_do_tool(e, 2 /* ind */, 36 + 4 * i, 47);
    }
    for (int x = 30; x < 70; x++) micropolis_do_tool(e, 6 /* wire */, x, 51);
    for (int t = 0; t < 3000; t++) micropolis_sim_tick(e);
    micropolis_make_disaster(e, 0);                  /* a fire in flight at snapshot time */
    for (int t = 0; t < 50; t++) micropolis_sim_tick(e);
    if (!micropolis_save_city(e, path)) { printf("FAIL: save\n"); return 1; }
    long long rng = micropolis_get_rng(e);
    micropolis_destroy(e);

    if (getenv("ONE")) {                       /* one replay per process: prints its hash */
        printf("rng=%lld\n", rng);
        replay(path, rng, &A);
        printf("one: %d events funds=%d map=%016llx\n", A.n, A.funds, A.mapHash);
        return 0;
    }
    replay(path, rng, &A);
    replay(path, rng, &B);
    replay(path, rng ^ 0x5DEECE66DLL, &C);

    printf("replay A: %d events, funds=%d time=%d map=%016llx\n", A.n, A.funds, A.time, A.mapHash);
    printf("replay B: %d events, funds=%d time=%d map=%016llx\n", B.n, B.funds, B.time, B.mapHash);
    printf("replay C: %d events, funds=%d time=%d map=%016llx (other rng)\n", C.n, C.funds, C.time, C.mapHash);
    if (A.n == 0) { printf("FAIL: no events to compare\n"); return 1; }
    if (getenv("DIFF")) {
        int d = 0;
        for (int i = 0; i < 12000 && d < 30; i++) if (A.map[i] != B.map[i]) { printf("diff (%d,%d): %04x vs %04x\n", i/100, i%100, A.map[i], B.map[i]); d++; }
        for (int i = 0; i < A.n && i < B.n; i++) if (memcmp(&A.recs[i], &B.recs[i], sizeof(Rec))) { printf("first event diff #%d tick %d/%d type %d/%d\n", i, A.recs[i].tick, B.recs[i].tick, A.recs[i].ev.type, B.recs[i].ev.type); break; }
    }
    if (!same(&A, &B)) { printf("FAIL: same snapshot + rng replayed differently\n"); return 1; }
    if (same(&A, &C)) { printf("FAIL: rng state had no effect (wrong state captured?)\n"); return 1; }
    printf("OK: determinism passed\n");
    return 0;
}
