/*
 * micropolis_c.cpp — bridge the C ABI in micropolis_c.h to the C++ Micropolis engine.
 */
#include "micropolis_c.h"
#include "micropolis.h"
#include "micropolis_null_callback.h"
#include "tool.h"
#include <cstring>
#include <new>

// Add static_asserts to prove C enums match C++ enums
static_assert(MICROPOLIS_TOOL_RESIDENTIAL == TOOL_RESIDENTIAL, "Tool enum mismatch");
static_assert(MICROPOLIS_TOOL_COMMERCIAL == TOOL_COMMERCIAL, "Tool enum mismatch");
static_assert(MICROPOLIS_TOOL_INDUSTRIAL == TOOL_INDUSTRIAL, "Tool enum mismatch");
static_assert(MICROPOLIS_TOOL_FIRESTATION == TOOL_FIRESTATION, "Tool enum mismatch");
static_assert(MICROPOLIS_TOOL_POLICESTATION == TOOL_POLICESTATION, "Tool enum mismatch");
static_assert(MICROPOLIS_TOOL_QUERY == TOOL_QUERY, "Tool enum mismatch");
static_assert(MICROPOLIS_TOOL_WIRE == TOOL_WIRE, "Tool enum mismatch");
static_assert(MICROPOLIS_TOOL_BULLDOZER == TOOL_BULLDOZER, "Tool enum mismatch");
static_assert(MICROPOLIS_TOOL_RAILROAD == TOOL_RAILROAD, "Tool enum mismatch");
static_assert(MICROPOLIS_TOOL_ROAD == TOOL_ROAD, "Tool enum mismatch");
static_assert(MICROPOLIS_TOOL_STADIUM == TOOL_STADIUM, "Tool enum mismatch");
static_assert(MICROPOLIS_TOOL_PARK == TOOL_PARK, "Tool enum mismatch");
static_assert(MICROPOLIS_TOOL_SEAPORT == TOOL_SEAPORT, "Tool enum mismatch");
static_assert(MICROPOLIS_TOOL_COALPOWER == TOOL_COALPOWER, "Tool enum mismatch");
static_assert(MICROPOLIS_TOOL_NUCLEARPOWER == TOOL_NUCLEARPOWER, "Tool enum mismatch");
static_assert(MICROPOLIS_TOOL_AIRPORT == TOOL_AIRPORT, "Tool enum mismatch");
static_assert(MICROPOLIS_TOOL_NETWORK == TOOL_NETWORK, "Tool enum mismatch");
static_assert(MICROPOLIS_TOOL_WATER == TOOL_WATER, "Tool enum mismatch");
static_assert(MICROPOLIS_TOOL_LAND == TOOL_LAND, "Tool enum mismatch");
static_assert(MICROPOLIS_TOOL_FOREST == TOOL_FOREST, "Tool enum mismatch");

// ToolResult enum checks
static_assert(MICROPOLIS_TOOL_NO_MONEY == TOOLRESULT_NO_MONEY, "ToolResult enum mismatch");
static_assert(MICROPOLIS_TOOL_NEED_BULLDOZE == TOOLRESULT_NEED_BULLDOZE, "ToolResult enum mismatch");
static_assert(MICROPOLIS_TOOL_FAILED == TOOLRESULT_FAILED, "ToolResult enum mismatch");
static_assert(MICROPOLIS_TOOL_OK == TOOLRESULT_OK, "ToolResult enum mismatch");

// Opaque handle type wrapping the Micropolis engine and a null callback.
// Both are heap-allocated because Micropolis::setCallback(NULL, ...)
// in the destructor will delete the callback.
struct MicropolisEngine {
    Micropolis *sim;
    NullCallback *callback;
};

extern "C" {

MicropolisEngine *micropolis_create(void) {
    MicropolisEngine *e = new MicropolisEngine();
    e->callback = new NullCallback();
    // Upstream Micropolis::Micropolis() does not initialize its `callback`
    // member, so setCallback()'s `if (callback != NULL) delete callback;` frees a
    // garbage pointer on the first call. That is harmless only where the heap
    // happens to be zero (host malloc by luck, WASM linear memory by spec) and
    // CRASHES on a real non-zeroed heap (Android). Zero the storage before
    // constructing so every member — including `callback` — starts at 0.
    void *mem = ::operator new(sizeof(Micropolis));
    std::memset(mem, 0, sizeof(Micropolis));
    e->sim = new (mem) Micropolis();
    e->sim->setCallback(e->callback, emscripten::val());
    return e;
}

void micropolis_destroy(MicropolisEngine *e) {
    if (e) {
        if (e->sim) {
            e->sim->~Micropolis();      // matches the placement new in create()
            ::operator delete(e->sim);  // the ~Micropolis already deleted callback
        }
        delete e;
    }
}

void micropolis_init(MicropolisEngine *e) {
    if (e) {
        e->sim->init();
    }
}

void micropolis_generate_random_city(MicropolisEngine *e) {
    if (e) {
        e->sim->generateSomeRandomCity();
    }
}

void micropolis_generate_city_seed(MicropolisEngine *e, int seed) {
    if (e) {
        e->sim->generateMap(seed);
    }
}

int micropolis_load_city(MicropolisEngine *e, const char *path) {
    if (e && path) {
        return e->sim->loadCity(std::string(path)) ? 1 : 0;
    }
    return 0;
}

void micropolis_sim_tick(MicropolisEngine *e) {
    if (e) {
        e->sim->simTick();
    }
}

void micropolis_sim_update(MicropolisEngine *e) {
    if (e) {
        e->sim->simUpdate();
    }
}

void micropolis_set_speed(MicropolisEngine *e, int speed) {
    if (e) {
        e->sim->setSpeed(short(speed));
    }
}

void micropolis_set_passes(MicropolisEngine *e, int passes) {
    if (e) {
        e->sim->setPasses(passes);
    }
}

void micropolis_set_city_tax(MicropolisEngine *e, int tax) {
    if (e) {
        e->sim->setCityTax(short(tax));
    }
}

void micropolis_set_game_level(MicropolisEngine *e, int level) {
    if (e) {
        e->sim->setGameLevel(GameLevel(level));
    }
}

int micropolis_map_width(void) { return MICROPOLIS_MAP_W; }
int micropolis_map_height(void) { return MICROPOLIS_MAP_H; }

unsigned short micropolis_get_tile(const MicropolisEngine *e, int x, int y) {
    if (e && Micropolis::testBounds(x, y)) {
        return const_cast<Micropolis *>(e->sim)->getTile(x, y);
    }
    return 0;
}

int micropolis_copy_tiles(const MicropolisEngine *e, unsigned short *dst, int dst_len) {
    if (!e || !dst || dst_len < MICROPOLIS_MAP_W * MICROPOLIS_MAP_H) {
        return 0;
    }
    
    // Copy in column-major order: dst[x * H + y]
    for (int x = 0; x < MICROPOLIS_MAP_W; x++) {
        for (int y = 0; y < MICROPOLIS_MAP_H; y++) {
            dst[x * MICROPOLIS_MAP_H + y] = const_cast<Micropolis *>(e->sim)->getTile(x, y);
        }
    }
    return MICROPOLIS_MAP_W * MICROPOLIS_MAP_H;
}

void micropolis_get_stats(const MicropolisEngine *e, MicropolisStats *out) {
    if (!e || !out) {
        return;
    }
    
    out->city_time = int(e->sim->cityTime);
    out->total_funds = int(e->sim->totalFunds);
    out->city_pop = int(e->sim->cityPop);
    out->city_score = int(e->sim->cityScore);
    out->city_year = int(e->sim->cityYear);
    out->city_month = int(e->sim->cityMonth);
    out->res_demand = int(e->sim->resValve);
    out->com_demand = int(e->sim->comValve);
    out->ind_demand = int(e->sim->indValve);
    out->game_level = int(e->sim->gameLevel);
}

int micropolis_do_tool(MicropolisEngine *e, int tool, int x, int y) {
    if (!e) {
        return MICROPOLIS_TOOL_FAILED;
    }
    
    ToolResult result = e->sim->doTool(EditingTool(tool), short(x), short(y));
    return int(result);
}

} /* extern "C" */
