/*
 * micropolis_seeded.cpp — engine internals we must reach without editing upstream:
 *   1. a load whose post-load simulation init is deterministic;
 *   2. a sprite pool of properly constructed SimSprite objects (see below);
 *   3. loading a scenario from a path we choose (see below);
 *   4. a tick that keeps the monster alive (see below).
 *
 * 1. Seeded load.
 * Micropolis::loadFile() calls initWillStuff(), which seeds the PRNG from the
 * clock, and then doSimInit(), which scans the whole map with that PRNG (zones
 * change). So load(S) gives a different city on every run, and setting the PRNG
 * after the load is too late. Background play needs load + replay to be exact
 * (DESIGN.md 12.5), so this file re-runs the init with a PRNG state we choose.
 *
 * initWillStuff() and doSimInit() are private upstream, and MicropolisCore stays
 * untouched (DESIGN.md), so this one translation unit opens the class. Access
 * specifiers do not change the member layout on GCC/Clang. The standard headers
 * are included first so the #define touches only micropolis.h.
 */
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <cstdarg>
#include <cmath>
#include <string>
#include <vector>
#include <map>
#include <new>
#include <sys/stat.h>
#include <sys/time.h>
#include <sys/types.h>
#include <unistd.h>

#define private public
#define protected public
#include "micropolis.h"
#undef private
#undef protected

bool micropolisSeededLoad(Micropolis *sim, const std::string &path, UQuad rng) {
    if (!sim->loadFile(path)) return false;     // parses funds, time, tax, funding, flags
    if (!sim->loadFileData(path)) return false; // put the raw map + history back as saved
    sim->initWillStuff();                       // (seeds from the clock...)
    sim->nextRandom = rng;                      // ...so replace the seed before the scan
    sim->scenario = SC_NONE;
    sim->initSimLoad = 1;
    sim->doInitialEval = false;
    sim->doSimInit();
    sim->invalidateMaps();
    return true;
}

/*
 * 2. Sprite pool. Upstream newSprite() takes a pooled sprite if one is free, else it
 * mallocs raw memory and assigns to its std::string `name` without constructing it.
 * That only works when malloc happens to return zeroed memory (usually on Android,
 * never on glibc: the host crashes on the first tornado). So each engine starts with
 * a pool of constructed sprites; destroySprite() returns sprites to the pool, so
 * upstream never needs to malloc one. The engine never frees sprites itself.
 */
static const int SPRITE_POOL = 64;   // the engine keeps about one sprite per type (+ explosions)

void micropolisPrimeSpritePool(Micropolis *sim) {
    for (int i = 0; i < SPRITE_POOL; i++) {
        void *mem = std::malloc(sizeof(SimSprite));
        if (!mem) break;
        std::memset(mem, 0, sizeof(SimSprite));
        SimSprite *s = new (mem) SimSprite();
        s->next = sim->freeSprites;
        sim->freeSprites = s;
    }
}

/* Free the pool at engine teardown, but only if every sprite is one of ours. */
void micropolisFreeSpritePool(Micropolis *sim) {
    int n = 0;
    for (SimSprite *s = sim->spriteList; s; s = s->next) n++;
    for (SimSprite *s = sim->freeSprites; s; s = s->next) n++;
    if (n != SPRITE_POOL) return;           // upstream malloc'd extras: unsafe to destruct; leak instead
    SimSprite *lists[2] = { sim->spriteList, sim->freeSprites };
    for (SimSprite *s : lists) {
        while (s) { SimSprite *next = s->next; s->~SimSprite(); std::free(s); s = next; }
    }
    sim->spriteList = nullptr;
    sim->freeSprites = nullptr;
    for (int i = 0; i < SPRITE_COUNT; i++) sim->globalSprites[i] = nullptr;
}

/*
 * 3. Scenarios. Upstream loadScenario() opens "cities/scenario_*.cty" relative to the
 * process working directory (not usable on Android) and starts with
 * `std::string name = NULL;` (undefined behaviour). This is the same procedure with the
 * file path passed in. Table copied from upstream fileio.cpp.
 */
bool micropolisLoadScenario(Micropolis *sim, int s, const std::string &path) {
    struct Sc { const char *name; int year; int funds; };
    static const Sc table[] = {
        { "",               0,    0     },  // SC_NONE
        { "Dullsville",     1900, 5000  },
        { "San Francisco",  1906, 20000 },
        { "Hamburg",        1944, 20000 },
        { "Bern",           1965, 20000 },
        { "Tokyo",          1957, 20000 },
        { "Detroit",        1972, 20000 },
        { "Boston",         2010, 20000 },
        { "Rio de Janeiro", 2047, 20000 },
    };
    if (s < SC_DULLSVILLE || s > SC_RIO) return false;
    sim->cityFileName = "";
    sim->setGameLevel(LEVEL_EASY);
    sim->scenario = (Scenario) s;
    sim->cityTime = ((table[s].year - 1900) * 48) + 2;
    sim->setFunds(table[s].funds);
    sim->setCleanCityName(table[s].name);
    sim->setSpeed(3);
    sim->setCityTax(7);
    if (!sim->loadFileData(path)) return false;
    sim->initWillStuff();
    sim->initFundingLevel();
    sim->updateFunds();
    sim->invalidateMaps();
    sim->initSimLoad = 1;
    sim->doInitialEval = false;
    sim->doSimInit();
    sim->didLoadScenario(s, table[s].name, path);
    return true;
}

/*
 * 4. Tick. Upstream kills the monster when it stands on RIVER while count != 0, yet
 * makeMonster() spawns it in the river with count = 1000, so it died in the same tick
 * it was born (simFrame spawns it, moveObjects kills it). This is Micropolis::simTick()
 * with simLoop(true) written out, clearing the monster's count between simFrame() and
 * moveObjects(). count = 0 removes only the river rule: the monster still walks to the
 * pollution peak and back, then leaves. Same work in the same order otherwise, so
 * replays stay deterministic.
 */
static void keepMonsterAlive(Micropolis *sim) {
    for (SimSprite *s = sim->spriteList; s; s = s->next)
        if (s->type == SPRITE_MONSTER) s->count = 0;
}

void micropolisSimTick(Micropolis *sim) {
    if (sim->simSpeed) {
        for (sim->simPass = 0; sim->simPass < sim->simPasses; sim->simPass++) {
            if (sim->heatSteps) { sim->simLoop(true); continue; }   // cellular-automaton mode: unchanged
            sim->simFrame();
            keepMonsterAlive(sim);
            sim->moveObjects();
            sim->simulateRobots();
            sim->simLoops++;
        }
    }
    sim->simUpdate();
}
