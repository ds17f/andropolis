# Headless smoke test: link the engine, tick a city, print stats.
# smoke_main.cpp and null_callback.h are written by the executor (task 001).
add_executable(micropolis_smoke smoke_main.cpp)
target_link_libraries(micropolis_smoke PRIVATE micropolis_engine)
