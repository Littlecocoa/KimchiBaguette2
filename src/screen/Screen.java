
package screen;

import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import audio.AudioControls;
import audio.AudioManager;
import engine.ControlsConfig;
import engine.Cooldown;
import engine.Core;
import engine.DrawManager;
import engine.InputManager;

import java.awt.Graphics;
import engine.Starfield;

/**
 * Implements a generic screen.
 *
 * @author <a href="mailto:RobertoIA1987@gmail.com">Roberto Izquierdo Amo</a>
 *
 */
public class Screen {

	/** Milliseconds until the screen accepts user input. */
	private static final int INPUT_DELAY = 1000;

	/** Draw Manager instance. */
	protected DrawManager drawManager;
	/** Input Manager instance. */
	protected InputManager inputManager;
	/** Application logger. */
	protected Logger logger;

	/** Screen width. */
	protected int width;
	/** Screen height. */
	protected int height;

	private final Starfield starfield;

	/** Frames per second shown on the screen. */
	protected int fps;
	/** Screen insets. */
	protected Insets insets;
	/** Time until the screen accepts user input. */
	protected Cooldown inputDelay;

	/** If the screen is running. */
	protected boolean isRunning;
	/** What kind of screen goes next. */
	protected int returnCode;

	/** Checks if the mute key was held down in the previous frame. */
	private static boolean muteKeyWasDown;

	/** Checks if the pause key (ESC) was held down in the previous frame. */
	private boolean pauseKeyWasDown;
	/** Whether the screen is currently paused. */
	protected boolean paused;

	/** Number of options in the pause menu. */
	private static final int PAUSE_OPTION_COUNT = 3;
	/** Delay between pause menu selections. */
	private static final int PAUSE_SELECTION_TIME = 200;
	/** Currently selected pause menu option. */
	private int selectedPauseOption = 0;
	/** Pause menu option hovered on the previous frame. */
	private int lastHoveredPauseOption = -1;
	/** Previous state of the confirmation key. */
	private boolean pauseSpaceWasDown;
	/** Previous state of the mouse button. */
	private boolean pauseMouseWasDown;
	/** Navigation cooldown independent of the gameplay clock. */
	private Cooldown pauseSelectionCooldown;

	/**
	 * Constructor, establishes the properties of the screen.
	 *
	 * @param width
	 *            Screen width.
	 * @param height
	 *            Screen height.
	 * @param fps
	 *            Frames per second, frame rate at which the game is run.
	 */
	public Screen(final int width, final int height, final int fps) {
		this.width = width;
		this.height = height;
		this.starfield = new Starfield(width, height);
		this.fps = fps;

		this.drawManager = Core.getDrawManager();
		this.inputManager = Core.getInputManager();
		this.logger = Core.getLogger();
		this.inputDelay = Core.getMenuCooldown(INPUT_DELAY);
		this.inputDelay.reset();
		this.returnCode = 0;
		this.pauseSelectionCooldown =
				Core.getMenuCooldown(PAUSE_SELECTION_TIME);
		this.pauseSelectionCooldown.reset();
	}

	/**
	 * Initializes basic screen properties.
	 */
	public void initialize() {

	}

	/**
	 * Activates the screen.
	 *
	 * @return Next screen code.
	 */
	public int run() {
		this.isRunning = true;
		long previousTime = System.nanoTime();

		while (this.isRunning) {
			long time = System.currentTimeMillis();

			long currentTime = System.nanoTime();
			double deltaSeconds =
					(currentTime - previousTime) / 1_000_000_000.0;
			previousTime = currentTime;

			if (!this.paused) {
				this.starfield.update(deltaSeconds);
			}
			update();

			time = (1000 / this.fps) - (System.currentTimeMillis() - time);
			if (time > 0) {
				try {
					TimeUnit.MILLISECONDS.sleep(time);
				} catch (InterruptedException e) {
					return 0;
				}
			}
		}

		return 0;
	}

	/**
	 * Updates the elements on screen and checks for events.
	 */
	protected void update() {
		handleMuteKey();
	}

	/**
	 * Toggles the mute state when the M key is pressed.
	 * Called from update, so it works on every screen whose update calls
	 * super.update(). Holding the key toggles only once, and the key state
	 * is shared by all screens so a new screen does not toggle it again.
	 * The key state is still updated on screens where usesGlobalMuteKey
	 * returns false.
	 */
	private void handleMuteKey() {
		boolean muteKeyDown = inputManager.isKeyDown(KeyEvent.VK_M);
		if (muteKeyDown && !muteKeyWasDown && usesGlobalMuteKey()
				&& !isMuteKeyBoundElsewhere())
			AudioControls.toggleMute();
		muteKeyWasDown = muteKeyDown;
	}

	/**
	 * Checks whether the M key is also bound to movement or firing.
	 * The global mute key is turned off in that case, so the same key
	 * does not move or fire and toggle the mute state at once.
	 *
	 * @return True if M is used by another control.
	 */
	private static boolean isMuteKeyBoundElsewhere() {
		return ControlsConfig.getKey(ControlsConfig.LEFT) == KeyEvent.VK_M
				|| ControlsConfig.getKey(ControlsConfig.RIGHT) == KeyEvent.VK_M
				|| ControlsConfig.getKey(ControlsConfig.FIRE) == KeyEvent.VK_M;
	}

	/**
	 * Checks if the M key toggles the global mute on this screen.
	 * Screens that handle the M key themselves can return false.
	 *
	 * @return True if the M key toggles the global mute.
	 */
	protected boolean usesGlobalMuteKey() {
		return true;
	}

	/**
	 * Getter for screen width.
	 *
	 * @return Screen width.
	 */
	public final int getWidth() {
		return this.width;
	}

	/**
	 * Getter for screen height.
	 *
	 * @return Screen height.
	 */
	public final int getHeight() {
		return this.height;
	}

	public final void drawBackground(final Graphics graphics) {
		this.starfield.draw(graphics);
	}

	/**
	 * Checks whether the pause key (ESC) was just pressed on this frame.
	 * Edge-triggered: holding ESC returns true only once.
	 *
	 * @return True on the rising edge of ESC.
	 */
	protected final boolean isPauseKeyJustPressed() {
		boolean pauseKeyDown = inputManager.isKeyDown(KeyEvent.VK_ESCAPE);
		boolean justPressed = pauseKeyDown && !this.pauseKeyWasDown;
		this.pauseKeyWasDown = pauseKeyDown;
		return justPressed;
	}

	/**
	 * Toggles the pause state on or off.
	 */
	protected void togglePause() {
		this.paused = !this.paused;

		if (this.paused) {
			this.selectedPauseOption = 0;
			this.lastHoveredPauseOption = -1;
			this.pauseSelectionCooldown.reset();

			this.pauseSpaceWasDown =
					inputManager.isKeyDown(KeyEvent.VK_SPACE);

			this.pauseMouseWasDown =
					inputManager.isMousePressed();
		}
	}

	/**
	 * Returns whether the screen is currently paused.
	 *
	 * @return True if paused.
	 */
	public final boolean isPaused() {
		return this.paused;
	}

	/**
	 * Returns the currently selected pause menu option.
	 */
	public final int getSelectedPauseOption() {
		return this.selectedPauseOption;
	}

	/**
	 * Handles keyboard and mouse navigation in the pause menu.
	 *
	 * @return Confirmed option, or -1 if no option was confirmed.
	 */
	protected final int updatePauseMenu() {

		// Mouse navigation.
		int hoveredOption = drawManager.getPauseMenuOptionAt(
				this, inputManager.getMouseX(),
				inputManager.getMouseY());

		if (hoveredOption != this.lastHoveredPauseOption) {
			this.lastHoveredPauseOption = hoveredOption;

			if (hoveredOption != -1
					&& hoveredOption != this.selectedPauseOption) {

				this.selectedPauseOption = hoveredOption;
				AudioManager.playSFX("audio/sfx/menu_move.wav");
			}
		}

		// Detect a new mouse click.
		boolean mouseDown = inputManager.isMousePressed();
		boolean newClick = mouseDown && !this.pauseMouseWasDown;
		this.pauseMouseWasDown = mouseDown;

		// Detect a new Space press.
		boolean spaceDown = inputManager.isKeyDown(KeyEvent.VK_SPACE);
		boolean newSpacePress = spaceDown && !this.pauseSpaceWasDown;
		this.pauseSpaceWasDown = spaceDown;

		// Confirm with the mouse.
		if (newClick && hoveredOption != -1) {
			this.selectedPauseOption = hoveredOption;
			AudioManager.playSFX("audio/sfx/menu_select.wav");
			return this.selectedPauseOption;
		}

		// Confirm with Space.
		if (newSpacePress) {
			AudioManager.playSFX("audio/sfx/menu_select.wav");
			return this.selectedPauseOption;
		}

		// Keyboard navigation.
		if (!this.pauseSelectionCooldown.checkFinished()) {
			return -1;
		}

		if (inputManager.isKeyDown(KeyEvent.VK_UP)
				|| inputManager.isKeyDown(KeyEvent.VK_W)) {

			this.selectedPauseOption =
					(this.selectedPauseOption - 1
							+ PAUSE_OPTION_COUNT) % PAUSE_OPTION_COUNT;

			this.pauseSelectionCooldown.reset();
			AudioManager.playSFX("audio/sfx/menu_move.wav");

		} else if (inputManager.isKeyDown(KeyEvent.VK_DOWN)
				|| inputManager.isKeyDown(KeyEvent.VK_S)) {

			this.selectedPauseOption =
					(this.selectedPauseOption + 1)
							% PAUSE_OPTION_COUNT;

			this.pauseSelectionCooldown.reset();
			AudioManager.playSFX("audio/sfx/menu_move.wav");
		}

		return -1;
	}
}
