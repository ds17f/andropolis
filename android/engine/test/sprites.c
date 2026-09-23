/*
 * sprites.c — moving objects are created safely and copy_sprites reports them.
 * Run with junk-filled malloc (MALLOC_PERTURB_=77) too: upstream newSprite() used
 * to assign into an unconstructed std::string and crashed on the host.
 * Prints "OK: sprites passed" and exits 0 on success.
 */
#include "micropolis_c.h"
#include <stdio.h>

int main(void) {
    MicropolisEngine *e = micropolis_create();
    micropolis_init(e);
    micropolis_generate_city_seed(e, 42);
    micropolis_set_speed(e, 3);
    for (int i = 0; i < 50; i++) micropolis_sim_tick(e);
    micropolis_make_disaster(e, 2);               /* tornado */
    int buf[128], seen = 0;
    for (int t = 0; t < 40; t++) {
        micropolis_sim_tick(e);
        int n = micropolis_copy_sprites(e, buf, 32);
        for (int i = 0; i < n; i++)
            if (buf[i * 4] == 6 && buf[i * 4 + 1] >= 1) seen++;
    }
    micropolis_destroy(e);
    /* a second engine in the same process must work too (background play makes many) */
    e = micropolis_create(); micropolis_init(e); micropolis_generate_city_seed(e, 7);
    micropolis_make_disaster(e, 2);
    for (int t = 0; t < 10; t++) micropolis_sim_tick(e);
    micropolis_destroy(e);
    if (!seen) { printf("FAIL: no tornado sprite reported\n"); return 1; }
    printf("tornado seen on %d ticks\nOK: sprites passed\n", seen);
    return 0;
}
