package boss.dialogue;

import entity.Boss;

public class BossDialogue {

    /* boss B dialogues */
    private static final String[] BOSS_A_DIALOGUES = {
        "You’ve made it this far by luck!", // Boss pop out
        "You made it this far with just that power",
        "Is that all you've got?",
        "I just eat your little ship!",
        "I will never fall!",
        "Subordinates, I leave the rear to you.." // Boss defeated
    };

    /* boss B dialogues */
    private static final String[] BOSS_B_DIALOGUES = {
        "Your luck ends here", // Boss pop out
        "The previous boss was the weakest of us",
        "How weak",
        "You can't beat me that much!!",
        "Catch me before your luck runs out!",
        "I can't believe I fell.." // Boss defeated
    };

    public String getDialogue(final Boss.BossType bossType) {
        return getDialogue(bossType, 0);
    }

    /** Returns a dialogue using an index from 0 to 5. */
    public String getDialogue(final Boss.BossType bossType, final int index) {
        if (index < 0 || index >= 6) {
            throw new IndexOutOfBoundsException("Dialogue index must be 0 to 5.");
        }
        if (bossType == Boss.BossType.BOSS_A) {
            return BOSS_A_DIALOGUES[index];
        } else if (bossType == Boss.BossType.BOSS_B) {
            return BOSS_B_DIALOGUES[index];
        }
        return null;
    }
}
