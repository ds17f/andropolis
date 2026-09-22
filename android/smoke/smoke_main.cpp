#include <cstdio>
#include "micropolis.h"
#include "micropolis_null_callback.h"

int main() {
    Micropolis *engine = new Micropolis();
    NullCallback *callback = new NullCallback();
    engine->setCallback(callback, emscripten::val());
    engine->init();
    engine->generateSomeRandomCity();

    for (int i = 0; i < 500; i++) {
        engine->simTick();
    }

    std::printf("cityTime=%d totalFunds=%d cityPop=%d\n",
                (int)engine->cityTime, (int)engine->totalFunds,
                (int)engine->cityPop);

    // Engine will delete the callback in its destructor
    delete engine;
    return 0;
}
