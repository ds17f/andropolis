/*
 * micropolis_c.h — a stable C ABI over the Micropolis C++ engine.
 *
 * This is the boundary Kotlin/JNI calls. It hides all C++ and Emscripten types.
 * The header is the CONTRACT (authored by the planner); the implementation in
 * micropolis_c.cpp must match it exactly.
 *
 * Ownership & threading:
 *   - A MicropolisEngine* is created by micropolis_create and freed by
 *     micropolis_destroy. The caller owns the handle.
 *   - NOT thread-safe. Use one engine from one thread. The Android app runs the
 *     simulation on a single dedicated thread.
 *   - No engine memory escapes this API. The map is COPIED into a caller buffer.
 *   - const char* arguments are borrowed for the duration of the call only.
 *
 * v1 scope: lifecycle, world generation, simulation control, map read, stats,
 * and tools. Engine->host callbacks (messages, sounds, UI updates) are a later
 * task; v1 uses an internal no-op callback, and the frontend polls stats + map.
 */
#ifndef MICROPOLIS_C_H
#define MICROPOLIS_C_H

#ifdef __cplusplus
extern "C" {
#endif

/* Opaque handle to one simulation. */
typedef struct MicropolisEngine MicropolisEngine;

/* The world is a fixed size. */
#define MICROPOLIS_MAP_W 120
#define MICROPOLIS_MAP_H 100

/* ---- Lifecycle ---- */
MicropolisEngine *micropolis_create(void);
void             micropolis_destroy(MicropolisEngine *e);

/* ---- Setup / world generation ---- */
void micropolis_init(MicropolisEngine *e);
void micropolis_generate_random_city(MicropolisEngine *e);
void micropolis_generate_city_seed(MicropolisEngine *e, int seed);
/* Returns 1 on success, 0 on failure. */
int  micropolis_load_city(MicropolisEngine *e, const char *path);
/* Save the current city to a file. Returns 1 on success, 0 on failure. */
int  micropolis_save_city(MicropolisEngine *e, const char *path);

/* ---- Simulation control ---- */
void micropolis_sim_tick(MicropolisEngine *e);
void micropolis_sim_update(MicropolisEngine *e);
void micropolis_set_speed(MicropolisEngine *e, int speed);      /* 0..3 */
void micropolis_set_passes(MicropolisEngine *e, int passes);
void micropolis_set_city_tax(MicropolisEngine *e, int tax);
void micropolis_set_game_level(MicropolisEngine *e, int level); /* 0..2 */

/* ---- Map read (for rendering) ---- */
/* Tile value: the low 10 bits are the tile index; the upper bits are flags. */
int            micropolis_map_width(void);   /* == MICROPOLIS_MAP_W */
int            micropolis_map_height(void);  /* == MICROPOLIS_MAP_H */
unsigned short micropolis_get_tile(const MicropolisEngine *e, int x, int y);
/*
 * Copy the whole map into dst in COLUMN-MAJOR order: dst[x * H + y].
 * dst_len must be >= W*H (== 12000). Returns the number of tiles written,
 * or 0 if dst is NULL or dst_len is too small.
 */
int micropolis_copy_tiles(const MicropolisEngine *e,
                          unsigned short *dst, int dst_len);

/* ---- Stats (for the HUD) ---- */
typedef struct MicropolisStats {
    int city_time;     /* cityTime   */
    int total_funds;   /* totalFunds */
    int city_pop;      /* cityPop    */
    int city_score;    /* cityScore  */
    int city_year;     /* cityYear   */
    int city_month;    /* cityMonth  */
    int res_demand;    /* resValve   */
    int com_demand;    /* comValve   */
    int ind_demand;    /* indValve   */
    int game_level;    /* gameLevel  */
} MicropolisStats;
void micropolis_get_stats(const MicropolisEngine *e, MicropolisStats *out);

/* ---- Budget (for the HUD) ---- */
typedef struct MicropolisBudget {
    int total_funds; int tax_rate; int tax_income;
    int road_fund; int road_spend; int road_percent;
    int police_fund; int police_spend; int police_percent;
    int fire_fund; int fire_spend; int fire_percent;
} MicropolisBudget;   /* 12 ints */
void micropolis_get_budget(const MicropolisEngine *e, MicropolisBudget *out);

/* ---- Evaluation (for the HUD) ---- */
typedef struct MicropolisEvaluation {
    int city_score; int score_delta; int city_class;
    int city_pop; int pop_delta; int assessed_value; int approval;
} MicropolisEvaluation;   /* 7 ints */
void micropolis_get_evaluation(const MicropolisEngine *e, MicropolisEvaluation *out);

/* ---- Tools / interaction ---- */
/*
 * These MUST equal the engine's EditingTool (tool.h). The implementation adds
 * static_assert checks so the two can never drift apart.
 */
typedef enum MicropolisTool {
    MICROPOLIS_TOOL_RESIDENTIAL  = 0,
    MICROPOLIS_TOOL_COMMERCIAL   = 1,
    MICROPOLIS_TOOL_INDUSTRIAL   = 2,
    MICROPOLIS_TOOL_FIRESTATION  = 3,
    MICROPOLIS_TOOL_POLICESTATION= 4,
    MICROPOLIS_TOOL_QUERY        = 5,
    MICROPOLIS_TOOL_WIRE         = 6,
    MICROPOLIS_TOOL_BULLDOZER    = 7,
    MICROPOLIS_TOOL_RAILROAD     = 8,
    MICROPOLIS_TOOL_ROAD         = 9,
    MICROPOLIS_TOOL_STADIUM      = 10,
    MICROPOLIS_TOOL_PARK         = 11,
    MICROPOLIS_TOOL_SEAPORT      = 12,
    MICROPOLIS_TOOL_COALPOWER    = 13,
    MICROPOLIS_TOOL_NUCLEARPOWER = 14,
    MICROPOLIS_TOOL_AIRPORT      = 15,
    MICROPOLIS_TOOL_NETWORK      = 16,
    MICROPOLIS_TOOL_WATER        = 17,
    MICROPOLIS_TOOL_LAND         = 18,
    MICROPOLIS_TOOL_FOREST       = 19
} MicropolisTool;

/* These MUST equal the engine's ToolResult (micropolis.h). */
typedef enum MicropolisToolResult {
    MICROPOLIS_TOOL_NO_MONEY      = -2,
    MICROPOLIS_TOOL_NEED_BULLDOZE = -1,
    MICROPOLIS_TOOL_FAILED        =  0,
    MICROPOLIS_TOOL_OK            =  1
} MicropolisToolResult;

/* Apply a tool at tile (x, y). Returns a MicropolisToolResult value. */
int micropolis_do_tool(MicropolisEngine *e, int tool, int x, int y);

#ifdef __cplusplus
}
#endif

#endif /* MICROPOLIS_C_H */
