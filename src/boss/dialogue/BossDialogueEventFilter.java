package boss.dialogue;

import java.util.EnumSet;
import java.util.Objects;

import boss.BossEvent;
import boss.dialogue.BossDialogue.Trigger;

/**
 * Converts events for one boss encounter into dialogue triggers. The director
 * owns one filter and applies its queue and timing rules after filtering.
 */
public final class BossDialogueEventFilter {

    private final EnumSet<Trigger> phases = EnumSet.noneOf(Trigger.class);
    private boolean defeated;
    private boolean secondSpecial;

    /**
     * Returns a trigger, or {@code null} when the event must not produce a line.
     * SPAWNED starts a new encounter, including another boss of the same type.
     * Phase triggers are consumed on receipt even if a downstream queue is full.
     * The two special lines alternate on accepted warnings, independently of
     * attack names, which are not part of the dialogue table contract.
     *
     * @param event Event from the boss event bus.
     * @return The matching dialogue trigger, or {@code null}.
     */
    public Trigger filter(final BossEvent event) {
        Objects.requireNonNull(event, "event");
        if (event.getType() == BossEvent.Type.SPAWNED) {
            this.phases.clear();
            this.defeated = false;
            this.secondSpecial = false;
            return Trigger.SPAWN;
        }
        if (this.defeated)
            return null;

        switch (event.getType()) {
            case PHASE_CHANGED:
                if (event.getPhase() <= event.getPreviousPhase())
                    return null;
                Trigger phase = switch (event.getPhase()) {
                    case 2 -> Trigger.PHASE_2;
                    case 3 -> Trigger.PHASE_3;
                    default -> null;
                };
                return phase != null && this.phases.add(phase) ? phase : null;
            case ATTACK:
                if (!event.isWindup() || !event.isSpecial())
                    return null;
                Trigger special = this.secondSpecial
                        ? Trigger.SPECIAL2 : Trigger.SPECIAL1;
                this.secondSpecial = !this.secondSpecial;
                return special;
            case DEFEATED:
                this.defeated = true;
                return Trigger.DEFEATED;
            default:
                return null;
        }
    }
}
