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

/* ---- Map write (for undo) ----
 * Overwrite one tile's full 16-bit value (tile index + flag bits), then mark the
 * engine's maps dirty so derived data (power grid, overlays) is recomputed on the
 * next scans. Out-of-bounds coordinates are ignored.
 */
void micropolis_set_tile(MicropolisEngine *e, int x, int y, unsigned short value);
/* Set the city's funds directly (used to refund an undone build). */
void micropolis_set_funds(MicropolisEngine *e, int funds);

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

/* ---- Overlays (map render modes) ----
 * The engine keeps several data maps at lower resolution than the tile map.
 * micropolis_copy_overlay upsamples the selected map (nearest-neighbour) to the
 * full W*H tile grid and writes an intensity 0..255 per tile, so the renderer can
 * tint tiles without knowing the source resolution.
 */
typedef enum MicropolisOverlay {
    MICROPOLIS_OVERLAY_NONE       = 0,
    MICROPOLIS_OVERLAY_POPULATION = 1,  /* populationDensityMap (half res)  */
    MICROPOLIS_OVERLAY_TRAFFIC    = 2,  /* trafficDensityMap   (half res)  */
    MICROPOLIS_OVERLAY_POLLUTION  = 3,  /* pollutionDensityMap (half res)  */
    MICROPOLIS_OVERLAY_LANDVALUE  = 4,  /* landValueMap        (half res)  */
    MICROPOLIS_OVERLAY_CRIME      = 5,  /* crimeRateMap        (half res)  */
    MICROPOLIS_OVERLAY_GROWTH     = 6,  /* rateOfGrowthMap     (1/8 res)   */
    MICROPOLIS_OVERLAY_POWER      = 7   /* powerGridMap        (full res)  */
} MicropolisOverlay;
/*
 * Fill dst (dst_len must be >= W*H == 12000) COLUMN-MAJOR (dst[x*H + y]) with an
 * intensity 0..255 per tile for the given overlay. Growth is signed in the
 * engine; it is mapped to 0..255 with 128 == no growth. Returns the number of
 * tiles written, or 0 if dst is NULL, dst_len too small, or overlay is NONE.
 */
int micropolis_copy_overlay(const MicropolisEngine *e, int overlay,
                            unsigned char *dst, int dst_len);

/* ---- History graphs ----
 * Six tracked series, each at two time scales. Values are the engine's own
 * history values (already scaled for display).
 */
typedef enum MicropolisHistory {
    MICROPOLIS_HIST_RES       = 0,  /* HISTORY_TYPE_RES       */
    MICROPOLIS_HIST_COM       = 1,  /* HISTORY_TYPE_COM       */
    MICROPOLIS_HIST_IND       = 2,  /* HISTORY_TYPE_IND       */
    MICROPOLIS_HIST_MONEY     = 3,  /* HISTORY_TYPE_MONEY     */
    MICROPOLIS_HIST_CRIME     = 4,  /* HISTORY_TYPE_CRIME     */
    MICROPOLIS_HIST_POLLUTION = 5   /* HISTORY_TYPE_POLLUTION */
} MicropolisHistory;
#define MICROPOLIS_HISTORY_LEN 120   /* samples returned per series/scale */
/*
 * scale: 0 == short (10-year), 1 == long (120-year).
 * Fill dst (dst_len must be >= MICROPOLIS_HISTORY_LEN) with the series, ordered
 * OLDEST FIRST: dst[0] is the oldest sample, dst[LEN-1] the newest. Returns the
 * number of samples written, or 0 on bad args.
 */
int micropolis_get_history(const MicropolisEngine *e, int history, int scale,
                           int *dst, int dst_len);

/* ---- Disasters ---- */
typedef enum MicropolisDisaster {
    MICROPOLIS_DISASTER_FIRE       = 0,  /* makeFire       */
    MICROPOLIS_DISASTER_FLOOD      = 1,  /* makeFlood      */
    MICROPOLIS_DISASTER_TORNADO    = 2,  /* makeTornado    */
    MICROPOLIS_DISASTER_EARTHQUAKE = 3,  /* makeEarthquake */
    MICROPOLIS_DISASTER_MONSTER    = 4,  /* makeMonster    */
    MICROPOLIS_DISASTER_MELTDOWN   = 5   /* makeMeltdown   */
} MicropolisDisaster;
/* Trigger a disaster. Unknown values are ignored. */
void micropolis_make_disaster(MicropolisEngine *e, int disaster);
/* Turn the engine's own random disasters on (1) or off (0). Does not affect
 * micropolis_make_disaster. (Engine: Micropolis::setEnableDisasters.) */
void micropolis_set_enable_disasters(MicropolisEngine *e, int on);

/* ---- Budget funding ----
 * Manual funding levels as whole percents 0..100. This turns autoBudget OFF so
 * the manual levels take effect. Use micropolis_set_auto_budget to restore auto.
 */
void micropolis_set_funding(MicropolisEngine *e,
                            int road_pct, int fire_pct, int police_pct);
void micropolis_set_auto_budget(MicropolisEngine *e, int on); /* on: 1/0 */

/* ---- Events (engine -> app) ----
 * The engine calls back into the frontend for messages, sounds, zone queries and
 * "go look here" requests. v1 used a no-op callback. Now those callbacks push
 * structured events into an internal ring buffer that the app drains with
 * micropolis_poll_event after each tick. The callback fires on the SAME thread that
 * runs the simulation, and poll is called from that same thread, so no locking is
 * needed.
 */
typedef enum MicropolisEventType {
    MICROPOLIS_EVENT_MESSAGE     = 0, /* a=messageIndex(1..57), b=picture(0/1), c=important(0/1) */
    MICROPOLIS_EVENT_ZONE_STATUS = 1, /* query result: a=tileCat b=popDensity c=landValue
                                         d=crime e=pollution f=growthRate (all indexes) */
    MICROPOLIS_EVENT_AUTO_GOTO   = 2, /* engine asks the view to center on (x,y) */
    MICROPOLIS_EVENT_EARTHQUAKE  = 3, /* a=strength */
    MICROPOLIS_EVENT_LOSE        = 4, /* game over: lost */
    MICROPOLIS_EVENT_WIN         = 5, /* scenario won */
    MICROPOLIS_EVENT_SOUND       = 6  /* a=MicropolisSound id; x,y = source tile */
} MicropolisEventType;

/* Sounds the engine can request (makeSound). Unknown names are not enqueued. */
typedef enum MicropolisSound {
    MICROPOLIS_SOUND_SIREN          = 0,
    MICROPOLIS_SOUND_EXPLOSION_LOW  = 1,
    MICROPOLIS_SOUND_EXPLOSION_HIGH = 2,
    MICROPOLIS_SOUND_MONSTER        = 3,
    MICROPOLIS_SOUND_HONK_LOW       = 4,
    MICROPOLIS_SOUND_HONK_MED       = 5,
    MICROPOLIS_SOUND_HONK_HIGH      = 6,
    MICROPOLIS_SOUND_HEAVY_TRAFFIC  = 7,
    MICROPOLIS_SOUND_FOGHORN        = 8,
    MICROPOLIS_SOUND_UHUH           = 9,
    MICROPOLIS_SOUND_SORRY          = 10
} MicropolisSound;

/* A single event. x,y are tile coords (or -1 when not applicable); a..f are
 * type-specific payload (see MicropolisEventType). */
typedef struct MicropolisEvent {
    int type;
    int x, y;
    int a, b, c, d, e, f;
} MicropolisEvent;

/*
 * Dequeue the oldest pending event into *out. Returns 1 if an event was written,
 * 0 if the queue is empty (or on bad args). Call in a loop to drain.
 */
int micropolis_poll_event(MicropolisEngine *e, MicropolisEvent *out);

#ifdef __cplusplus
}
#endif

#endif /* MICROPOLIS_C_H */
