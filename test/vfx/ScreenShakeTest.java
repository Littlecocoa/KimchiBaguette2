package vfx;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScreenShakeTest {

    /** Enough frames to hit both signs and the extremes of the random range. */
    private static final int SAMPLES = 2000;

    @BeforeEach
    void startStill() {
        ScreenShake.reset();
        ScreenShake.setIntensity(1.0);
    }

    /** Starts a fresh frame with exactly the given trauma. */
    private static void frameAt(final double trauma) {
        ScreenShake.reset();
        ScreenShake.update();
        ScreenShake.add(trauma);
    }

    @Test
    void noTraumaMeansNoOffset() {
        ScreenShake.update();
        assertEquals(0.0, ScreenShake.getTrauma());
        assertEquals(0, ScreenShake.getOffsetX());
        assertEquals(0, ScreenShake.getOffsetY());
    }

    @Test
    void fullTraumaSettlesToZeroWithinSixtyFrames() {
        ScreenShake.add(1.0);
        for (int frame = 0; frame < 60; frame++)
            ScreenShake.update();
        assertEquals(0.0, ScreenShake.getTrauma());
        assertEquals(0, ScreenShake.getOffsetX());
        assertEquals(0, ScreenShake.getOffsetY());
    }

    @Test
    void traumaDecaysEachFrame() {
        ScreenShake.add(1.0);
        ScreenShake.update();
        assertEquals(0.9, ScreenShake.getTrauma(), 1e-9);
        ScreenShake.update();
        assertEquals(0.81, ScreenShake.getTrauma(), 1e-9);
    }

    @Test
    void offsetStaysWithinMaxOffsetAndCanReachIt() {
        int largest = 0;
        for (int i = 0; i < SAMPLES; i++) {
            frameAt(1.0);
            int x = ScreenShake.getOffsetX();
            int y = ScreenShake.getOffsetY();
            assertTrue(Math.abs(x) <= ScreenShake.MAX_OFFSET, "x = " + x);
            assertTrue(Math.abs(y) <= ScreenShake.MAX_OFFSET, "y = " + y);
            largest = Math.max(largest, Math.max(Math.abs(x), Math.abs(y)));
        }
        assertEquals(ScreenShake.MAX_OFFSET, largest,
                "full trauma should be able to reach MAX_OFFSET");
    }

    @Test
    void offsetIsStableWithinAFrame() {
        for (int i = 0; i < SAMPLES; i++) {
            frameAt(1.0);
            assertEquals(ScreenShake.getOffsetX(), ScreenShake.getOffsetX());
            assertEquals(ScreenShake.getOffsetY(), ScreenShake.getOffsetY());
        }
    }

    @Test
    void smallTraumaMovesTheFrameByAtMostOnePixel() {
        // trauma 0.3: 6 * 0.3 * 0.3 = 0.54, so rounding gives at most 1 pixel,
        // and does give 1 on some frames. Without the square it would reach 2.
        boolean moved = false;
        for (int i = 0; i < SAMPLES; i++) {
            frameAt(0.3);
            int x = ScreenShake.getOffsetX();
            int y = ScreenShake.getOffsetY();
            assertTrue(Math.abs(x) <= 1, "x = " + x);
            assertTrue(Math.abs(y) <= 1, "y = " + y);
            moved |= x != 0 || y != 0;
        }
        assertTrue(moved, "trauma 0.3 should still move the frame sometimes");
    }

    @Test
    void traumaIsCappedAtOne() {
        ScreenShake.add(0.8);
        ScreenShake.add(0.8);
        assertEquals(1.0, ScreenShake.getTrauma());
        ScreenShake.add(5.0);
        assertEquals(1.0, ScreenShake.getTrauma());
    }

    @Test
    void nonPositiveOrNaNTraumaIsIgnored() {
        ScreenShake.add(0.5);
        ScreenShake.add(-1.0);
        ScreenShake.add(0.0);
        ScreenShake.add(Double.NaN);
        assertEquals(0.5, ScreenShake.getTrauma());
    }

    @Test
    void intensityZeroAlwaysGivesZeroOffset() {
        ScreenShake.setIntensity(0.0);
        for (int i = 0; i < SAMPLES; i++) {
            frameAt(1.0);
            assertEquals(0, ScreenShake.getOffsetX());
            assertEquals(0, ScreenShake.getOffsetY());
        }
    }

    @Test
    void intensityScalesTheOffset() {
        // Low (0.5) halves the range: at most 3 pixels.
        ScreenShake.setIntensity(0.5);
        for (int i = 0; i < SAMPLES; i++) {
            frameAt(1.0);
            assertTrue(Math.abs(ScreenShake.getOffsetX()) <= 3);
            assertTrue(Math.abs(ScreenShake.getOffsetY()) <= 3);
        }
    }

    @Test
    void intensityIsClampedAndIgnoresNaN() {
        ScreenShake.setIntensity(2.0);
        assertEquals(1.0, ScreenShake.getIntensity());
        ScreenShake.setIntensity(-1.0);
        assertEquals(0.0, ScreenShake.getIntensity());
        ScreenShake.setIntensity(0.5);
        ScreenShake.setIntensity(Double.NaN);
        assertEquals(0.5, ScreenShake.getIntensity());
    }

    @Test
    void resetStopsTheShakeImmediately() {
        ScreenShake.add(1.0);
        ScreenShake.update();
        ScreenShake.reset();
        assertEquals(0.0, ScreenShake.getTrauma());
        assertEquals(0, ScreenShake.getOffsetX());
        assertEquals(0, ScreenShake.getOffsetY());
    }

    @Test
    void resetKeepsTheIntensitySetting() {
        ScreenShake.setIntensity(0.5);
        ScreenShake.add(1.0);
        ScreenShake.reset();
        assertEquals(0.5, ScreenShake.getIntensity());
    }
}
