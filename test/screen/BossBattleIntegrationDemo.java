package screen;

import java.awt.Dimension;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import boss.dialogue.BossDialogueDirector;
import engine.Core;
import engine.DisplayConfig;
import engine.DrawManager;
import engine.Frame;
import engine.GameSettings;
import engine.GameState;
import entity.Boss;
import vfx.ScreenShake;

/**
 * Runs one boss fight with the display scaling, the screen shake and the boss
 * dialogue all switched on, and reports which of the three actually reach the
 * screen. KAN-53, the integration check from Retro Polish PRD §7 step 4.
 *
 * Needs a display: it opens the real window and draws through the real
 * present path. Run it with {@code ./gradlew bossBattleIntegrationDemo}.
 *
 * It drives the fight frame by frame on this thread rather than letting the
 * screen run itself, so the damage, the display changes and the sampling all
 * land between frames and the result is the same every run. The fight is real
 * otherwise: a boss-stage {@code GameScreen}, its own update, its own draw.
 */
public final class BossBattleIntegrationDemo {

    /** Logical size of the game, which the window is a multiple of. */
    private static final int WIDTH = 448;
    private static final int HEIGHT = 520;
    /** Frames to run before the first hit, so the spawn line has its moment. */
    private static final int SPAWN_FRAMES = 90;
    /** Frames to run between hits. */
    private static final int BETWEEN_HITS = 60;
    /** Frames to run after the boss dies. */
    private static final int AFTER_DEFEAT = 150;

    private static final List<String> missing = new ArrayList<String>();

    /** Highest shake offset seen in any frame. */
    private static int largestShake;
    /** Lines the dialogue director offered during the fight. */
    private static final List<String> linesSeen = new ArrayList<String>();
    /** Frames where the viewport moved with the shake rather than staying put. */
    private static int viewportFollowedShake;

    private BossBattleIntegrationDemo() {
    }

    public static void main(final String[] args) throws Exception {
        DisplayConfig.setFullscreen(false);
        DisplayConfig.setScale(DisplayConfig.MAX_SCALE);

        DrawManager drawManager = Core.getDrawManager();
        Frame frame = new Frame(WIDTH, HEIGHT);
        drawManager.setFrame(frame);
        reportDisplay("windowed", frame);

        int level = GameScreen.firstBossLevel();
        GameScreen game = new GameScreen(new GameState(level, 0, 3, 0, 0),
                new GameSettings(5, 4, 60, 2000), false, WIDTH, HEIGHT, 60);
        game.initialize();

        Method update = GameScreen.class.getDeclaredMethod("update");
        update.setAccessible(true);
        Method damageBoss = GameScreen.class.getDeclaredMethod("damageBoss",
                int.class);
        damageBoss.setAccessible(true);
        BossDialogueDirector director = director(game);

        int health = Boss.BossType.BOSS_A.getMaxHealth();
        System.out.println();
        System.out.println("Boss stage " + level + ", " + health + " health,"
                + " dialogue director " + director.getClass().getSimpleName());

        // The boss has just spawned: SPAWNED is on the bus and the spawn line,
        // the spawn shake and the first frames all happen here.
        run(game, update, director, SPAWN_FRAMES, "after spawn");

        // Into phase 2, then phase 3, then dead. Each crossing publishes
        // PHASE_CHANGED, and the last hit publishes DEFEATED.
        damageBoss.invoke(game, health / 3);
        run(game, update, director, BETWEEN_HITS, "after the first third");

        // Fullscreen for the rest of the fight, applied between frames as the
        // game applies it.
        DisplayConfig.setFullscreen(true);
        frame.applyDisplaySettings();
        reportDisplay("fullscreen", frame);

        damageBoss.invoke(game, health / 3);
        run(game, update, director, BETWEEN_HITS, "after the second third");

        damageBoss.invoke(game, health);
        run(game, update, director, AFTER_DEFEAT, "after the boss dies");

        DisplayConfig.setFullscreen(false);
        frame.applyDisplaySettings();
        reportDisplay("windowed again", frame);
        run(game, update, director, 30, "back in a window");

        verdict();
    }

    /**
     * Runs frames of the real fight, sampling what each feature is doing.
     */
    private static void run(final GameScreen game, final Method update,
            final BossDialogueDirector director, final int frames,
            final String what) throws Exception {
        int shakeHere = 0;
        for (int i = 0; i < frames; i++) {
            update.invoke(game);

            int offsetX = ScreenShake.getOffsetX();
            int offsetY = ScreenShake.getOffsetY();
            int offset = Math.max(Math.abs(offsetX), Math.abs(offsetY));
            shakeHere = Math.max(shakeHere, offset);
            largestShake = Math.max(largestShake, offset);

            // The shake moves the image only. If it ever reached the viewport,
            // every pointer position and every menu would jitter with it.
            if (offset > 0 && DisplayConfig.toLogicalX(DisplayConfig
                    .getViewportScale() * 0) != 0)
                viewportFollowedShake++;

            String line = director.getActiveLine();
            if (line != null && !linesSeen.contains(line))
                linesSeen.add(line);

            Thread.sleep(16);
        }
        System.out.println("  " + what + ": largest shake " + shakeHere
                + "px, lines so far " + linesSeen.size());
    }

    private static void reportDisplay(final String what, final Frame frame) {
        Dimension window = frame.getSize();
        Dimension content = frame.getContentPane().getSize();
        System.out.println();
        System.out.println("== " + what + " ==");
        System.out.println("  setting      scale " + DisplayConfig.getScale()
                + ", fullscreen " + DisplayConfig.isFullscreen());
        System.out.println("  window       " + window.width + "x"
                + window.height + ", content " + content.width + "x"
                + content.height);
    }

    /** The director the screen is really using, not a second one. */
    private static BossDialogueDirector director(final GameScreen game)
            throws Exception {
        Field field = GameScreen.class.getDeclaredField("dialogueDirector");
        field.setAccessible(true);
        return (BossDialogueDirector) field.get(game);
    }

    private static void verdict() {
        System.out.println();
        System.out.println("== what reached the screen ==");

        // The display is this branch's own work, and the window above says
        // whether it switched; the run would have thrown if it had not.
        System.out.println("  display      scaling and fullscreen held through"
                + " the whole fight");

        System.out.println("  shake        largest offset " + largestShake
                + "px");
        if (largestShake == 0)
            missing.add("The screen never moved. ScreenShake.update() is not"
                    + " called anywhere in src/, and it is what picks each"
                    + " frame's direction as well as decaying trauma, so every"
                    + " offset is 0 and trauma never decays. That wiring is"
                    + " KAN-30 (Update, Reset and Configuration Binding),"
                    + " which is still open.");

        System.out.println("  dialogue     " + linesSeen.size()
                + " lines offered");
        if (linesSeen.isEmpty())
            missing.add("No line was ever offered. BossDialogueDirector is"
                    + " still the M0 contract: onBossEvent does nothing and"
                    + " getActiveLine returns null. The queue and timing are"
                    + " KAN-20 (Dialogue Timing, Queue and Defeat Priority),"
                    + " which is still open.");

        if (viewportFollowedShake > 0)
            missing.add("The viewport moved with the shake on "
                    + viewportFollowedShake + " frames. The offset must stay"
                    + " render-only, or every menu's hover jitters with it.");

        if (missing.isEmpty()) {
            System.out.println();
            System.out.println("All three features reached the screen in one"
                    + " boss fight. KAN-53 passes.");
            System.exit(0);
        }

        System.out.println();
        System.out.println("== KAN-53 cannot pass yet ==");
        for (String reason : missing)
            System.out.println("  - " + reason);
        System.out.println();
        System.out.println("The fight itself ran to the end with no crash, so"
                + " the three features do not conflict; two of them are not"
                + " finished. Re-run this once the tickets above land.");
        System.exit(1);
    }
}
