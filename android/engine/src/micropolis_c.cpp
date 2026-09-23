/*
 * micropolis_c.cpp — bridge the C ABI in micropolis_c.h to the C++ Micropolis engine.
 */
#include "micropolis_c.h"
#include "micropolis.h"
#include "micropolis_null_callback.h"
#include "micropolis_queue_callback.h"
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
// C++ helpers in micropolis_seeded.cpp (engine internals; upstream untouched).
bool micropolisSeededLoad(Micropolis *sim, const std::string &path, UQuad rng);
void micropolisPrimeSpritePool(Micropolis *sim);
void micropolisFreeSpritePool(Micropolis *sim);
bool micropolisLoadScenario(Micropolis *sim, int s, const std::string &path);
void micropolisSimTick(Micropolis *sim);

struct MicropolisEngine {
    Micropolis *sim;
    QueueCallback *callback;   // captures engine events into a ring buffer
};

extern "C" {

MicropolisEngine *micropolis_create(void) {
    MicropolisEngine *e = new MicropolisEngine();
    e->callback = new QueueCallback();
    // Upstream Micropolis::Micropolis() does not initialize its `callback`
    // member, so setCallback()'s `if (callback != NULL) delete callback;` frees a
    // garbage pointer on the first call. That is harmless only where the heap
    // happens to be zero (host malloc by luck, WASM linear memory by spec) and
    // CRASHES on a real non-zeroed heap (Android). Zero the storage before
    // constructing so every member — including `callback` — starts at 0.
    void *mem = ::operator new(sizeof(Micropolis));
    std::memset(mem, 0, sizeof(Micropolis));
    // The memset is a store before the object's lifetime starts, so an optimizing
    // compiler may drop it as dead (GCC -O2 did: callback and mapBase stayed
    // garbage and a second engine crashed). This barrier says the memory may be
    // read here, so the zeroing stays.
    asm volatile("" : : "r"(mem) : "memory");
    e->sim = new (mem) Micropolis();
    e->sim->callback = nullptr;   // belt and braces for setCallback() below
    e->sim->setCallback(e->callback, emscripten::val());
    return e;
}

void micropolis_destroy(MicropolisEngine *e) {
    if (e) {
        if (e->sim) {
            micropolisFreeSpritePool(e->sim);
            e->sim->~Micropolis();      // matches the placement new in create()
            ::operator delete(e->sim);  // the ~Micropolis already deleted callback
        }
        delete e;
    }
}

void micropolis_init(MicropolisEngine *e) {
    if (e) {
        e->sim->init();                      // (resets freeSprites to NULL)
        micropolisPrimeSpritePool(e->sim);   // upstream newSprite() mallocs unconstructed SimSprites
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

int micropolis_save_city(MicropolisEngine *e, const char *path) {
    if (e && path) {
        return e->sim->saveFile(std::string(path)) ? 1 : 0;
    }
    return 0;
}

void micropolis_sim_tick(MicropolisEngine *e) {
    if (e) {
        micropolisSimTick(e->sim);   // simTick() that keeps the monster alive (micropolis_seeded.cpp)
    }
}

void micropolis_set_terrain(MicropolisEngine *e, int trees, int lakes, int river, int island) {
    if (!e) return;
    e->sim->terrainTreeLevel = trees;
    e->sim->terrainLakeLevel = lakes;
    e->sim->terrainCurveLevel = river;
    e->sim->terrainCreateIsland = island;
}

int micropolis_load_scenario(MicropolisEngine *e, int scenario, const char *path) {
    return (e && path && micropolisLoadScenario(e->sim, scenario, std::string(path))) ? 1 : 0;
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

void micropolis_get_budget(const MicropolisEngine *e, MicropolisBudget *out) {
    if (!e || !out) return;
    Micropolis *s = e->sim;
    out->total_funds = int(s->totalFunds); out->tax_rate = int(s->cityTax); out->tax_income = int(s->taxFund);
    out->road_fund = int(s->roadFund);   out->road_spend = int(s->roadSpend);   out->road_percent = int(s->roadPercent * 100);
    out->police_fund = int(s->policeFund); out->police_spend = int(s->policeSpend); out->police_percent = int(s->policePercent * 100);
    out->fire_fund = int(s->fireFund);   out->fire_spend = int(s->fireSpend);   out->fire_percent = int(s->firePercent * 100);
}

void micropolis_get_evaluation(const MicropolisEngine *e, MicropolisEvaluation *out) {
    if (!e || !out) return;
    Micropolis *s = e->sim;
    out->city_score = int(s->cityScore); out->score_delta = int(s->cityScoreDelta); out->city_class = int(s->cityClass);
    out->city_pop = int(s->cityPop); out->pop_delta = int(s->cityPopDelta);
    out->assessed_value = int(s->cityAssessedValue); out->approval = int(s->cityYes);
}

int micropolis_get_problems(const MicropolisEngine *e, int *ids, int *votes, int n) {
    if (!e || !ids || !votes || n <= 0) return 0;
    Micropolis *s = e->sim;
    int count = s->countProblems();
    if (count > n) count = n;
    for (int i = 0; i < count; i++) {
        ids[i] = s->getProblemNumber(i);
        votes[i] = s->getProblemVotes(i);
    }
    return count;
}

int micropolis_do_tool(MicropolisEngine *e, int tool, int x, int y) {
    if (!e) {
        return MICROPOLIS_TOOL_FAILED;
    }
    
    ToolResult result = e->sim->doTool(EditingTool(tool), short(x), short(y));
    return int(result);
}

int micropolis_copy_overlay(const MicropolisEngine *e, int overlay, unsigned char *dst, int dst_len) {
    if (!e || !dst || dst_len < MICROPOLIS_MAP_W * MICROPOLIS_MAP_H || overlay == MICROPOLIS_OVERLAY_NONE) {
        return 0;
    }
    
    // Copy in column-major order: dst[x * H + y]
    for (int x = 0; x < MICROPOLIS_MAP_W; x++) {
        for (int y = 0; y < MICROPOLIS_MAP_H; y++) {
            unsigned char val = 0;
            switch (overlay) {
                case MICROPOLIS_OVERLAY_POPULATION:
                    val = (unsigned char)e->sim->populationDensityMap.worldGet(x, y);
                    break;
                case MICROPOLIS_OVERLAY_TRAFFIC:
                    val = (unsigned char)e->sim->trafficDensityMap.worldGet(x, y);
                    break;
                case MICROPOLIS_OVERLAY_POLLUTION:
                    val = (unsigned char)e->sim->pollutionDensityMap.worldGet(x, y);
                    break;
                case MICROPOLIS_OVERLAY_LANDVALUE:
                    val = (unsigned char)e->sim->landValueMap.worldGet(x, y);
                    break;
                case MICROPOLIS_OVERLAY_CRIME:
                    val = (unsigned char)e->sim->crimeRateMap.worldGet(x, y);
                    break;
                case MICROPOLIS_OVERLAY_GROWTH: {
                    short g = e->sim->rateOfGrowthMap.worldGet(x, y);
                    int v = 128 + g;
                    if (v < 0) v = 0;
                    if (v > 255) v = 255;
                    val = (unsigned char)v;
                    break;
                }
                case MICROPOLIS_OVERLAY_POWER:
                    val = e->sim->powerGridMap.worldGet(x, y) ? 255 : 0;
                    break;
                default:
                    val = 0;
                    break;
            }
            dst[x * MICROPOLIS_MAP_H + y] = val;
        }
    }
    return MICROPOLIS_MAP_W * MICROPOLIS_MAP_H;
}

int micropolis_get_history(const MicropolisEngine *e, int history, int scale, int *dst, int dst_len) {
    if (!e || !dst || dst_len < MICROPOLIS_HISTORY_LEN ||
        history < MICROPOLIS_HIST_RES || history > MICROPOLIS_HIST_POLLUTION ||
        scale < 0 || scale > 1) {
        return 0;
    }
    
    int histType;
    switch (history) {
        case MICROPOLIS_HIST_RES:       histType = 0; break;
        case MICROPOLIS_HIST_COM:       histType = 1; break;
        case MICROPOLIS_HIST_IND:       histType = 2; break;
        case MICROPOLIS_HIST_MONEY:     histType = 3; break;
        case MICROPOLIS_HIST_CRIME:     histType = 4; break;
        case MICROPOLIS_HIST_POLLUTION: histType = 5; break;
        default:                        histType = 0; break;
    }
    
    // Fill oldest-first: dst[0] oldest, dst[LEN-1] newest
    for (int i = 0; i < MICROPOLIS_HISTORY_LEN; i++) {
        dst[i] = e->sim->getHistory(histType, scale, (MICROPOLIS_HISTORY_LEN - 1) - i);
    }
    return MICROPOLIS_HISTORY_LEN;
}

void micropolis_make_disaster(MicropolisEngine *e, int disaster) {
    if (!e) return;
    switch (disaster) {
        case MICROPOLIS_DISASTER_FIRE:       e->sim->makeFire(); break;
        case MICROPOLIS_DISASTER_FLOOD:      e->sim->makeFlood(); break;
        case MICROPOLIS_DISASTER_TORNADO:    e->sim->makeTornado(); break;
        case MICROPOLIS_DISASTER_EARTHQUAKE: e->sim->makeEarthquake(); break;
        case MICROPOLIS_DISASTER_MONSTER:    e->sim->makeMonster(); break;
        case MICROPOLIS_DISASTER_MELTDOWN:   e->sim->makeMeltdown(); break;
        default:                             break;
    }
}

void micropolis_set_funding(MicropolisEngine *e, int road_pct, int fire_pct, int police_pct) {
    if (!e) return;
    
    // Clamp to 0..100
    if (road_pct < 0) road_pct = 0;
    if (road_pct > 100) road_pct = 100;
    if (fire_pct < 0) fire_pct = 0;
    if (fire_pct > 100) fire_pct = 100;
    if (police_pct < 0) police_pct = 0;
    if (police_pct > 100) police_pct = 100;
    
    e->sim->roadPercent = road_pct / 100.0f;
    e->sim->firePercent = fire_pct / 100.0f;
    e->sim->policePercent = police_pct / 100.0f;
    e->sim->setAutoBudget(false);
}

void micropolis_set_tile(MicropolisEngine *e, int x, int y, unsigned short value) {
    if (!e || x < 0 || y < 0 || x >= MICROPOLIS_MAP_W || y >= MICROPOLIS_MAP_H) return;
    e->sim->setTile(x, y, value);
    e->sim->invalidateMaps();
}

void micropolis_set_funds(MicropolisEngine *e, int funds) {
    if (e) e->sim->setFunds(funds);
}

void micropolis_set_enable_disasters(MicropolisEngine *e, int on) {
    if (!e) return;
    e->sim->setEnableDisasters(on != 0);
}

void micropolis_set_auto_budget(MicropolisEngine *e, int on) {
    if (!e) return;
    e->sim->setAutoBudget(on != 0);
}

int micropolis_load_city_seeded(MicropolisEngine *e, const char *path, long long rng) {
    return (e && path && micropolisSeededLoad(e->sim, std::string(path), (UQuad) rng)) ? 1 : 0;
}

int micropolis_copy_sprites(const MicropolisEngine *e, int *out, int max) {
    if (!e || !out || max <= 0) return 0;
    int n = 0;
    for (SimSprite *s = e->sim->spriteList; s && n < max; s = s->next) {
        if (s->frame == 0) continue;               // inactive
        out[n * 4 + 0] = s->type;
        out[n * 4 + 1] = s->frame;
        out[n * 4 + 2] = s->x + s->xOffset;
        out[n * 4 + 3] = s->y + s->yOffset;
        n++;
    }
    return n;
}

long long micropolis_get_rng(const MicropolisEngine *e) {
    return e ? (long long) e->sim->nextRandom : 0;
}

void micropolis_set_rng(MicropolisEngine *e, long long state) {
    if (e) e->sim->nextRandom = (UQuad) state;
}

int micropolis_poll_event(MicropolisEngine *e, MicropolisEvent *out) {
    if (!e || !out || !e->callback) return 0;
    return e->callback->pop(out) ? 1 : 0;
}

} /* extern "C" */
