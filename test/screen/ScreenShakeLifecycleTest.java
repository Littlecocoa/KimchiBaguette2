package screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import audio.AudioManager;
import engine.Core;
import engine.GameClock;
import engine.GameSettings;
import engine.GameState;
import engine.GameplayConfig;
import entity.Bullet;
import entity.BulletPool;
import entity.Ship;
import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vfx.ScreenShake;

class ScreenShakeLifecycleTest {
    private static final Canvas KEY_SOURCE = new Canvas();
    private static final String SCREEN_SHAKE_KEY = "screenShakeIntensity";

    private boolean previousMute;
    private String previousScreenShakePreference;

    @BeforeEach
    void setUp() {
        previousMute = AudioManager.isMuted();
        previousScreenShakePreference = screenShakePreferences().get(
                SCREEN_SHAKE_KEY, null);
        AudioManager.setMuted(true);
        GameplayConfig.setScreenShakeIntensity(
                GameplayConfig.SCREEN_SHAKE_NORMAL);
        releaseEscape();
        ScreenShake.reset();
        ScreenShake.setIntensity(1.0);
    }

    @AfterEach
    void cleanUp() {
        releaseEscape();
        GameClock.resume();
        ScreenShake.reset();
        ScreenShake.setIntensity(1.0);
        restoreScreenShakePreference(previousScreenShakePreference);
        AudioManager.setMuted(previousMute);
    }

    @Test
    void campaignScreenResetsShakeOnInitialization() {
        ScreenShake.add(1.0);

        campaignScreen();

        assertEquals(0.0, ScreenShake.getTrauma());
    }

    @Test
    void infiniteScreenResetsShakeOnInitialization() {
        ScreenShake.add(1.0);

        infiniteScreen();

        assertEquals(0.0, ScreenShake.getTrauma());
    }

    @Test
    void campaignScreenUpdatesShakeOncePerActiveFrame() {
        GameScreen screen = campaignScreen();
        screen.update();
        ScreenShake.add(1.0);

        screen.update();

        assertEquals(0.9, ScreenShake.getTrauma(), 1e-9);
    }

    @Test
    void infiniteScreenUpdatesShakeOncePerActiveFrame() {
        InfiniteGameScreen screen = infiniteScreen();
        ScreenShake.add(1.0);

        screen.update();

        assertEquals(0.9, ScreenShake.getTrauma(), 1e-9);
    }

    @Test
    void campaignEventKeepsItsFullTraumaForTheFirstRenderedFrame()
            throws Exception {
        GameScreen screen = campaignScreen();
        screen.update();
        addEnemyBulletAtShip(screen, "bullets");

        screen.update();

        assertEquals(0.80, ScreenShake.getTrauma(), 1e-9);
    }

    @Test
    void bossSpawnKeepsFullTraumaUntilItsFirstRenderedFrame() {
        GameScreen screen = campaignScreen(3);

        assertEquals(0.70, ScreenShake.getTrauma(), 1e-9);

        screen.update();
        assertEquals(0.70, ScreenShake.getTrauma(), 1e-9);

        screen.update();
        assertEquals(0.63, ScreenShake.getTrauma(), 1e-9);
    }

    @Test
    void infiniteEventKeepsItsFullTraumaForTheFirstRenderedFrame()
            throws Exception {
        InfiniteGameScreen screen = infiniteScreen();
        addEnemyBulletAtShip(screen, "enemyBullets");

        screen.update();

        assertEquals(0.80, ScreenShake.getTrauma(), 1e-9);
    }

    @Test
    void campaignPauseClearsTheCurrentShake() {
        GameScreen screen = campaignScreen();
        pauseWithTrauma(screen);

        assertTrue(screen.isPaused());
        assertEquals(0.0, ScreenShake.getTrauma());

        resumeWithTrauma(screen);
        assertFalse(screen.isPaused());
        assertEquals(0.0, ScreenShake.getTrauma());
    }

    @Test
    void infinitePauseClearsTheCurrentShake() {
        InfiniteGameScreen screen = infiniteScreen();
        pauseWithTrauma(screen);

        assertTrue(screen.isPaused());
        assertEquals(0.0, ScreenShake.getTrauma());

        resumeWithTrauma(screen);
        assertFalse(screen.isPaused());
        assertEquals(0.0, ScreenShake.getTrauma());
    }

    @Test
    void bothModesApplyEverySavedIntensityOnInitialization() {
        double[] intensities = {
                GameplayConfig.SCREEN_SHAKE_OFF,
                GameplayConfig.SCREEN_SHAKE_LOW,
                GameplayConfig.SCREEN_SHAKE_NORMAL
        };

        for (double intensity : intensities) {
            GameplayConfig.setScreenShakeIntensity(intensity);
            ScreenShake.setIntensity(intensity == 1.0 ? 0.0 : 1.0);
            campaignScreen();
            assertEquals(intensity, ScreenShake.getIntensity());

            ScreenShake.setIntensity(intensity == 1.0 ? 0.0 : 1.0);
            infiniteScreen();
            assertEquals(intensity, ScreenShake.getIntensity());
        }
    }

    @Test
    void preferenceSnapshotRestoresMissingAndExistingValues() {
        Preferences preferences = screenShakePreferences();

        preferences.remove(SCREEN_SHAKE_KEY);
        String missing = preferences.get(SCREEN_SHAKE_KEY, null);
        GameplayConfig.setScreenShakeIntensity(
                GameplayConfig.SCREEN_SHAKE_LOW);
        restoreScreenShakePreference(missing);
        assertEquals(null, preferences.get(SCREEN_SHAKE_KEY, null));

        preferences.putDouble(SCREEN_SHAKE_KEY,
                GameplayConfig.SCREEN_SHAKE_OFF);
        String existing = preferences.get(SCREEN_SHAKE_KEY, null);
        GameplayConfig.setScreenShakeIntensity(
                GameplayConfig.SCREEN_SHAKE_NORMAL);
        restoreScreenShakePreference(existing);
        assertEquals(existing, preferences.get(SCREEN_SHAKE_KEY, null));
    }

    private GameScreen campaignScreen() {
        return campaignScreen(1);
    }

    private GameScreen campaignScreen(final int level) {
        GameScreen screen = new GameScreen(
                new GameState(level, 0, 3, 0, 0),
                new GameSettings(5, 4, 60, 2000),
                false, 448, 520, 60);
        screen.initialize();
        return screen;
    }

    private InfiniteGameScreen infiniteScreen() {
        InfiniteGameScreen screen = new InfiniteGameScreen(
                new GameState(1, 0, 3, 0, 0), 448, 520, 60);
        screen.initialize();
        return screen;
    }

    private void pauseWithTrauma(final Screen screen) {
        screen.inputDelay = Core.getCooldown(0);
        ScreenShake.add(1.0);
        setEscape(true);
        try {
            screen.update();
        } finally {
            releaseEscape();
        }
    }

    private void resumeWithTrauma(final Screen screen) {
        screen.update();
        ScreenShake.add(1.0);
        setEscape(true);
        try {
            screen.update();
        } finally {
            releaseEscape();
        }
    }

    private Preferences screenShakePreferences() {
        return Preferences.userNodeForPackage(GameplayConfig.class);
    }

    private void restoreScreenShakePreference(final String value) {
        Preferences preferences = screenShakePreferences();
        if (value == null) {
            preferences.remove(SCREEN_SHAKE_KEY);
        } else {
            preferences.put(SCREEN_SHAKE_KEY, value);
        }
    }

    @SuppressWarnings("unchecked")
    private void addEnemyBulletAtShip(final Screen screen,
            final String bulletFieldName) throws Exception {
        Ship ship = (Ship) field(screen, "ship");
        Set<Bullet> bullets = (Set<Bullet>) field(screen, bulletFieldName);
        bullets.add(BulletPool.getBullet(
                ship.getPositionX() + ship.getWidth() / 2,
                ship.getPositionY(), 0, Bullet.Owner.ENEMY));
    }

    private Object field(final Screen screen, final String name)
            throws Exception {
        Field field = screen.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(screen);
    }

    private void releaseEscape() {
        setEscape(false);
    }

    private void setEscape(final boolean pressed) {
        KeyEvent event = new KeyEvent(KEY_SOURCE,
                pressed ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED,
                System.currentTimeMillis(), 0, KeyEvent.VK_ESCAPE,
                KeyEvent.CHAR_UNDEFINED);
        if (pressed) {
            Core.getInputManager().keyPressed(event);
        } else {
            Core.getInputManager().keyReleased(event);
        }
    }
}
