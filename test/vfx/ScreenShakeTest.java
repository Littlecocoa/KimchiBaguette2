package vfx;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScreenShakeTest {

    /** Enough samples to hit both signs and the extremes of the random range. */
    private static final int SAMPLES = 2000;

    @BeforeEach
    void startStill() {
        ScreenShake.reset();
        ScreenShake.setIntensity(1.0);
    }

    @Test
    void noTraumaMeansNoOffset() {
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
    void offsetNeverExceedsMaxOffset() {
        ScreenShake.add(1.0);
        boolean moved = false;
        for (int i = 0; i < SAMPLES; i++) {
            int x = ScreenShake.getOffsetX();
            int y = ScreenShake.getOffsetY();
            assertTrue(Math.abs(x) <= ScreenShake.MAX_OFFSET, "x = " + x);
            assertTrue(Math.abs(y) <= ScreenShake.MAX_OFFSET, "y = " + y);
            moved |= x != 0 || y != 0;
        }
        assertTrue(moved, "full trauma should actually move the frame");
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
    void smallTraumaStaysSubtle() {
        // trauma 0.3 (a grenade) gives 6 * 0.3 * 0.3 = 0.54, under 1 pixel.
        // Without the square it would be 1.8 and the frame would visibly move.
        ScreenShake.add(0.3);
        for (int i = 0; i < SAMPLES; i++) {
            assertEquals(0, ScreenShake.getOffsetX());
            assertEquals(0, ScreenShake.getOffsetY());
        }
    }

    @Test
    void intensityZeroAlwaysGivesZeroOffset() {
        ScreenShake.setIntensity(0.0);
        ScreenShake.add(1.0);
        for (int i = 0; i < SAMPLES; i++) {
            assertEquals(0, ScreenShake.getOffsetX());
            assertEquals(0, ScreenShake.getOffsetY());
        }
    }

    @Test
    void intensityScalesTheOffset() {
        // Low (0.5) halves the range: at most 3 pixels.
        ScreenShake.setIntensity(0.5);
        ScreenShake.add(1.0);
        for (int i = 0; i < SAMPLES; i++) {
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
