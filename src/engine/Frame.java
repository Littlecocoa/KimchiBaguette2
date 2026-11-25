package engine;

import java.awt.Dimension;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferStrategy;

import javax.swing.JFrame;

import screen.Screen;

/**
 * Implements a frame to show screens on.
 *
 * The frame holds the game at a fixed logical size and makes the window a
 * whole-number multiple of it, so the window can grow without the game having
 * to know. F11 fills the screen, F10 steps through the scales.
 *
 * @author <a href="mailto:RobertoIA1987@gmail.com">Roberto Izquierdo Amo</a>
 *
 */
@SuppressWarnings("serial")
public class Frame extends JFrame {

	/** Frame width. */
	private int width;
	/** Frame height. */
	private int height;
	/** Screen currently shown. */
	private Screen currentScreen;
	/** Display change asked for by a key, applied between frames. */
	private volatile boolean displayChangePending;
	/** Scale the window is showing, or 0 before it is first shown. */
	private int appliedScale;
	/** Whether the window is currently filling the screen. */
	private boolean appliedFullscreen;

	/**
	 * Initializes the new frame.
	 *
	 * @param width
	 *               Frame width.
	 * @param height
	 *               Frame height.
	 */
	public Frame(final int width, final int height) {
		// The logical size, which is what the screens draw in. It no longer
		// depends on how thick the window decoration happens to be: the
		// content area is sized to it instead, so the game is the same size on
		// every platform and the window is what changes.
		this.width = width;
		this.height = height;

		setResizable(false);
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		setTitle("Invaders");

		addKeyListener(Core.getInputManager());
		// On the window rather than in a screen, so it works everywhere,
		// including in the middle of a level.
		addKeyListener(new DisplayKeys());
		getContentPane().addMouseListener(Core.getInputManager());
		getContentPane().addMouseMotionListener(Core.getInputManager());

		applyDisplaySettings();
	}

	/**
	 * Sets current screen.
	 *
	 * @param screen
	 *               Screen to show.
	 * @return Return code of the finished screen.
	 */
	public final int setScreen(final Screen screen) {
		currentScreen = screen;
		currentScreen.initialize();
		return currentScreen.run();
	}

	/**
	 * Getter for frame width.
	 *
	 * This is the logical width the game draws in, not the width of the
	 * window, which is this times the current scale. Code that needs the real
	 * one asks {@link #getSize()}.
	 *
	 * @return Frame width.
	 */
	public final int getWidth() {
		return this.width;
	}

	/**
	 * Getter for frame height.
	 *
	 * As with {@link #getWidth()}, this is the logical height rather than the
	 * height of the window.
	 *
	 * @return Frame height.
	 */

	public final int getHeight() {
		return this.height;
	}

	/**
	 * Applies a display change asked for by a key press, if one is waiting.
	 *
	 * Called between frames, from the thread that draws: resizing the window
	 * or dropping its decoration while a frame is being presented would pull
	 * the buffer strategy out from under it. The key press itself arrives on
	 * the event thread and only raises the flag.
	 */
	public final void applyPendingDisplayChange() {
		if (!displayChangePending)
			return;
		displayChangePending = false;
		applyDisplaySettings();
	}

	/**
	 * Puts the window into the state {@link DisplayConfig} asks for.
	 *
	 * Call this from the thread that draws, which is where a screen's update
	 * runs, and not from an event handler. The keys below go the long way
	 * round for exactly that reason.
	 */
	public final void applyDisplaySettings() {
		boolean fullscreen = DisplayConfig.isFullscreen();
		int scale = largestFitting(DisplayConfig.getScale());

		// Nothing on screen would change. Showing the window again anyway
		// would cost it the keyboard for no reason, and on a screen with no
		// room for a larger scale that is most presses of the scale key.
		if (appliedScale != 0 && fullscreen == appliedFullscreen
				&& (fullscreen || scale == appliedScale))
			return;

		// The buffers belong to the window as it is now. Dropping them before
		// it changes leaves DrawManager to build new ones for the new size.
		discardBufferStrategy();

		if (fullscreen)
			enterFullscreen();
		else
			scale = enterWindowed(scale);

		appliedFullscreen = fullscreen;
		appliedScale = scale;

		// Showing the window again does not necessarily bring the keyboard
		// back with it, and without it the next key press goes nowhere.
		toFront();
		requestFocus();
	}

	/**
	 * Shows the window at the given scale, or smaller if the desktop turns
	 * out not to have room for it.
	 *
	 * @param requestedScale
	 *                       Scale to show.
	 * @return Scale the window ended up at.
	 */
	private int enterWindowed(final int requestedScale) {
		GraphicsDevice device = device();
		if (device != null && device.getFullScreenWindow() == this)
			device.setFullScreenWindow(null);

		if (isUndecorated()) {
			// Decoration can only be changed while the window has no peer.
			dispose();
			setUndecorated(false);
		}

		int scale = requestedScale;
		setContentSize(scale);
		// The first time through there was no window yet, so the decoration
		// was not counted and the scale may have been one too generous. Now
		// it can be measured.
		int fitting = largestFitting(scale);
		if (fitting != scale) {
			scale = fitting;
			setContentSize(scale);
		}

		setLocationRelativeTo(null);
		setVisible(true);
		return scale;
	}

	/**
	 * Makes the content area exactly the logical size times the scale, so that
	 * whole logical pixels fill it with nothing left over.
	 *
	 * Sized by hand rather than with {@link #pack()}: pack keeps the preferred
	 * size it worked out the first time, so a window that is already up would
	 * never leave the scale it opened at.
	 *
	 * @param scale
	 *              Scale to size the content area for.
	 */
	private void setContentSize(final int scale) {
		Dimension content =
				new Dimension(this.width * scale, this.height * scale);
		getContentPane().setPreferredSize(content);

		if (!isDisplayable()) {
			// No window yet, so no decoration to measure. Packing asks the
			// platform for it, and works the once.
			pack();
			return;
		}

		Insets insets = getInsets();
		setSize(content.width + insets.left + insets.right,
				content.height + insets.top + insets.bottom);
		validate();
	}

	/**
	 * Largest scale up to the one asked for whose window the desktop has room
	 * for. A window wider or taller than the screen would have the game cut
	 * off at the edge, which is worse than showing it smaller.
	 *
	 * @param requestedScale
	 *                       Largest scale to consider.
	 * @return Scale to use, never below {@link DisplayConfig#MIN_SCALE}.
	 */
	private int largestFitting(final int requestedScale) {
		GraphicsEnvironment environment =
				GraphicsEnvironment.getLocalGraphicsEnvironment();
		if (environment.isHeadlessInstance())
			return requestedScale;

		Rectangle usable = environment.getMaximumWindowBounds();
		Insets insets = isDisplayable() ? getInsets() : new Insets(0, 0, 0, 0);
		int scale = requestedScale;
		while (scale > DisplayConfig.MIN_SCALE
				&& (this.width * scale + insets.left + insets.right
						> usable.width
				|| this.height * scale + insets.top + insets.bottom
						> usable.height))
			scale--;
		return scale;
	}

	/**
	 * Fills the screen, keeping the game's aspect ratio. The image is scaled
	 * and centred by DrawManager, so whatever is left over stays black.
	 */
	private void enterFullscreen() {
		GraphicsDevice device = device();
		if (device == null) {
			enterWindowed(largestFitting(DisplayConfig.getScale()));
			return;
		}

		if (!isUndecorated()) {
			dispose();
			setUndecorated(true);
		}

		// Exclusive fullscreen puts the window over the dock and the menu bar
		// where the platform allows it.
		device.setFullScreenWindow(this);
		if (device.getFullScreenWindow() != this) {
			// Refused, so cover the screen with a borderless window instead.
			Rectangle bounds = device.getDefaultConfiguration().getBounds();
			setBounds(bounds);
			setVisible(true);
		}
	}

	/**
	 * Releases the buffers tied to the window in its current shape.
	 */
	private void discardBufferStrategy() {
		BufferStrategy strategy = getBufferStrategy();
		if (strategy != null)
			strategy.dispose();
	}

	/**
	 * @return Device the window is on, or {@code null} with no display at all.
	 */
	private GraphicsDevice device() {
		if (getGraphicsConfiguration() != null)
			return getGraphicsConfiguration().getDevice();

		GraphicsEnvironment environment =
				GraphicsEnvironment.getLocalGraphicsEnvironment();
		if (environment.isHeadlessInstance())
			return null;
		return environment.getDefaultScreenDevice();
	}

	/**
	 * Display keys, which work on every screen because they sit on the window
	 * rather than in a screen's update.
	 */
	private final class DisplayKeys extends KeyAdapter {

		@Override
		public void keyPressed(final KeyEvent event) {
			switch (event.getKeyCode()) {
			case KeyEvent.VK_F11:
				DisplayConfig.setFullscreen(!DisplayConfig.isFullscreen());
				displayChangePending = true;
				break;
			case KeyEvent.VK_F10:
				// Stepping the scale leaves fullscreen, so that the change is
				// something you can see rather than one stored for later.
				DisplayConfig.setScale(DisplayConfig.nextScale());
				DisplayConfig.setFullscreen(false);
				displayChangePending = true;
				break;
			default:
				break;
			}
		}
	}
}
