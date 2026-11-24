package engine;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameClockTest {

	/** Long enough for the wall clock to move noticeably. */
	private static final int WAIT = 50;

	@BeforeEach
	void startFresh() {
		GameClock.reset();
	}

	@AfterEach
	void leaveRunning() {
		GameClock.reset();
	}

	@Test
	void advancesWhileRunning() throws InterruptedException {
		long before = GameClock.now();
		Thread.sleep(WAIT);
		assertTrue(GameClock.now() > before);
	}

	@Test
	void doesNotAdvanceWhilePaused() throws InterruptedException {
		GameClock.pause();
		long frozen = GameClock.now();
		Thread.sleep(WAIT);
		assertEquals(frozen, GameClock.now());
	}

	@Test
	void resumeLeavesPausedTimeOut() throws InterruptedException {
		GameClock.pause();
		long frozen = GameClock.now();
		Thread.sleep(WAIT);
		GameClock.resume();
		assertTrue(GameClock.now() - frozen < WAIT);
	}

	@Test
	void pauseTwiceAndResumeWithoutPauseAreHarmless()
			throws InterruptedException {
		GameClock.resume();
		GameClock.pause();
		long frozen = GameClock.now();
		Thread.sleep(WAIT);
		GameClock.pause();
		assertEquals(frozen, GameClock.now());
	}

	@Test
	void resetClearsPausedTime() throws InterruptedException {
		GameClock.pause();
		Thread.sleep(WAIT);
		GameClock.reset();
		long wall = System.currentTimeMillis();
		assertTrue(Math.abs(GameClock.now() - wall) < WAIT);
	}

	@Test
	void gameplayCooldownDoesNotFinishDuringPause()
			throws InterruptedException {
		Cooldown cooldown = new Cooldown(WAIT);
		cooldown.reset();
		GameClock.pause();
		Thread.sleep(WAIT * 2);
		assertFalse(cooldown.checkFinished());
	}

	@Test
	void menuCooldownKeepsRunningDuringPause() throws InterruptedException {
		Cooldown cooldown = Core.getMenuCooldown(WAIT);
		cooldown.reset();
		GameClock.pause();
		Thread.sleep(WAIT * 2);
		assertTrue(cooldown.checkFinished());
	}
}
