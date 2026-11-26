package engine;

import java.util.HashMap;
import java.util.Map;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.Preferences;
import java.util.prefs.PreferencesFactory;

/**
 * Checks the display settings and the pointer transform they imply.
 *
 * Run in a fresh JVM. Uses memory-only preferences, so it never reads or
 * writes the real display setting.
 */
public final class DisplayConfigTest {

	private DisplayConfigTest() {
	}

	public static void main(final String[] args) {
		System.setProperty("java.util.prefs.PreferencesFactory",
				MemoryPreferencesFactory.class.getName());
		check(Preferences.userRoot() instanceof MemoryNode,
				"Run in a fresh JVM to isolate preferences");

		checkScaleSetting();
		checkFullscreenSetting();
		checkPointerBeforeFirstFrame();
		checkPointerAtEveryScale();
		checkPointerOffTheImage();
		checkPointerOnAFilledImage();
		checkWhenFillingIsWorthIt();

		System.out.println("Display config checks passed.");
	}

	/** The stored scale survives a round trip and stays in range. */
	private static void checkScaleSetting() {
		check(DisplayConfig.getScale() == DisplayConfig.DEFAULT_SCALE,
				"Unset scale should fall back to the default");

		for (int scale = DisplayConfig.MIN_SCALE;
				scale <= DisplayConfig.MAX_SCALE; scale++) {
			DisplayConfig.setScale(scale);
			check(DisplayConfig.getScale() == scale,
					"Scale " + scale + " did not survive a round trip");
		}

		// Out of range is clamped rather than dropped, so that a caller cannot
		// leave the setting unchanged by accident.
		DisplayConfig.setScale(DisplayConfig.MAX_SCALE + 5);
		check(DisplayConfig.getScale() == DisplayConfig.MAX_SCALE,
				"Too large a scale should clamp to the largest");
		DisplayConfig.setScale(0);
		check(DisplayConfig.getScale() == DisplayConfig.MIN_SCALE,
				"Too small a scale should clamp to the smallest");
		DisplayConfig.setScale(-3);
		check(DisplayConfig.getScale() == DisplayConfig.MIN_SCALE,
				"A negative scale should clamp to the smallest");

		// The cycle covers every scale and comes back round.
		DisplayConfig.setScale(DisplayConfig.MIN_SCALE);
		for (int step = DisplayConfig.MIN_SCALE;
				step < DisplayConfig.MAX_SCALE; step++) {
			check(DisplayConfig.nextScale() == step + 1,
					"The scale after " + step + " should be " + (step + 1));
			DisplayConfig.setScale(DisplayConfig.nextScale());
		}
		check(DisplayConfig.getScale() == DisplayConfig.MAX_SCALE,
				"Stepping through the cycle should reach the largest scale");
		check(DisplayConfig.nextScale() == DisplayConfig.MIN_SCALE,
				"The cycle should wrap back to the smallest scale");
	}

	private static void checkFullscreenSetting() {
		check(!DisplayConfig.isFullscreen(),
				"The game should start in a window");
		DisplayConfig.setFullscreen(true);
		check(DisplayConfig.isFullscreen(),
				"Fullscreen did not survive a round trip");
		DisplayConfig.setFullscreen(false);
		check(!DisplayConfig.isFullscreen(),
				"Leaving fullscreen did not survive a round trip");
	}

	/**
	 * Nothing has been presented yet, so there is no image for the pointer to
	 * be on or off, and the position passes through untouched.
	 */
	private static void checkPointerBeforeFirstFrame() {
		check(DisplayConfig.toLogicalX(17) == 17,
				"A position should pass through before the first frame");
		check(DisplayConfig.toLogicalY(23) == 23,
				"A position should pass through before the first frame");
		check(DisplayConfig.toLogicalX(-1) == -1,
				"A pointer outside the window stays outside");
	}

	/**
	 * Every pointer position on the image maps back to the logical pixel that
	 * was drawn there, at every scale and with the image off-centre. This is
	 * the menu hover check: if this drifts, every mouse-driven menu drifts
	 * with it.
	 */
	private static void checkPointerAtEveryScale() {
		int logicalWidth = 448;
		int logicalHeight = 520;

		for (int scale = DisplayConfig.MIN_SCALE;
				scale <= DisplayConfig.MAX_SCALE; scale++) {
			// A letterbox on both axes, of a size that is not a multiple of
			// the scale, so an off-by-one in the order of operations shows.
			int marginX = 37;
			int marginY = 11;
			DisplayConfig.setViewport(marginX, marginY, logicalWidth * scale,
					logicalHeight * scale, logicalWidth, logicalHeight);
			check(DisplayConfig.getViewportScale() == scale,
					"The viewport scale should be the one presented");

			for (int logicalX = 0; logicalX < logicalWidth; logicalX++)
				// Every screen pixel the logical one covers has to answer with
				// that logical pixel, not just its first corner.
				for (int inside = 0; inside < scale; inside++) {
					int screenX = marginX + logicalX * scale + inside;
					check(DisplayConfig.toLogicalX(screenX) == logicalX,
							"At " + scale + "x, screen x " + screenX
									+ " should be logical " + logicalX);
				}

			for (int logicalY = 0; logicalY < logicalHeight; logicalY++)
				for (int inside = 0; inside < scale; inside++) {
					int screenY = marginY + logicalY * scale + inside;
					check(DisplayConfig.toLogicalY(screenY) == logicalY,
							"At " + scale + "x, screen y " + screenY
									+ " should be logical " + logicalY);
				}
		}
	}

	/**
	 * A pointer in the letterbox is on no logical pixel, and says so the same
	 * way a pointer that left the window does.
	 */
	private static void checkPointerOffTheImage() {
		int logicalWidth = 448;
		int logicalHeight = 520;
		int scale = 2;
		int marginX = 37;
		int marginY = 11;
		DisplayConfig.setViewport(marginX, marginY, logicalWidth * scale,
				logicalHeight * scale, logicalWidth, logicalHeight);

		check(DisplayConfig.toLogicalX(marginX - 1) == -1,
				"One pixel into the left letterbox is off the image");
		check(DisplayConfig.toLogicalX(0) == -1,
				"The left edge of the content area is off the image");
		check(DisplayConfig.toLogicalY(marginY - 1) == -1,
				"One pixel into the top letterbox is off the image");
		check(DisplayConfig.toLogicalX(marginX + logicalWidth * scale) == -1,
				"One pixel past the right edge is off the image");
		check(DisplayConfig.toLogicalY(marginY + logicalHeight * scale) == -1,
				"One pixel past the bottom edge is off the image");

		// The last logical pixel is still on the image.
		check(DisplayConfig.toLogicalX(marginX + logicalWidth * scale - 1)
				== logicalWidth - 1,
				"The last column should be the last logical pixel");
		check(DisplayConfig.toLogicalY(marginY + logicalHeight * scale - 1)
				== logicalHeight - 1,
				"The last row should be the last logical pixel");
	}

	/**
	 * A screen too short for the next whole scale is filled instead, so the
	 * image is not a whole multiple of the game. Every pointer position on it
	 * still has to land on a logical pixel, and every logical pixel has to be
	 * reachable, or parts of a menu would be unclickable.
	 */
	private static void checkPointerOnAFilledImage() {
		int logicalWidth = 448;
		int logicalHeight = 520;
		// 448x520 filling a 1512x982 screen: 1.888 times, not 1 or 2.
		int imageWidth = 846;
		int imageHeight = 982;
		int marginX = (1512 - imageWidth) / 2;
		int marginY = 0;
		DisplayConfig.setViewport(marginX, marginY, imageWidth, imageHeight,
				logicalWidth, logicalHeight);

		check(DisplayConfig.getViewportScale() == 1,
				"A filled image reports the whole scale it was filled past");

		boolean[] reached = new boolean[logicalWidth];
		for (int screenX = marginX; screenX < marginX + imageWidth; screenX++) {
			int logical = DisplayConfig.toLogicalX(screenX);
			check(logical >= 0 && logical < logicalWidth,
					"Screen x " + screenX + " is on the image, so it has a"
							+ " logical pixel, not " + logical);
			reached[logical] = true;
		}
		for (int logicalX = 0; logicalX < logicalWidth; logicalX++)
			check(reached[logicalX],
					"No pointer position maps to logical x " + logicalX);

		boolean[] reachedDown = new boolean[logicalHeight];
		for (int screenY = marginY; screenY < marginY + imageHeight;
				screenY++) {
			int logical = DisplayConfig.toLogicalY(screenY);
			check(logical >= 0 && logical < logicalHeight,
					"Screen y " + screenY + " is on the image, so it has a"
							+ " logical pixel, not " + logical);
			reachedDown[logical] = true;
		}
		for (int logicalY = 0; logicalY < logicalHeight; logicalY++)
			check(reachedDown[logicalY],
					"No pointer position maps to logical y " + logicalY);

		// The edges are still the edges.
		check(DisplayConfig.toLogicalX(marginX) == 0,
				"The first column of a filled image is the first pixel");
		check(DisplayConfig.toLogicalX(marginX + imageWidth - 1)
				== logicalWidth - 1,
				"The last column of a filled image is the last pixel");
		check(DisplayConfig.toLogicalX(marginX - 1) == -1,
				"The letterbox beside a filled image is off it");
		check(DisplayConfig.toLogicalX(marginX + imageWidth) == -1,
				"One pixel past a filled image is off it");
	}

	/**
	 * Filling costs the even pixel grid, so it is only worth it when the
	 * whole-number scale would leave a lot of the screen black.
	 */
	private static void checkWhenFillingIsWorthIt() {
		// The screens this was written for: short of the next whole scale.
		checkFills(1512, 982, true);
		checkFills(1366, 768, true);
		checkFills(2560, 1440, true);

		// Room to spare, so the pixels stay exact.
		checkFills(1920, 1080, false);
		checkFills(3840, 2160, false);
		// An exact multiple is never worth filling.
		checkFills(448, 520, false);
		checkFills(896, 1040, false);
		checkFills(1344, 1560, false);

		// Smaller than the game: cropped as before rather than shrunk.
		checkFills(300, 300, false);
		checkFills(0, 0, false);
	}

	private static void checkFills(final int areaWidth, final int areaHeight,
			final boolean expected) {
		int scale = DrawManager.integerScale(areaWidth, areaHeight, 448, 520);
		boolean fills = DrawManager.fillsBetter(areaWidth, areaHeight, 448,
				520, scale);
		check(fills == expected, areaWidth + "x" + areaHeight + " at " + scale
				+ "x should " + (expected ? "" : "not ") + "be filled");
	}

	private static void check(final boolean condition, final String message) {
		if (!condition)
			throw new AssertionError(message);
	}

	/** Settings for this test only; never touches the user's store. */
	public static final class MemoryPreferencesFactory
			implements PreferencesFactory {
		private final Preferences user = new MemoryNode(null, "");
		private final Preferences system = new MemoryNode(null, "");

		@Override
		public Preferences userRoot() {
			return user;
		}

		@Override
		public Preferences systemRoot() {
			return system;
		}
	}

	private static final class MemoryNode extends AbstractPreferences {
		private final Map<String, String> values =
				new HashMap<String, String>();
		private final Map<String, MemoryNode> children =
				new HashMap<String, MemoryNode>();

		MemoryNode(final MemoryNode parent, final String name) {
			super(parent, name);
		}

		@Override
		protected void putSpi(final String key, final String value) {
			values.put(key, value);
		}

		@Override
		protected String getSpi(final String key) {
			return values.get(key);
		}

		@Override
		protected void removeSpi(final String key) {
			values.remove(key);
		}

		@Override
		protected String[] keysSpi() {
			return values.keySet().toArray(new String[0]);
		}

		@Override
		protected String[] childrenNamesSpi() {
			return children.keySet().toArray(new String[0]);
		}

		@Override
		protected AbstractPreferences childSpi(final String name) {
			return children.computeIfAbsent(name,
					key -> new MemoryNode(this, key));
		}

		@Override
		protected void removeNodeSpi() {
			if (parent() != null)
				((MemoryNode) parent()).children.remove(name());
			values.clear();
			children.clear();
		}

		@Override
		protected void syncSpi() {
		}

		@Override
		protected void flushSpi() {
		}
	}
}
