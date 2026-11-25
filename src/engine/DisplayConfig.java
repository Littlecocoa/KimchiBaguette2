package engine;

import java.util.prefs.Preferences;

/**
 * Window scale and fullscreen state, and the viewport the game is presented
 * through.
 *
 * Two related things live here. The scale and the fullscreen flag are what the
 * player asked for, and they persist across runs next to the other settings.
 * The viewport is where the image actually landed inside the window this
 * frame: {@link DrawManager} publishes it while presenting, and
 * {@link InputManager} reads it back to turn a pointer position into the
 * coordinates the game draws in. The two halves have to agree, or the menus
 * stop matching what is on screen, which is why they are one class.
 */
public final class DisplayConfig {

    /** Smallest window scale. One logical pixel per screen pixel. */
    public static final int MIN_SCALE = 1;
    /** Largest window scale. Past 3x the window stops fitting most screens. */
    public static final int MAX_SCALE = 3;
    /**
     * Scale used until the player picks one. The frame shrinks this to
     * whatever the desktop has room for, so it is an upper bound for the first
     * run rather than a promise.
     */
    public static final int DEFAULT_SCALE = 2;

    /** Scale is stored in the same node as the other settings. */
    private static final String SCALE_KEY = "displayScale";
    /** Fullscreen flag is stored in the same node as the other settings. */
    private static final String FULLSCREEN_KEY = "displayFullscreen";

    private static final Preferences PREFS =
            Preferences.userNodeForPackage(DisplayConfig.class);

    /** Scale the last presented frame was drawn at. */
    private static volatile int viewportScale = MIN_SCALE;
    /** Left edge of the image within the content area, in screen pixels. */
    private static volatile int viewportX;
    /** Top edge of the image within the content area, in screen pixels. */
    private static volatile int viewportY;
    /** Width of the coordinate space the game draws in, 0 until drawn. */
    private static volatile int viewportWidth;
    /** Height of the coordinate space the game draws in, 0 until drawn. */
    private static volatile int viewportHeight;

    private DisplayConfig() {
    }

    /**
     * Window scale the player chose, clamped to a scale we can present.
     *
     * @return Scale between {@link #MIN_SCALE} and {@link #MAX_SCALE}.
     */
    public static int getScale() {
        return clampScale(PREFS.getInt(SCALE_KEY, DEFAULT_SCALE));
    }

    /**
     * Stores the window scale. Out-of-range values are clamped rather than
     * ignored, so a caller cannot leave the setting unchanged by accident.
     *
     * @param scale
     *              Scale to store.
     */
    public static void setScale(final int scale) {
        PREFS.putInt(SCALE_KEY, clampScale(scale));
    }

    /**
     * Next scale in the 1x to 2x to 3x cycle, wrapping back to 1x.
     *
     * @return Scale that follows the current one.
     */
    public static int nextScale() {
        int next = getScale() + 1;
        return next > MAX_SCALE ? MIN_SCALE : next;
    }

    /**
     * @return Whether the game should fill the screen.
     */
    public static boolean isFullscreen() {
        return PREFS.getBoolean(FULLSCREEN_KEY, false);
    }

    /**
     * Stores whether the game should fill the screen.
     *
     * @param fullscreen
     *                   Whether to fill the screen.
     */
    public static void setFullscreen(final boolean fullscreen) {
        PREFS.putBoolean(FULLSCREEN_KEY, fullscreen);
    }

    private static int clampScale(final int scale) {
        if (scale < MIN_SCALE)
            return MIN_SCALE;
        if (scale > MAX_SCALE)
            return MAX_SCALE;
        return scale;
    }

    /**
     * Records where the image was drawn, for the pointer to be read back
     * against.
     *
     * The offsets are measured from the corner of the content area, not of the
     * window, because that is where pointer positions are measured from. The
     * shake offset is deliberately not included: it moves the image only, and
     * folding it in here would make every menu's hover jitter along with it.
     *
     * @param scale
     *                      Whole-number scale the image was drawn at.
     * @param originX
     *                      Left edge of the image within the content area.
     * @param originY
     *                      Top edge of the image within the content area.
     * @param logicalWidth
     *                      Width of the coordinate space the game draws in.
     * @param logicalHeight
     *                      Height of the coordinate space the game draws in.
     */
    static void setViewport(final int scale, final int originX,
            final int originY, final int logicalWidth,
            final int logicalHeight) {
        viewportScale = Math.max(MIN_SCALE, scale);
        viewportX = originX;
        viewportY = originY;
        viewportWidth = logicalWidth;
        viewportHeight = logicalHeight;
    }

    /**
     * @return Whole-number scale the last presented frame was drawn at.
     */
    public static int getViewportScale() {
        return viewportScale;
    }

    /**
     * Turns a horizontal pointer position within the content area into the
     * coordinate the game drew at.
     *
     * @param screenX
     *                Position within the content area.
     * @return Logical x, or -1 when the pointer is off the image, which is the
     *         same thing the pointer leaving the window reports.
     */
    public static int toLogicalX(final int screenX) {
        return toLogical(screenX, viewportX, viewportWidth);
    }

    /**
     * Turns a vertical pointer position within the content area into the
     * coordinate the game drew at.
     *
     * @param screenY
     *                Position within the content area.
     * @return Logical y, or -1 when the pointer is off the image.
     */
    public static int toLogicalY(final int screenY) {
        return toLogical(screenY, viewportY, viewportHeight);
    }

    private static int toLogical(final int position, final int origin,
            final int extent) {
        // Nothing has been presented yet, so there is no image to be off, and
        // no scaling to undo either.
        if (extent <= 0)
            return position;

        // Subtracting first, and before dividing: a position one pixel into
        // the letterbox is off the image, but at 2x it would divide down to
        // the first logical pixel.
        int offset = position - origin;
        if (offset < 0)
            return -1;

        int logical = offset / viewportScale;
        return logical < extent ? logical : -1;
    }
}
