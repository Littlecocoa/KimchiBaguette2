package boss.dialogue;

import entity.Boss;

public class BossDialogue {

    /* boss A dialogues */
    private static final String[] BOSS_A_DIALOGUES = {
        "You've made it this far by luck!", 
        "Is that all you've got?",
        "I will never fall!",
        "I'll eat your little ship!",
        "Subordinates, I leave the rear to you.." 
    };

    /* boss B dialogues */
    private static final String[] BOSS_B_DIALOGUES = {
        "The previous boss was the weakest of us",
        "How weak",
        "You can't beat me that much!!",
        "Catch me before your luck runs out!",
        "I can't believe I fell.." 
    };

    public enum Trigger {
        SPAWN, PHASE_2, PHASE_3, SPECIAL, DEFEATED
    }

    /** Returns a dialogue using an index from 0 to 5. */
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
            case SPECIAL -> 3;
            case DEFEATED -> 4;
        };

        return lines[index];
    }
}