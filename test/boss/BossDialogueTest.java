package boss;

import boss.dialogue.BossDialogue;
import boss.dialogue.BossDialogue.Trigger;
import boss.dialogue.BossDialogueEventFilter;
import entity.Boss;
import entity.Boss.BossType;

public class BossDialogueTest {

    public static void main(String[] args) {
        testAllDialogues();
        for (BossType type : BossType.values()) {
            testEventFiltering(type);
            testPhaseTriggers(type);
        }
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
                if (line.length() > 44)
                    throw new AssertionError("Dialogue exceeds 44 characters: " + line);
            }
        }
    }

    private static void testEventFiltering(BossType type) {
        Boss boss = new Boss(100, 78, type);
        BossDialogueEventFilter filter = new BossDialogueEventFilter();
        expect(Trigger.SPAWN, filter.filter(BossEvent.spawned(boss)));
        for (int i = 0; i < 100; i++)
            expect(null, filter.filter(BossEvent.damaged(boss, 1)));
        expect(null, filter.filter(BossEvent.attack(boss, "normal", false, false, 1)));
        expect(null, filter.filter(BossEvent.attack(boss, "normal", false, true, 0)));
        expect(null, filter.filter(BossEvent.attack(boss, "special", true, false, 8)));
        expect(Trigger.SPECIAL1,
                filter.filter(BossEvent.attack(boss, "special", true, true, 0)));
        expect(null, filter.filter(BossEvent.damaged(boss, 1)));
        expect(Trigger.SPECIAL2,
                filter.filter(BossEvent.attack(boss, "special", true, true, 0)));
        expect(Trigger.SPECIAL1,
                filter.filter(BossEvent.attack(boss, "special", true, true, 0)));
        expect(Trigger.DEFEATED, filter.filter(BossEvent.defeated(boss)));
        expect(null, filter.filter(BossEvent.defeated(boss)));
        expect(null, filter.filter(BossEvent.phaseChanged(boss, 1, 2)));
        expect(null, filter.filter(BossEvent.attack(boss, "special", true, true, 0)));
        expect(Trigger.SPAWN, filter.filter(BossEvent.spawned(boss)));
        expect(Trigger.SPECIAL1,
                filter.filter(BossEvent.attack(boss, "special", true, true, 0)));
        expect(Trigger.PHASE_2, filter.filter(BossEvent.phaseChanged(boss, 1, 2)));
    }

    private static void testPhaseTriggers(BossType type) {
        Boss boss = new Boss(100, 78, type);
        BossDialogueEventFilter filter = new BossDialogueEventFilter();
        expect(Trigger.SPAWN, filter.filter(BossEvent.spawned(boss)));
        expect(null, filter.filter(BossEvent.phaseChanged(boss, 0, 1)));
        expect(null, filter.filter(BossEvent.phaseChanged(boss, 2, 2)));
        expect(null, filter.filter(BossEvent.phaseChanged(boss, 3, 2)));
        expect(Trigger.PHASE_2, filter.filter(BossEvent.phaseChanged(boss, 1, 2)));
        for (int i = 0; i < 100; i++)
            expect(null, filter.filter(BossEvent.phaseChanged(boss, 1, 2)));
        expect(Trigger.PHASE_3, filter.filter(BossEvent.phaseChanged(boss, 2, 3)));
        expect(null, filter.filter(BossEvent.phaseChanged(boss, 2, 3)));
        expect(null, filter.filter(BossEvent.phaseChanged(boss, 3, 4)));
        expect(Trigger.SPAWN, filter.filter(BossEvent.spawned(boss)));
        expect(Trigger.PHASE_2, filter.filter(BossEvent.phaseChanged(boss, 1, 2)));
        expect(Trigger.PHASE_3, filter.filter(BossEvent.phaseChanged(boss, 2, 3)));

        // A large hit may jump directly to phase 3: use the event's new phase.
        expect(Trigger.SPAWN, filter.filter(BossEvent.spawned(boss)));
        expect(Trigger.PHASE_3, filter.filter(BossEvent.phaseChanged(boss, 1, 3)));
    }

    private static void expect(Trigger expected, Trigger actual) {
        if (expected != actual)
            throw new AssertionError("Expected " + expected + ", got " + actual);
    }
}
