/*
 * abi_smoke.c — the Definition of Done for task 002.
 *
 * This is a plain C program (no C++), which proves the boundary is callable
 * across a real C ABI. It drives the engine through micropolis_c.h and checks
 * the invariants. With the stub implementation it FAILS; a correct
 * implementation makes it print "OK: C ABI smoke passed" and exit 0.
 */
#include "micropolis_c.h"
#include <stdio.h>
#include <string.h>

int main(void) {
    MicropolisEngine *e = micropolis_create();
    if (!e) { printf("FAIL: create returned NULL\n"); return 1; }

    micropolis_init(e);
    micropolis_generate_random_city(e);
    for (int i = 0; i < 500; i++) {
        micropolis_sim_tick(e);
    }

    MicropolisStats s;
    memset(&s, 0, sizeof s);
    micropolis_get_stats(e, &s);
    printf("stats: time=%d funds=%d pop=%d score=%d year=%d month=%d demand=%d/%d/%d level=%d\n",
           s.city_time, s.total_funds, s.city_pop, s.city_score, s.city_year,
           s.city_month, s.res_demand, s.com_demand, s.ind_demand, s.game_level);

    int W = micropolis_map_width();
    int H = micropolis_map_height();
    if (W != MICROPOLIS_MAP_W || H != MICROPOLIS_MAP_H) {
        printf("FAIL: dims are %d x %d\n", W, H);
        micropolis_destroy(e);
        return 1;
    }

    static unsigned short buf[MICROPOLIS_MAP_W * MICROPOLIS_MAP_H];
    int n = micropolis_copy_tiles(e, buf, (int)(sizeof buf / sizeof buf[0]));
    if (n != W * H) {
        printf("FAIL: copy_tiles returned %d, expected %d\n", n, W * H);
        micropolis_destroy(e);
        return 1;
    }

    /* get_tile must agree with the copied buffer (column-major: x*H + y). */
    unsigned short t = micropolis_get_tile(e, 10, 10);
    if (t != buf[10 * H + 10]) {
        printf("FAIL: get_tile(10,10)=%u but buf[10*H+10]=%u (layout mismatch)\n",
               t, buf[10 * H + 10]);
        micropolis_destroy(e);
        return 1;
    }

    int r = micropolis_do_tool(e, MICROPOLIS_TOOL_PARK, 40, 40);
    printf("do_tool(PARK,40,40) = %d\n", r);

    if (s.city_time <= 0) {
        printf("FAIL: city_time did not advance past 0\n");
        micropolis_destroy(e);
        return 1;
    }

    micropolis_destroy(e);
    printf("OK: C ABI smoke passed\n");
    return 0;
}
