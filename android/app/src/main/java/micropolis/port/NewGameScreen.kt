package micropolis.port

// PLACEHOLDER (task 063 replaces this file): the "New game" screen.

/** ⋮ → New game. `resume` restarts the sim when the screen closes without starting a game too. */
internal fun MainActivity.showNewGame(resume: () -> Unit) {
    startNewMap(-1, -1, -1, -1, resume)
}
