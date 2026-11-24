package boss.dialogue;

import entity.Boss;

public class BossDialogue {

    /* boss A dialogues */
    private static final String[] BOSS_A_DIALOGUES = {
        "You've made it this far by luck!",
        "Is that all you've got?",
        "I will never fall!",
        "I'll eat your little ship!",
        "You made it this far with just that power?",
        "Subordinates, I leave the rear to you.."
    };

    /* boss B dialogues */
    private static final String[] BOSS_B_DIALOGUES = {
        "The previous boss was the weakest of us",
        "How weak",
        "You can't beat me that much!!",
        "Catch me before your luck runs out!",
        "Your luck ends here",
        "I can't believe I fell.."
    };

    public enum Trigger {
        SPAWN, PHASE_2, PHASE_3, SPECIAL1,SPECIAL2, DEFEATED
    }

    /**
     * Returns a dialogue line for the specified boss type
     * and dialogue trigger.
     */
    public String getDialogue(final Boss.BossType bossType, Trigger trigger) {

        java.util.Objects.requireNonNull(bossType, "bossType");
        java.util.Objects.requireNonNull(trigger, "trigger");

        String[] lines = switch (bossType) {
            case BOSS_A -> BOSS_A_DIALOGUES;
            case BOSS_B -> BOSS_B_DIALOGUES;
        };

        int index = switch (trigger) {
            case SPAWN -> 0;
            case PHASE_2 -> 1;
            case PHASE_3 -> 2;
            case SPECIAL1 -> 3;
            case SPECIAL2 -> 4;
            case DEFEATED -> 5;
        };

        return lines[index];
    }
}
