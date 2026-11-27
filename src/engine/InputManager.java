package engine;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

/**
 * Manages keyboard input for the provided screen.
 *
 * @author <a href="mailto:RobertoIA1987@gmail.com">Roberto Izquierdo Amo</a>
 *
 */
public final class InputManager implements KeyListener, MouseListener, MouseMotionListener {

	/** Number of recognised keys. */
	private static final int NUM_KEYS = 256;
	/** Array with the jeys marked as pressed or not. */
	private static boolean[] keys;

	private volatile int lastPressedKey = KeyEvent.VK_UNDEFINED;
	/** Singleton instance of the class. */
	private static InputManager instance;

	/**
	 * Mouse position relative to the window content, before the scale the
	 * image is drawn at is undone.
	 */
	private volatile int screenMouseX = -1;
	private volatile int screenMouseY = -1;

	/** Left mouse button state. */
	private volatile boolean mousePressed = false;

	/**
	 * Private constructor.
	 */
	private InputManager() {
		keys = new boolean[NUM_KEYS];
	}

	/**
	 * Returns shared instance of InputManager.
	 *
	 * @return Shared instance of InputManager.
	 */
	protected static InputManager getInstance() {
		if (instance == null)
			instance = new InputManager();
		return instance;
	}

	/**
	 * Returns true if the provided key is currently pressed.
	 *
	 * @param keyCode
	 *                Key number to check.
	 * @return Key state.
	 */
	public boolean isKeyDown(final int keyCode) {
		return keys[keyCode];
	}

	public int consumeLastPressedKey() {
		int keyCode = lastPressedKey;
		lastPressedKey = KeyEvent.VK_UNDEFINED;
		return keyCode;
	}

	public void clearLastPressedKey() {
		lastPressedKey = KeyEvent.VK_UNDEFINED;
	}

	/**
	 * Changes the state of the key to pressed.
	 *
	 * @param key
	 *            Key pressed.
	 */
	@Override
	public void keyPressed(final KeyEvent key) {
		if (key.getKeyCode() >= 0 && key.getKeyCode() < NUM_KEYS) {
			if (!keys[key.getKeyCode()]) {
				lastPressedKey = key.getKeyCode();
			}
			keys[key.getKeyCode()] = true;
		}
	}

	/**
	 * Changes the state of the key to not pressed.
	 *
	 * @param key
	 *            Key released.
	 */
	@Override
	public void keyReleased(final KeyEvent key) {
		if (key.getKeyCode() >= 0 && key.getKeyCode() < NUM_KEYS)
			keys[key.getKeyCode()] = false;
	}

	/**
	 * Does nothing.
	 *
	 * @param key
	 *            Key typed.
	 */
	@Override
	public void keyTyped(final KeyEvent key) {

	}

	/**
	 * Mouse position in the coordinates the game draws in.
	 *
	 * Where the pointer is in the window is not where it is in the game once
	 * the image is scaled and centred, so the viewport is undone here. Doing
	 * it when the position is read rather than when the event arrives keeps it
	 * right across a scale change, which happens without the mouse moving.
	 *
	 * @return Logical x, or -1 when the pointer is not on the game image.
	 */
	public int getMouseX() {
		return DisplayConfig.toLogicalX(screenMouseX);
	}

	/**
	 * Mouse position in the coordinates the game draws in.
	 *
	 * @return Logical y, or -1 when the pointer is not on the game image.
	 */
	public int getMouseY() {
		return DisplayConfig.toLogicalY(screenMouseY);
	}

	public boolean isMousePressed() {
		return mousePressed;
	}

	@Override
	public void mousePressed(MouseEvent e) {
		if (e.getButton() == MouseEvent.BUTTON1) {
			mousePressed = true;
		}
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		if (e.getButton() == MouseEvent.BUTTON1) {
			mousePressed = false;
		}
	}

	@Override
	public void mouseMoved(MouseEvent e) {
		screenMouseX = e.getX();
		screenMouseY = e.getY();
	}

	@Override
	public void mouseDragged(MouseEvent e) {
		screenMouseX = e.getX();
		screenMouseY = e.getY();
	}

	@Override
	public void mouseExited(MouseEvent e) {
		screenMouseX = -1;
		screenMouseY = -1;
	}

	@Override
	public void mouseEntered(MouseEvent e) {
		screenMouseX = e.getX();
		screenMouseY = e.getY();
	}

	@Override
	public void mouseClicked(MouseEvent e) {
	}
}
