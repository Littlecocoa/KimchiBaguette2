package boss.dialogue;

import boss.BossEvent;
import boss.BossEventListener;
import boss.dialogue.BossDialogue.Trigger;
import engine.Cooldown;
import engine.Core;

/**
 * Manages boss dialogue timing, queueing and defeat priority.
 *
 * Filters boss events and displays dialogue for 2.5 seconds
 * with a 2-second gap between consecutive lines.
 *
 * Stores at most one pending dialogue and discards overflow.
 * Defeat dialogue immediately overrides the active dialogue
 * and clears the pending dialogue.
 */

public final class BossDialogueDirector implements BossEventListener {

    private static final int DISPLAY_DURATION = 2500; // How long to display a line, in milliseconds.
    private static final int DIALOGUE_INTERVAL = 2000; // How long to wait before showing the next line, in milliseconds.

    // Supplies the line table and boss type to trigger mapping.
    private final BossDialogue dialogue = new BossDialogue();

    // Supplies event filtering and phase triggers.
    private final BossDialogueEventFilter filter = new BossDialogueEventFilter();

    // How long to display a line, in milliseconds.
    private final Cooldown displayCooldown = Core.getCooldown(DISPLAY_DURATION);
    // How long to wait before showing the next line, in milliseconds.
    private final Cooldown dialogueCooldown = Core.getCooldown(DISPLAY_DURATION + DIALOGUE_INTERVAL);

    private String activeLine; // The line to draw now, or {@code null} when there is none.
    private String pendingLine; // The line to draw next, or {@code null} when there is none.
    private String spawnLine; // The line to draw on spawn, or {@code null} when there is none.

    /**
     * Receives a boss event and queues a line for it, if that event has one.
     *
     * @param event
     *            The event, with boss type, position, health and phase.
     */
    @Override
    public void onBossEvent(final BossEvent event) {

        Trigger trigger = this.filter.filter(event);
        if(trigger == null) return;

        String line = this.dialogue.getDialogue(event.getBossType(), trigger);

        // Handle DEFEATED, SPAWN triggers
        if (trigger == Trigger.DEFEATED){
            this.pendingLine = null;
            this.spawnLine = null;
            showLine(line);
            return;
        }
        if (trigger == Trigger.SPAWN){
            this.pendingLine = null;
            this.spawnLine = line;
            showLine(line);
            return;
        }

        updateDialogue();

        enqeueLine(line);
    }

    /**
     * Updates the dialogue timeline and returns the active line.
     *
     * @return the active dialogue, or {@code null} if none is active
     */
    public String getActiveLine() {
        updateDialogue();
        return this.activeLine;
    }

    // Show the line immediately, and reset the dooldowns
    private void showLine(final String line){
        this.activeLine = line;
        this.displayCooldown.reset();
        this.dialogueCooldown.reset();
    }

    // When the display cooldown is finished, clear the line
    private void updateDialogue() {
        // Clear active line
        if(this.activeLine != null &&
            this.displayCooldown.checkFinished()){
            this.activeLine = null;
        }

        // Show pending line
        if(this.pendingLine != null && canShowLine()) {
            String nextLine = this.pendingLine;
            this.pendingLine = null;
            showLine(nextLine);
        }
    }

    // Returns true if the director can show a line now.
    private boolean canShowLine(){
        return this.activeLine == null &&
            this.dialogueCooldown.checkFinished();
    }

    // Enqueues a line to show or shows it immediately
    private void enqeueLine(final String line){
        if(canShowLine()){
            showLine(line);
        } else if(this.pendingLine ==null){
            this.pendingLine = line;
        }
    }

    // Restarts the spawn dialogue after the initial countdown
    public void restartSpawnLine() {
        if(this.spawnLine != null) {
            showLine(this.spawnLine);
            this.spawnLine =null;
        }
    }
}
