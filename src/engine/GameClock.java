package engine;

/**
 * Gameplay time source that can be frozen. Every gameplay timer reads this
 * clock instead of the wall clock, so pausing freezes all of them at once and
 * no timer needs its own pause bookkeeping.
 *
 * Contract for the pause work. The logic here is complete, but nothing reads
 * it yet: routing {@code Cooldown} and {@code PlayerStatus} through
 * {@link #now()} is the pausable clock task (P1), which also calls
 * {@link #reset()} when a run starts so paused time cannot leak between runs.
 *
 * Note that RunSession and RecordsService deliberately measure real elapsed
 * time and must not be routed through this clock without asking their owners.
 */
public final class GameClock {

    /** Wall time when the pause began, 0 while running. */
    private static long pausedAt = 0;
    /** Accumulated paused milliseconds. */
    private static long totalPaused = 0;

    private GameClock() {
    }

    /**
     * @return Gameplay time in milliseconds. Does not advance while paused.
     */
    public static long now() {
        if (pausedAt != 0)
            return pausedAt - totalPaused;
        return System.currentTimeMillis() - totalPaused;
    }

    /** Freezes the clock. Does nothing if it is already frozen. */
    public static void pause() {
        if (pausedAt == 0)
            pausedAt = System.currentTimeMillis();
    }

    /** Restarts the clock, keeping the time spent paused out of it. */
    public static void resume() {
        if (pausedAt != 0) {
            totalPaused += System.currentTimeMillis() - pausedAt;
            pausedAt = 0;
        }
    }

    /** Clears the clock for a new run. */
    public static void reset() {
        pausedAt = 0;
        totalPaused = 0;
    }
}
