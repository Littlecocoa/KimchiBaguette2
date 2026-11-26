package screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import audio.AudioManager;
import engine.Core;
import engine.GameplayConfig;
import java.awt.Canvas;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GameplaySettingsScreenTest {
    private final Canvas inputSource = new Canvas();
    private int previousDifficulty;
    private double previousScreenShake;
    private boolean previousMute;
    private GameplaySettingsScreen screen;

    @BeforeEach
    void setUp() {
        previousDifficulty = GameplayConfig.getDifficulty();
        previousScreenShake = GameplayConfig.getScreenShakeIntensity();
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
        GameplayConfig.setDifficulty(previousDifficulty);
        GameplayConfig.setScreenShakeIntensity(previousScreenShake);
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
    void mouseClicksChangeBothValuesWithoutClosingUntilSpaceConfirms() {
        Point hardDifficulty = findOption(2);
        moveMouse(hardDifficulty);
        click(hardDifficulty);

        assertTrue(screen.isRunning);
        assertEquals(GameplayConfig.NORMAL, GameplayConfig.getDifficulty());
        assertEquals(GameplayConfig.SCREEN_SHAKE_NORMAL,
                GameplayConfig.getScreenShakeIntensity());

        Point lowScreenShake = findOption(4);
        moveMouse(lowScreenShake);
        click(lowScreenShake);

        assertTrue(screen.isRunning);
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
        Preferences.userNodeForPackage(GameplayConfig.class)
                .remove("screenShakeIntensity");

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

    private Point findOption(final int option) {
        for (int y = 0; y < screen.getHeight(); y++) {
            for (int x = 0; x < screen.getWidth(); x++) {
                if (Core.getDrawManager().getGameplaySettingsOptionAt(
                        screen, x, y) == option)
                    return new Point(x, y);
            }
        }
        throw new AssertionError("Gameplay option not found: " + option);
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
