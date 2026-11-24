package vfx;

/**
 * Screen shake offsets, applied by {@code DrawManager} when it blits the back
 * buffer. The offset is render-only: it never touches gameplay coordinates, so
 * collision detection is unaffected.
 *
 * Contract only. The trauma model lives behind these methods and is filled in
 * by the screen shake engine task (KAN-25, KAN-26); every method here is a
 * no-op, so the game draws exactly as it did before.
 *
 * State is static because the call sites are spread across GameScreen and
 * InfiniteGameScreen and one shake per screen is enough.
 */
public final class ScreenShake {

	private ScreenShake() {
	}

	/**
	 * Adds trauma, increasing the shake.
	 *
	 * @param trauma
	 *            Amount to add, 0.0 to 1.0.
	 */
	public static void add(final double trauma) {
	}

	/** Decays the shake. Call once per frame from the game screen's update. */
	public static void update() {
	}

	/**
	 * @return Horizontal render offset for this frame, in pixels.
	 */
	public static int getOffsetX() {
		return 0;
	}

	/**
	 * @return Vertical render offset for this frame, in pixels.
	 */
	public static int getOffsetY() {
		return 0;
	}

	/** Stops the shake immediately, for a pause or a screen change. */
	public static void reset() {
	}

	/**
	 * Sets the accessibility multiplier.
	 *
	 * @param intensity
	 *            Multiplier, 0.0 (off) to 1.0 (full).
	 */
	public static void setIntensity(final double intensity) {
	}
}
