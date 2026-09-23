#pragma once
#include "micropolis_null_callback.h"
#include "micropolis_c.h"   // MicropolisEvent, MicropolisEventType

// A Callback that captures the engine's frontend events into a fixed ring buffer.
// It inherits every no-op override from NullCallback and only overrides the methods
// that carry events we surface to the app. push()/pop() run on the simulation thread
// (the engine calls back during simTick; the app polls right after), so no locking.
class QueueCallback : public NullCallback {
public:
    static const int CAP = 64;
    MicropolisEvent ring[CAP];
    int head = 0;
    int count = 0;

    void push(const MicropolisEvent &ev) {
        if (count == CAP) {           // full: drop the oldest
            head = (head + 1) % CAP;
            count--;
        }
        ring[(head + count) % CAP] = ev;
        count++;
    }

    bool pop(MicropolisEvent *out) {
        if (count == 0) return false;
        *out = ring[head];
        head = (head + 1) % CAP;
        count--;
        return true;
    }

    virtual void sendMessage(Micropolis *, emscripten::val, int messageIndex,
                             int x, int y, bool picture, bool important) override {
        MicropolisEvent ev{};
        ev.type = MICROPOLIS_EVENT_MESSAGE; ev.x = x; ev.y = y;
        ev.a = messageIndex; ev.b = picture ? 1 : 0; ev.c = important ? 1 : 0;
        push(ev);
    }

    virtual void showZoneStatus(Micropolis *, emscripten::val, int tileCategoryIndex,
                                int populationDensityIndex, int landValueIndex,
                                int crimeRateIndex, int pollutionIndex,
                                int growthRateIndex, int x, int y) override {
        MicropolisEvent ev{};
        ev.type = MICROPOLIS_EVENT_ZONE_STATUS; ev.x = x; ev.y = y;
        ev.a = tileCategoryIndex; ev.b = populationDensityIndex; ev.c = landValueIndex;
        ev.d = crimeRateIndex; ev.e = pollutionIndex; ev.f = growthRateIndex;
        push(ev);
    }

    virtual void autoGoto(Micropolis *, emscripten::val, int x, int y,
                          std::string /*message*/) override {
        MicropolisEvent ev{};
        ev.type = MICROPOLIS_EVENT_AUTO_GOTO; ev.x = x; ev.y = y;
        push(ev);
    }

    virtual void makeSound(Micropolis *, emscripten::val, std::string /*channel*/,
                           std::string sound, int x, int y) override {
        static const struct { const char *name; int id; } kSounds[] = {
            {"Siren", MICROPOLIS_SOUND_SIREN},
            {"ExplosionLow", MICROPOLIS_SOUND_EXPLOSION_LOW},
            {"ExplosionHigh", MICROPOLIS_SOUND_EXPLOSION_HIGH},
            {"Monster", MICROPOLIS_SOUND_MONSTER},
            {"HonkHonkLow", MICROPOLIS_SOUND_HONK_LOW},
            {"HonkHonkMed", MICROPOLIS_SOUND_HONK_MED},
            {"HonkHonkHigh", MICROPOLIS_SOUND_HONK_HIGH},
            {"HeavyTraffic", MICROPOLIS_SOUND_HEAVY_TRAFFIC},
            {"FogHornLow", MICROPOLIS_SOUND_FOGHORN},
            {"UhUh", MICROPOLIS_SOUND_UHUH},
            {"Sorry", MICROPOLIS_SOUND_SORRY},
        };
        for (const auto &s : kSounds) {
            if (sound == s.name) {
                MicropolisEvent ev{};
                ev.type = MICROPOLIS_EVENT_SOUND; ev.x = x; ev.y = y; ev.a = s.id;
                push(ev);
                return;
            }
        }
    }

    virtual void startEarthquake(Micropolis *, emscripten::val, int strength) override {
        MicropolisEvent ev{};
        ev.type = MICROPOLIS_EVENT_EARTHQUAKE; ev.x = -1; ev.y = -1; ev.a = strength;
        push(ev);
    }

    virtual void didLoseGame(Micropolis *, emscripten::val) override {
        MicropolisEvent ev{}; ev.type = MICROPOLIS_EVENT_LOSE; ev.x = -1; ev.y = -1;
        push(ev);
    }

    virtual void didWinGame(Micropolis *, emscripten::val) override {
        MicropolisEvent ev{}; ev.type = MICROPOLIS_EVENT_WIN; ev.x = -1; ev.y = -1;
        push(ev);
    }
};
