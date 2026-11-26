package screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import audio.AudioManager;
import engine.Core;
import engine.DrawManager;
import engine.GameplayConfig;
import java.awt.Canvas;
import java.awt.FontMetrics;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GameplaySettingsScreenTest {
    private static final String DIFFICULTY_KEY = "difficulty";
    private static final String SCREEN_SHAKE_KEY = "screenShakeIntensity";

    private final Canvas inputSource = new Canvas();
    private String previousDifficultyPreference;
    private String previousScreenShakePreference;
    private boolean previousMute;
    private GameplaySettingsScreen screen;

    @BeforeEach
    void setUp() {
        Preferences preferences = gameplayPreferences();
        previousDifficultyPreference = preferences.get(DIFFICULTY_KEY, null);
        previousScreenShakePreference = preferences.get(SCREEN_SHAKE_KEY, null);
        previousMute = AudioManager.isMuted();
        AudioManager.setMuted(true);
        releaseInput();
        GameplayConfig.setDifficulty(GameplayConfig.NORMAL);
        GameplayConfig.setScreenShakeIntensity(
                GameplayConfig.SCREEN_SHAKE_NORMAL);
        openScreen();
    }

    @AfterEach
    void cleanUp() {
        releaseInput();
        restorePreference(DIFFICULTY_KEY, previousDifficultyPreference);
        restorePreference(SCREEN_SHAKE_KEY, previousScreenShakePreference);
        AudioManager.setMuted(previousMute);
    }

    @Test
    void keyboardChangesRowsIndependentlyAndConfirmsBoth() {
        tap(KeyEvent.VK_RIGHT);
        tap(KeyEvent.VK_DOWN);
        tap(KeyEvent.VK_LEFT);

        assertEquals(GameplayConfig.NORMAL, GameplayConfig.getDifficulty());
        assertEquals(GameplayConfig.SCREEN_SHAKE_NORMAL,
                GameplayConfig.getScreenShakeIntensity());

        tap(KeyEvent.VK_SPACE);

        assertFalse(screen.isRunning);
        assertEquals(GameplayConfig.HARD, GameplayConfig.getDifficulty());
        assertEquals(GameplayConfig.SCREEN_SHAKE_LOW,
                GameplayConfig.getScreenShakeIntensity());
    }

    @Test
    void keyboardCanSelectConfirmAndSaveBothSettings() {
        tap(KeyEvent.VK_RIGHT);
        tap(KeyEvent.VK_S);
        tap(KeyEvent.VK_A);
        tap(KeyEvent.VK_DOWN);

        assertEquals(2, selectedOption());
        assertTrue(screen.isRunning);

        tap(KeyEvent.VK_SPACE);

        assertFalse(screen.isRunning);
        assertEquals(GameplayConfig.HARD, GameplayConfig.getDifficulty());
        assertEquals(GameplayConfig.SCREEN_SHAKE_LOW,
                GameplayConfig.getScreenShakeIntensity());
    }

    @Test
    void horizontalKeysDoNotChangeValuesWhenConfirmIsSelected() {
        tap(KeyEvent.VK_W);
        assertEquals(2, selectedOption());

        tap(KeyEvent.VK_LEFT);
        assertEquals(GameplayConfig.NORMAL, selectedDifficulty());
        assertEquals(2, selectedScreenShake());

        tap(KeyEvent.VK_D);
        assertEquals(GameplayConfig.NORMAL, selectedDifficulty());
        assertEquals(2, selectedScreenShake());
        assertTrue(screen.isRunning);
    }

    @Test
    void mouseAndKeyboardShareOneNavigationSelection() {
        Point confirm = findOption(6, GameplayConfig.NORMAL, 2);
        moveMouse(confirm);
        assertEquals(2, selectedOption());

        tap(KeyEvent.VK_UP);
        assertEquals(1, selectedOption());
    }

    @Test
    void escapeDiscardsKeyboardChanges() {
        tap(KeyEvent.VK_LEFT);
        tap(KeyEvent.VK_DOWN);
        tap(KeyEvent.VK_LEFT);
        tap(KeyEvent.VK_ESCAPE);

        assertFalse(screen.isRunning);
        assertEquals(GameplayConfig.NORMAL, GameplayConfig.getDifficulty());
        assertEquals(GameplayConfig.SCREEN_SHAKE_NORMAL,
                GameplayConfig.getScreenShakeIntensity());
    }

    @Test
    void savedSettingsAreLoadedWhenTheScreenIsReopened() {
        tap(KeyEvent.VK_RIGHT);
        tap(KeyEvent.VK_DOWN);
        tap(KeyEvent.VK_LEFT);
        tap(KeyEvent.VK_SPACE);

        openScreen();

        assertEquals(GameplayConfig.HARD, selectedDifficulty());
        assertEquals(1, selectedScreenShake());
    }

    @Test
    void mouseCanChangeBothValuesAndConfirmWithoutKeyboard() {
        Point hardDifficulty = findOption(2,
                GameplayConfig.NORMAL, 2);
        moveMouse(hardDifficulty);
        click(hardDifficulty);

        assertTrue(screen.isRunning);
        assertEquals(GameplayConfig.NORMAL, GameplayConfig.getDifficulty());
        assertEquals(GameplayConfig.SCREEN_SHAKE_NORMAL,
                GameplayConfig.getScreenShakeIntensity());

        Point lowScreenShake = findOption(4,
                GameplayConfig.HARD, 2);
        moveMouse(lowScreenShake);
        click(lowScreenShake);

        assertTrue(screen.isRunning);
        assertEquals(GameplayConfig.NORMAL, GameplayConfig.getDifficulty());
        assertEquals(GameplayConfig.SCREEN_SHAKE_NORMAL,
                GameplayConfig.getScreenShakeIntensity());

        Point confirm = findOption(6, GameplayConfig.HARD, 1);
        moveMouse(confirm);
        click(confirm);

        assertFalse(screen.isRunning);
        assertEquals(GameplayConfig.HARD, GameplayConfig.getDifficulty());
        assertEquals(GameplayConfig.SCREEN_SHAKE_LOW,
                GameplayConfig.getScreenShakeIntensity());
    }

    @Test
    void selectedValueBracketsAreInsideTheMouseHitArea() throws Exception {
        Point normalDifficulty = findOption(1,
                GameplayConfig.NORMAL, 2);
        FontMetrics metrics = gameplayFontMetrics();
        int width = metrics.stringWidth("< Normal >");
        int centerX = screen.getWidth() / 2;
        int left = centerX - width / 2;

        assertEquals(1, Core.getDrawManager().getGameplaySettingsOptionAt(
                screen, GameplayConfig.NORMAL, 2,
                left, normalDifficulty.y));
        assertEquals(1, Core.getDrawManager().getGameplaySettingsOptionAt(
                screen, GameplayConfig.NORMAL, 2,
                left + width, normalDifficulty.y));
    }

    @Test
    void screenShakeValuesAreLimitedToTheThreeAccessibilityLevels() {
        double[] values = {
                GameplayConfig.SCREEN_SHAKE_OFF,
                GameplayConfig.SCREEN_SHAKE_LOW,
                GameplayConfig.SCREEN_SHAKE_NORMAL
        };

        for (double value : values) {
            GameplayConfig.setScreenShakeIntensity(value);
            assertEquals(value, GameplayConfig.getScreenShakeIntensity());
        }

        GameplayConfig.setScreenShakeIntensity(
                GameplayConfig.SCREEN_SHAKE_LOW);
        GameplayConfig.setScreenShakeIntensity(0.25);
        assertEquals(GameplayConfig.SCREEN_SHAKE_LOW,
                GameplayConfig.getScreenShakeIntensity());
    }

    @Test
    void screenShakeDefaultsToNormalWhenNoPreferenceExists() {
        gameplayPreferences().remove(SCREEN_SHAKE_KEY);

        assertEquals(GameplayConfig.SCREEN_SHAKE_NORMAL,
                GameplayConfig.getScreenShakeIntensity());
    }

    private void openScreen() {
        screen = new GameplaySettingsScreen(448, 520, 60);
        screen.inputDelay = Core.getMenuCooldown(0);
        screen.isRunning = true;
        screen.update();
        finishSelectionCooldown();
    }

    private void tap(final int keyCode) {
        key(keyCode, true);
        screen.update();
        key(keyCode, false);
        finishSelectionCooldown();
    }

    private void finishSelectionCooldown() {
        try {
            var field = GameplaySettingsScreen.class
                    .getDeclaredField("selectionCooldown");
            field.setAccessible(true);
            var cooldown = (engine.Cooldown) field.get(screen);
            cooldown.setDuration(0);
            var time = engine.Cooldown.class.getDeclaredField("time");
            time.setAccessible(true);
            time.setLong(cooldown, 0);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private Point findOption(final int option, final int difficulty,
            final int screenShake) {
        for (int y = 0; y < screen.getHeight(); y++) {
            for (int x = 0; x < screen.getWidth(); x++) {
                if (Core.getDrawManager().getGameplaySettingsOptionAt(
                        screen, difficulty, screenShake, x, y) == option)
                    return new Point(x, y);
            }
        }
        throw new AssertionError("Gameplay option not found: " + option);
    }

    private FontMetrics gameplayFontMetrics() throws Exception {
        var field = DrawManager.class.getDeclaredField("fontRegularMetrics");
        field.setAccessible(true);
        return (FontMetrics) field.get(null);
    }

    private int selectedOption() {
        return intField("selectedOption");
    }

    private int selectedDifficulty() {
        return intField("selectedDifficulty");
    }

    private int selectedScreenShake() {
        return intField("selectedScreenShake");
    }

    private int intField(final String name) {
        try {
            var field = GameplaySettingsScreen.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.getInt(screen);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private Preferences gameplayPreferences() {
        return Preferences.userNodeForPackage(GameplayConfig.class);
    }

    private void restorePreference(final String key, final String value) {
        if (value == null) {
            gameplayPreferences().remove(key);
        } else {
            gameplayPreferences().put(key, value);
        }
    }

    private void moveMouse(final Point point) {
        Core.getInputManager().mouseMoved(new MouseEvent(inputSource,
                MouseEvent.MOUSE_MOVED, 0, 0, point.x, point.y, 0, false));
        screen.update();
    }

    private void click(final Point point) {
        Core.getInputManager().mousePressed(new MouseEvent(inputSource,
                MouseEvent.MOUSE_PRESSED, 0, 0, point.x, point.y, 1, false,
                MouseEvent.BUTTON1));
        screen.update();
        Core.getInputManager().mouseReleased(new MouseEvent(inputSource,
                MouseEvent.MOUSE_RELEASED, 0, 0, point.x, point.y, 1, false,
                MouseEvent.BUTTON1));
    }

    private void key(final int code, final boolean down) {
        KeyEvent event = new KeyEvent(inputSource,
                down ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED,
                0, 0, code, KeyEvent.CHAR_UNDEFINED);
        if (down)
            Core.getInputManager().keyPressed(event);
        else
            Core.getInputManager().keyReleased(event);
    }

    private void releaseInput() {
        for (int code = 0; code < 256; code++)
            key(code, false);
        Core.getInputManager().mouseReleased(new MouseEvent(inputSource,
                MouseEvent.MOUSE_RELEASED, 0, 0, -1, -1, 1, false,
                MouseEvent.BUTTON1));
        Core.getInputManager().mouseExited(new MouseEvent(inputSource,
                MouseEvent.MOUSE_EXITED, 0, 0, -1, -1, 0, false));
        Core.getInputManager().clearLastPressedKey();
    }
}
