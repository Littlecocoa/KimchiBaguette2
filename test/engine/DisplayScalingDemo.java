package engine;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import screen.Screen;

/**
 * Drives a window through every display state and checks that a pointer
 * position still lands on the menu entry it looks like it is on. KAN-36.
 *
 * Needs a display, so it is not part of {@code check}. Run it with
 * {@code ./gradlew displayScalingDemo} after changing anything about the
 * window, the scale or the pointer. What it checks cannot be seen from a
 * headless test, and what a headless test checks ({@link DisplayConfigTest})
 * is the arithmetic rather than the window.
 *
 * The window it opens is a quarter of the game's size. That is not a detail of
 * the game but of the machine: on a laptop screen the game at 2x is taller
 * than the desktop, so a full-size window could only ever be shown at 1x and
 * the scaling would go unexercised. The logical size is the only difference;
 * everything below is the real frame, the real present path and the real
 * InputManager.
 */
public final class DisplayScalingDemo {

    /** A quarter of the game's 448x520. */
    private static final int WIDTH = 224;
    private static final int HEIGHT = 260;
    /** Return code of the "Play" entry on the title menu. */
    private static final int PLAY_OPTION = 2;
    /** Return code of the "High scores" entry on the title menu. */
    private static final int HIGH_SCORES_OPTION = 3;
    /** Time to let the window settle after it changes shape. */
    private static final long SETTLE_MILLIS = 1500;

    private static final List<String> failures = new ArrayList<String>();
    private static volatile boolean drawing = true;
    private static volatile boolean applyPending;
    private static volatile int hoveredOption = -1;

    private DisplayScalingDemo() {
    }

    public static void main(final String[] args) throws Exception {
        DisplayConfig.setFullscreen(false);
        DisplayConfig.setScale(DisplayConfig.MIN_SCALE);

        DrawManager drawManager = Core.getDrawManager();
        Frame frame = new Frame(WIDTH, HEIGHT);
        drawManager.setFrame(frame);
        Screen screen = new Screen(WIDTH, HEIGHT, 60);
        startDrawing(frame, drawManager, screen);

        for (int scale = DisplayConfig.MIN_SCALE;
                scale <= DisplayConfig.MAX_SCALE; scale++) {
            DisplayConfig.setFullscreen(false);
            DisplayConfig.setScale(scale);
            apply();
            report(scale + "x window", frame, drawManager, screen);
        }

        DisplayConfig.setFullscreen(true);
        apply();
        report("fullscreen", frame, drawManager, screen);

        DisplayConfig.setFullscreen(false);
        DisplayConfig.setScale(2);
        apply();
        report("2x window again", frame, drawManager, screen);

        // A size nobody would pick, as if the window had been dragged there:
        // the player can do this now that the window is resizable, and every
        // size has to present and read the pointer like any other.
        resizeByHand(frame, 2 * WIDTH + 97, 2 * HEIGHT + 53);
        report("dragged to an awkward size", frame, drawManager, screen);

        drawing = false;
        Thread.sleep(200);

        if (!failures.isEmpty()) {
            for (String failure : failures)
                System.out.println("FAILED " + failure);
            System.out.println();
            System.out.println("If a pointer check failed on its own, keep"
                    + " the mouse off the window and run it again: a real"
                    + " pointer over the window reports its own position over"
                    + " the top of the one being tested.");
            System.exit(1);
        }
        System.out.println();
        System.out.println("Display scaling checks passed.");
        System.exit(0);
    }

    /** Stands in for a screen's run loop. */
    private static void startDrawing(final Frame frame,
            final DrawManager drawManager, final Screen screen)
            throws Exception {
        Thread loop = new Thread(() -> {
            while (drawing) {
                drawManager.initDrawing(screen);
                drawManager.drawTitle(screen);
                drawManager.drawMenu(screen, hoveredOption);
                drawManager.completeDrawing(screen);

                // Where the game applies it too: on the thread that draws,
                // between frames, which is what the frame asks for.
                if (applyPending) {
                    applyPending = false;
                    frame.applyDisplaySettings();
                }
                try {
                    Thread.sleep(16);
                } catch (InterruptedException interrupted) {
                    return;
                }
            }
        }, "draw");
        loop.setDaemon(true);
        loop.start();
        Thread.sleep(SETTLE_MILLIS);
    }

    /**
     * Resizes the window the way dragging its corner would, rather than
     * through the settings.
     *
     * @param frame
     *              Window to resize.
     * @param width
     *              Content width to ask for.
     * @param height
     *              Content height to ask for.
     */
    private static void resizeByHand(final Frame frame, final int width,
            final int height) throws Exception {
        java.awt.Insets insets = frame.getInsets();
        javax.swing.SwingUtilities.invokeAndWait(() -> frame.setSize(
                width + insets.left + insets.right,
                height + insets.top + insets.bottom));
        Thread.sleep(SETTLE_MILLIS);
    }

    /** Asks the drawing thread to apply the settings, and waits for it. */
    private static void apply() throws Exception {
        applyPending = true;
        for (int attempt = 0; attempt < 100 && applyPending; attempt++)
            Thread.sleep(50);
        Thread.sleep(SETTLE_MILLIS);
    }

    private static void report(final String what, final Frame frame,
            final DrawManager drawManager, final Screen screen)
            throws Exception {
        Dimension window = frame.getSize();
        Dimension content = frame.getRenderTarget().getSize();
        int scale = DisplayConfig.getViewportScale();
        int marginX = DrawManager.letterbox(content.width, WIDTH * scale);
        int marginY = DrawManager.letterbox(content.height, HEIGHT * scale);

        System.out.println();
        System.out.println("== " + what + " ==");
        System.out.println("  setting      scale " + DisplayConfig.getScale()
                + ", fullscreen " + DisplayConfig.isFullscreen()
                + ", swing.bufferPerWindow "
                + System.getProperty("swing.bufferPerWindow"));
        System.out.println("  window       " + window.width + "x"
                + window.height + ", content " + content.width + "x"
                + content.height);
        System.out.println("  presented    " + scale + "x, letterbox "
                + marginX + "," + marginY + ", image " + (WIDTH * scale) + "x"
                + (HEIGHT * scale));

        check(what, frame.getWidth() == WIDTH && frame.getHeight() == HEIGHT,
                "the logical size changed with the window");
        check(what, content.width >= WIDTH * scale
                && content.height >= HEIGHT * scale,
                "the image does not fit in the content area");
        if (!DisplayConfig.isFullscreen() && !what.startsWith("dragged"))
            check(what, scale == DisplayConfig.getScale(),
                    "asked for " + DisplayConfig.getScale() + "x, presented "
                            + scale + "x");

        if (DisplayConfig.isFullscreen()) {
            // The window covers the screen, so the real pointer is on it
            // wherever it happens to be and reports over the top of anything
            // posted here. Hover a menu entry by hand instead; the arithmetic
            // itself is covered by DisplayConfigTest.
            System.out.println("  pointer      not checked in fullscreen, see"
                    + " docs/Display_Scaling_Controls.md");
            return;
        }
        checkPointer(what, frame, drawManager, screen, scale, marginX,
                marginY);
    }

    /**
     * Puts the pointer on a menu entry and checks the game reads it as being
     * on that entry, at whatever scale is on screen. This is what breaks
     * first and most visibly when the transform is wrong.
     */
    private static void checkPointer(final String what, final Frame frame,
            final DrawManager drawManager, final Screen screen,
            final int scale, final int marginX, final int marginY)
            throws Exception {
        for (int option : new int[] { HIGH_SCORES_OPTION, PLAY_OPTION }) {
            Point logical = findOption(drawManager, screen, option);
            if (logical == null) {
                check(what, false, "could not find menu option " + option);
                continue;
            }

            // Middle of the block of screen pixels that one logical pixel
            // covers, which is where someone aiming at it would click.
            movePointer(frame, marginX + logical.x * scale + scale / 2,
                    marginY + logical.y * scale + scale / 2);

            int readX = Core.getInputManager().getMouseX();
            int readY = Core.getInputManager().getMouseY();
            int hovered = drawManager.getMenuOptionAt(screen, readX, readY);
            hoveredOption = hovered;
            System.out.println("  pointer      on option " + option + " at "
                    + logical.x + "," + logical.y + " reads " + readX + ","
                    + readY + " -> option " + hovered);

            check(what, readX == logical.x && readY == logical.y,
                    "pointer on " + logical.x + "," + logical.y
                            + " read back as " + readX + "," + readY);
            check(what, hovered == option, "pointer on option " + option
                    + " was read as option " + hovered);
            Thread.sleep(300);
        }

        // The letterbox is not part of the game, and says so the same way a
        // pointer that left the window does.
        if (marginX > 0) {
            movePointer(frame, marginX - 1, marginY + HEIGHT / 2);
            check(what, Core.getInputManager().getMouseX() == -1,
                    "a pointer in the left letterbox read as on the game");
        }
        if (marginY > 0) {
            movePointer(frame, marginX + WIDTH / 2, marginY - 1);
            check(what, Core.getInputManager().getMouseY() == -1,
                    "a pointer in the top letterbox read as on the game");
        }
    }

    /** First logical point down the middle that is on that menu entry. */
    private static Point findOption(final DrawManager drawManager,
            final Screen screen, final int option) {
        for (int y = 0; y < HEIGHT; y++)
            if (drawManager.getMenuOptionAt(screen, WIDTH / 2, y) == option)
                return new Point(WIDTH / 2, y);
        return null;
    }

    /**
     * Moves the pointer by handing the window the event it would get, rather
     * than taking over the real mouse, which belongs to whoever is running
     * this.
     *
     * @param frame
     *              Window to move the pointer within.
     * @param x
     *              Position in the content area.
     * @param y
     *              Position in the content area.
     */
    private static void movePointer(final Frame frame, final int x,
            final int y) throws Exception {
        Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(
                new MouseEvent(frame.getRenderTarget(),
                        MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0,
                        x, y, 0, false));
        Thread.sleep(150);
    }

    private static void check(final String what, final boolean condition,
            final String message) {
        if (!condition)
            failures.add(what + ": " + message);
    }
}
