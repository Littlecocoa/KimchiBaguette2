package screen;

import java.awt.event.KeyEvent;

import audio.AudioManager;
import engine.Cooldown;
import engine.Core;
import engine.GameplayConfig;

public class GameplaySettingsScreen extends Screen {
    private static final int DIFFICULTY_OPTION = 0;
    private static final int SCREEN_SHAKE_OPTION = 1;
    private static final int OPTION_COUNT = 2;
    private static final int VALUE_COUNT = 3;
    private static final int CONFIRM_OPTION = OPTION_COUNT * VALUE_COUNT;
    private static final int SELECTION_TIME = 200;

    private static final double[] SCREEN_SHAKE_VALUES = {
            GameplayConfig.SCREEN_SHAKE_OFF,
            GameplayConfig.SCREEN_SHAKE_LOW,
            GameplayConfig.SCREEN_SHAKE_NORMAL
    };

    private final Cooldown selectionCooldown;
    private int selectedOption;
    private int selectedDifficulty;
    private int selectedScreenShake;
    /** Option under the mouse on the last frame, -1 if none. */
    private int lastHoveredOption = -1;
    /** Starts true so a click held from the previous screen is ignored. */
    private boolean wasMousePressed = true;

    public GameplaySettingsScreen(final int width, final int height,
            final int fps) {
        super(width, height, fps);
        this.returnCode = 4;
        this.selectedOption = DIFFICULTY_OPTION;
        this.selectedDifficulty = GameplayConfig.getDifficulty();
        this.selectedScreenShake = getScreenShakeOption(
                GameplayConfig.getScreenShakeIntensity());
        this.selectionCooldown = Core.getMenuCooldown(SELECTION_TIME);
        this.selectionCooldown.reset();
    }

    @Override
    public int run() {
        super.run();
        return this.returnCode;
    }

    @Override
    protected void update() {
        super.update();
        draw();

        int hoveredOption = drawManager.getGameplaySettingsOptionAt(this,
                selectedDifficulty, selectedScreenShake,
                inputManager.getMouseX(), inputManager.getMouseY());

        // Select a setting row when the mouse moves onto one of its values.
        if (hoveredOption != lastHoveredOption) {
            lastHoveredOption = hoveredOption;
            int hoveredSetting = getSettingIndex(hoveredOption);
            if (hoveredSetting != -1 && hoveredSetting != selectedOption) {
                selectedOption = hoveredSetting;
                AudioManager.playSFX("audio/sfx/menu_move.wav");
            }
        }

        boolean mousePressed = inputManager.isMousePressed();
        boolean newMouseClick = mousePressed && !wasMousePressed;
        wasMousePressed = mousePressed;

        if (newMouseClick && hoveredOption != -1
                && inputDelay.checkFinished()) {
            if (hoveredOption == CONFIRM_OPTION) {
                confirmSettings();
                return;
            }
            selectedOption = getSettingIndex(hoveredOption);
            setSelectedValue(selectedOption, hoveredOption % VALUE_COUNT);
            AudioManager.playSFX("audio/sfx/menu_move.wav");
            return;
        }

        if (!selectionCooldown.checkFinished()
                || !inputDelay.checkFinished()) {
            return;
        }

        if (inputManager.isKeyDown(KeyEvent.VK_UP)
                || inputManager.isKeyDown(KeyEvent.VK_W)) {
            selectedOption = (selectedOption - 1 + OPTION_COUNT)
                    % OPTION_COUNT;
            selectionCooldown.reset();
            AudioManager.playSFX("audio/sfx/menu_move.wav");
        } else if (inputManager.isKeyDown(KeyEvent.VK_DOWN)
                || inputManager.isKeyDown(KeyEvent.VK_S)) {
            selectedOption = (selectedOption + 1) % OPTION_COUNT;
            selectionCooldown.reset();
            AudioManager.playSFX("audio/sfx/menu_move.wav");
        } else if (inputManager.isKeyDown(KeyEvent.VK_LEFT)
                || inputManager.isKeyDown(KeyEvent.VK_A)) {
            changeSelectedValue(-1);
            selectionCooldown.reset();
            AudioManager.playSFX("audio/sfx/menu_move.wav");
        } else if (inputManager.isKeyDown(KeyEvent.VK_RIGHT)
                || inputManager.isKeyDown(KeyEvent.VK_D)) {
            changeSelectedValue(1);
            selectionCooldown.reset();
            AudioManager.playSFX("audio/sfx/menu_move.wav");
        } else if (inputManager.isKeyDown(KeyEvent.VK_SPACE)) {
            confirmSettings();
        } else if (inputManager.isKeyDown(KeyEvent.VK_ESCAPE)) {
            AudioManager.playSFX("audio/sfx/menu_back.wav");
            returnCode = 4;
            isRunning = false;
        }
    }

    /**
     * Changes the value of the selected setting, wrapping at either end.
     */
    private void changeSelectedValue(final int amount) {
        int currentValue = selectedOption == DIFFICULTY_OPTION
                ? selectedDifficulty : selectedScreenShake;
        int newValue = (currentValue + amount + VALUE_COUNT) % VALUE_COUNT;
        setSelectedValue(selectedOption, newValue);
    }

    private void setSelectedValue(final int setting, final int value) {
        if (setting == DIFFICULTY_OPTION) {
            selectedDifficulty = value;
        } else if (setting == SCREEN_SHAKE_OPTION) {
            selectedScreenShake = value;
        }
    }

    private static int getSettingIndex(final int option) {
        return option < 0 || option >= CONFIRM_OPTION
                ? -1 : option / VALUE_COUNT;
    }

    private static int getScreenShakeOption(final double intensity) {
        for (int i = 0; i < SCREEN_SHAKE_VALUES.length; i++) {
            if (SCREEN_SHAKE_VALUES[i] == intensity) {
                return i;
            }
        }
        return SCREEN_SHAKE_VALUES.length - 1;
    }

    /**
     * Saves both gameplay settings and returns to the settings menu.
     */
    private void confirmSettings() {
        GameplayConfig.setDifficulty(selectedDifficulty);
        GameplayConfig.setScreenShakeIntensity(
                SCREEN_SHAKE_VALUES[selectedScreenShake]);
        AudioManager.playSFX("audio/sfx/menu_back.wav");
        returnCode = 4;
        isRunning = false;
    }

    private void draw() {
        drawManager.initDrawing(this);
        drawManager.drawGameplaySettings(this, selectedOption,
                selectedDifficulty, selectedScreenShake,
                lastHoveredOption == CONFIRM_OPTION);
        drawManager.completeDrawing(this);
    }
}
