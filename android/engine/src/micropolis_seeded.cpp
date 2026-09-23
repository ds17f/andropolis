/*
 * micropolis_seeded.cpp — a load whose post-load simulation init is deterministic.
 *
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
