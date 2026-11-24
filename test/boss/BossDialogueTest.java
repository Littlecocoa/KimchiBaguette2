package boss;

import boss.dialogue.BossDialogue;
import boss.dialogue.BossDialogue.Trigger;
import entity.Boss.BossType;

public class BossDialogueTest {

    public static void main(String[] args) {
        testAllDialogues();
        System.out.println("BossDialogueTest passed.");
    }

    private static void testAllDialogues() {
        BossDialogue dialogue = new BossDialogue();

        for (BossType type : BossType.values()) {
            for (Trigger trigger : Trigger.values()) {

                String line = dialogue.getDialogue(type, trigger);

                if (line == null || line.isBlank()) {
                    throw new AssertionError(
                        "Missing dialogue: " + type + " / " + trigger
                    );
                }
            }
        }
    }
}
