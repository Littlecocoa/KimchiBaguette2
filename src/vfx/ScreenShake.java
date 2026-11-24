package vfx;

import java.util.Random;

/**
 * Screen shake offsets, applied by {@code DrawManager} when it blits the back
 * buffer. The offset is render-only: it never touches gameplay coordinates, so
 * collision detection is unaffected.
 *
 * Uses a trauma model. Events add trauma (0.0 to 1.0), trauma decays every
 * frame, and the offset scales with trauma squared, so a big hit shakes hard
 * and settles quickly while a small one stays subtle instead of trembling.
 *
 * State is static because the call sites are spread across GameScreen and
 * InfiniteGameScreen and one shake per screen is enough.
 */
public final class ScreenShake {

	/** Largest offset in pixels, about 1.3% of the 448-pixel-wide screen. */
	public static final int MAX_OFFSET = 6;
	/** Share of trauma kept after each frame. */
	private static final double DECAY = 0.90;
	/** Trauma below this snaps to 0, so the shake ends cleanly. */
	private static final double SNAP = 0.01;

	private static final Random RANDOM = new Random();

	/** Current trauma, 0.0 (still) to 1.0 (strongest). */
	private static double trauma = 0;
	/** Accessibility multiplier, 0.0 (off) to 1.0 (full). */
	private static double intensity = 1.0;
	/** This frame's random direction on each axis, -1.0 to 1.0. */
	private static double directionX = 0;
	private static double directionY = 0;

	private ScreenShake() {
	}

	/**
	 * Adds trauma, increasing the shake. The total is capped at 1.0. Values
	 * that are not positive, including NaN, are ignored.
	 *
	 * @param amount
	 *            Amount to add, 0.0 to 1.0.
	 */
	public static void add(final double amount) {
		if (!(amount > 0))
			return;
		trauma = Math.min(1.0, trauma + amount);
	}

	/**
	 * Decays the shake and picks this frame's direction. Call once per frame
	 * from the game screen's update, so that every read of the offset within
	 * a frame agrees.
	 */
	public static void update() {
		trauma *= DECAY;
		if (trauma < SNAP)
			trauma = 0;
		directionX = RANDOM.nextDouble() * 2 - 1;
		directionY = RANDOM.nextDouble() * 2 - 1;
	}

	/**
	 * @return Horizontal render offset for this frame, in pixels. Repeated
	 *         calls return the same value until the next {@link #update()}.
	 */
	public static int getOffsetX() {
		return offset(directionX);
	}

	/**
	 * @return Vertical render offset for this frame, in pixels. Repeated calls
	 *         return the same value until the next {@link #update()}.
	 */
	public static int getOffsetY() {
		return offset(directionY);
	}

	/** Stops the shake immediately, for a pause or a screen change. */
	public static void reset() {
		trauma = 0;
	}

	/**
	 * Sets the accessibility multiplier. 0.0 turns the shake off. Values
	 * outside 0.0 to 1.0 are clamped, and NaN is ignored.
	 *
	 * @param value
	 *            Multiplier, 0.0 (off) to 1.0 (full).
	 */
	public static void setIntensity(final double value) {
		if (Double.isNaN(value))
			return;
		intensity = Math.max(0.0, Math.min(1.0, value));
	}

	/**
	 * @return Current trauma, for tests and debugging.
	 */
	public static double getTrauma() {
		return trauma;
	}

	/**
	 * @return Current accessibility multiplier.
	 */
	public static double getIntensity() {
		return intensity;
	}

	/**
	 * Offset in [-MAX_OFFSET, MAX_OFFSET] for one axis, scaled by trauma².
	 * Rounded rather than truncated, so small trauma still moves the frame by
	 * a pixel and full trauma can reach MAX_OFFSET.
	 */
	private static int offset(final double direction) {
		if (trauma == 0 || intensity == 0)
			return 0;
		return (int) Math.round(
				MAX_OFFSET * trauma * trauma * intensity * direction);
	}
}
