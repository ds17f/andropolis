#pragma once
#include "callback.h"

// A Callback that does nothing. Use it for headless runs (no JS, no UI).
// Override EVERY pure virtual method of Callback with an empty body.
// Methods that return a value return a default value (0, false, "").
class NullCallback : public Callback {
public:
    virtual ~NullCallback() {}

    virtual void autoGoto(Micropolis *micropolis, emscripten::val callbackVal, int x, int y, std::string message) override {}
    virtual void didGenerateMap(Micropolis *micropolis, emscripten::val callbackVal, int seed) override {}
    virtual void didLoadCity(Micropolis *micropolis, emscripten::val callbackVal, std::string filename) override {}
    virtual void didLoadScenario(Micropolis *micropolis, emscripten::val callbackVals, std::string name, std::string fname) override {}
    virtual void didLoseGame(Micropolis *micropolis, emscripten::val callbackVal) override {}
    virtual void didSaveCity(Micropolis *micropolis, emscripten::val callbackVal, std::string filename) override {}
    virtual void didTool(Micropolis *micropolis, emscripten::val callbackVal, std::string name, int x, int y) override {}
    virtual void didWinGame(Micropolis *micropolis, emscripten::val callbackVal) override {}
    virtual void didntLoadCity(Micropolis *micropolis, emscripten::val callbackVal, std::string filename) override {}
    virtual void didntSaveCity(Micropolis *micropolis, emscripten::val callbackVal, std::string filename) override {}
    virtual void makeSound(Micropolis *micropolis, emscripten::val callbackVal, std::string channel, std::string sound, int x, int y) override {}
    virtual void newGame(Micropolis *micropolis, emscripten::val callbackVal) override {}
    virtual void saveCityAs(Micropolis *micropolis, emscripten::val callbackVal, std::string filename) override {}
    virtual void sendMessage(Micropolis *micropolis, emscripten::val callbackVal, int messageIndex, int x, int y, bool picture, bool important) override {}
    virtual void showBudgetAndWait(Micropolis *micropolis, emscripten::val callbackVal) override {}
    virtual void showZoneStatus(Micropolis *micropolis, emscripten::val callbackVal, int tileCategoryIndex, int populationDensityIndex, int landValueIndex, int crimeRateIndex, int pollutionIndex, int growthRateIndex, int x, int y) override {}
    virtual void simulateRobots(Micropolis *micropolis, emscripten::val callbackVal) override {}
    virtual void simulateChurch(Micropolis *micropolis, emscripten::val callbackVal, int posX, int posY, int churchNumber) override {}
    virtual void startEarthquake(Micropolis *micropolis, emscripten::val callbackVal, int strength) override {}
    virtual void startGame(Micropolis *micropolis, emscripten::val callbackVal) override {}
    virtual void startScenario(Micropolis *micropolis, emscripten::val callbackVal, int scenario) override {}
    virtual void updateBudget(Micropolis *micropolis, emscripten::val callbackVal) override {}
    virtual void updateCityName(Micropolis *micropolis, emscripten::val callbackVal, std::string cityName) override {}
    virtual void updateDate(Micropolis *micropolis, emscripten::val callbackVal, int cityYear, int cityMonth) override {}
    virtual void updateDemand(Micropolis *micropolis, emscripten::val callbackVal, float r, float c, float i) override {}
    virtual void updateEvaluation(Micropolis *micropolis, emscripten::val callbackVal) override {}
    virtual void updateFunds(Micropolis *micropolis, emscripten::val callbackVal, int totalFunds) override {}
    virtual void updateGameLevel(Micropolis *micropolis, emscripten::val callbackVal, int gameLevel) override {}
    virtual void updateHistory(Micropolis *micropolis, emscripten::val callbackVal) override {}
    virtual void updateMap(Micropolis *micropolis, emscripten::val callbackVal) override {}
    virtual void updateOptions(Micropolis *micropolis, emscripten::val callbackVal) override {}
    virtual void updatePasses(Micropolis *micropolis, emscripten::val callbackVal, int passes) override {}
    virtual void updatePaused(Micropolis *micropolis, emscripten::val callbackVal, bool simPaused) override {}
    virtual void updateSpeed(Micropolis *micropolis, emscripten::val callbackVal, int speed) override {}
    virtual void updateTaxRate(Micropolis *micropolis, emscripten::val callbackVal, int cityTax) override {}
};
