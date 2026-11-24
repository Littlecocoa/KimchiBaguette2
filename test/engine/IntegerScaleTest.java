package engine;

/** Checks that the render scale stays a whole number and never disappears. */
public final class IntegerScaleTest {

	private IntegerScaleTest() {
	}

	private static void check(final boolean condition, final String message) {
		if (!condition)
			throw new AssertionError(message);
	}

	private static void checkScale(final int areaWidth, final int areaHeight,
			final int expected) {
		int actual = DrawManager.integerScale(areaWidth, areaHeight, 448, 492);
		check(actual == expected, "Expected scale " + expected + " in "
				+ areaWidth + "x" + areaHeight + ", got " + actual);
	}

	public static void main(final String[] args) {
		// The window as it ships: exactly one logical pixel per screen pixel.
		checkScale(448, 492, 1);

		// Whole multiples.
		checkScale(896, 984, 2);
		checkScale(1344, 1476, 3);

		// Anything between two multiples falls back to the lower one, and the
		// leftover space becomes the letterbox.
		checkScale(1000, 1000, 2);
		checkScale(895, 984, 1);
		checkScale(896, 983, 1);

		// The limiting side decides, so the aspect ratio is kept.
		checkScale(3840, 492, 1);
		checkScale(448, 2160, 1);

		// A window smaller than the game still draws, cropped rather than
		// scaled down, because a fractional scale would blur it.
		checkScale(200, 200, 1);
		checkScale(0, 0, 1);

		// A degenerate logical size must not divide by zero.
		check(DrawManager.integerScale(800, 600, 0, 0) == 1,
				"Zero logical size falls back to scale 1");
		check(DrawManager.integerScale(800, 600, -1, -1) == 1,
				"Negative logical size falls back to scale 1");

		System.out.println("Integer scale checks passed.");
	}
}
