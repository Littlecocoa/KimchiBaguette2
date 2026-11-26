package engine;

import java.util.prefs.Preferences;

public final class GameplayConfig {
    public static final int EASY = 0;
    public static final int NORMAL = 1;
    public static final int HARD = 2;

    public static final double SCREEN_SHAKE_OFF = 0.0;
    public static final double SCREEN_SHAKE_LOW = 0.5;
    public static final double SCREEN_SHAKE_NORMAL = 1.0;

    private static final String DIFFICULTY_KEY = "difficulty";
    private static final String SCREEN_SHAKE_KEY = "screenShakeIntensity";
    private static final Preferences PREFS =
            Preferences.userNodeForPackage(GameplayConfig.class);

    private GameplayConfig() {
    }

    public static int getDifficulty() {
        int value = PREFS.getInt(DIFFICULTY_KEY, NORMAL);
        if (value < EASY || value > HARD) {
            return NORMAL;
        }
        return value;
    }

    public static void setDifficulty(final int difficulty) {
        if (difficulty >= EASY && difficulty <= HARD) {
            PREFS.putInt(DIFFICULTY_KEY, difficulty);
        }
    }

    public static double getMultiplier() {
        switch (getDifficulty()) {
        case EASY:
            return 1.25;
        case HARD:
            return 0.8;
        default:
            return 1.0;
        }
    }

    public static double getScreenShakeIntensity() {
        double value = PREFS.getDouble(SCREEN_SHAKE_KEY,
                SCREEN_SHAKE_NORMAL);
        if (!isScreenShakeIntensity(value)) {
            return SCREEN_SHAKE_NORMAL;
        }
        return value;
    }

    public static void setScreenShakeIntensity(final double intensity) {
        if (isScreenShakeIntensity(intensity)) {
            PREFS.putDouble(SCREEN_SHAKE_KEY, intensity);
        }
    }

    private static boolean isScreenShakeIntensity(final double intensity) {
        return intensity == SCREEN_SHAKE_OFF
                || intensity == SCREEN_SHAKE_LOW
                || intensity == SCREEN_SHAKE_NORMAL;
    }
}
