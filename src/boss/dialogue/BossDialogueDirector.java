package boss.dialogue;

import boss.BossEvent;
import boss.BossEventListener;
import boss.dialogue.BossDialogue.Trigger;
import engine.Cooldown;
import engine.Core;

/**
 * Decides which boss line is on screen. It listens on the boss event bus and
 * exposes the line the renderer should draw, so neither the boss code nor the
 * renderer needs to know about the other.
 *
 * Contract only. {@link BossDialogue} supplies the line table and
 * {@link BossDialogueEventFilter} supplies event filtering and phase triggers.
 * The queue and timing task (KAN-20) should own one filter, pass events to
 * {@link BossDialogueEventFilter#filter(BossEvent)}, and queue only non-null
 * triggers using the event's boss type. {@link #getActiveLine()}
 * returns {@code null} for now, which the renderer already treats as "draw
 * nothing".
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

        this.activeLine = line;
    }

    /**
     * @return The line to draw now, or {@code null} when there is none.
     */
    public String getActiveLine() {
        return this.activeLine;
    }
}
