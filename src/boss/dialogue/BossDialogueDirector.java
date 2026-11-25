package boss.dialogue;

import boss.BossEvent;
import boss.BossEventListener;

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

    /**
     * Receives a boss event and queues a line for it, if that event has one.
     *
     * @param event
     *            The event, with boss type, position, health and phase.
     */
    @Override
    public void onBossEvent(final BossEvent event) {
    }

    /**
     * @return The line to draw now, or {@code null} when there is none.
     */
    public String getActiveLine() {
        return null;
    }
}
