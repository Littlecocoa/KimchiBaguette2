package boss;

import boss.dialogue.BossDialogue;
import boss.dialogue.BossDialogue.Trigger;
import boss.dialogue.BossDialogueEventFilter;
import boss.dialogue.BossDialogueDirector;
import entity.Boss;
import entity.Boss.BossType;

public class BossDialogueTest {

    public static void main(String[] args)
            throws InterruptedException {

        testAllDialogues();

        for (BossType type : BossType.values()) {

            testEventFiltering(type);
            testPhaseTriggers(type);
            testDamageSpam(type);
        }

        testDialogueTiming(BossType.BOSS_A);
        testDialogueQueue(BossType.BOSS_A);
        testDefeatPriority(BossType.BOSS_A);

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

    private static void expectLine(String expected, String actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(
                    "Expected " + expected + ", got " + actual);
        }
    }

    private static void testDialogueTiming(BossType type)
        throws InterruptedException {

        Boss boss = new Boss(100, 78, type);
        BossDialogue dialogue = new BossDialogue();

        BossDialogueDirector director =
                new BossDialogueDirector();

        director.onBossEvent(BossEvent.spawned(boss));

        String expected = dialogue.getDialogue(
                type, Trigger.SPAWN);

        expectLine(expected, director.getActiveLine());

        Thread.sleep(2600);

        expectLine(null, director.getActiveLine());
    }

    private static void testDialogueQueue(BossType type)
        throws InterruptedException {

        Boss boss = new Boss(100, 78, type);
        BossDialogue dialogue = new BossDialogue();

        BossDialogueDirector director =
                new BossDialogueDirector();

        director.onBossEvent(BossEvent.spawned(boss));
        director.onBossEvent(
                BossEvent.phaseChanged(boss, 1, 2));
        director.onBossEvent(
                BossEvent.phaseChanged(boss, 2, 3));

        expectLine(
                dialogue.getDialogue(type, Trigger.SPAWN),
                director.getActiveLine());

        Thread.sleep(2600);
        expectLine(null, director.getActiveLine());

        Thread.sleep(2000);
        expectLine(
                dialogue.getDialogue(type, Trigger.PHASE_2),
                director.getActiveLine());

        Thread.sleep(4600);
        expectLine(null, director.getActiveLine());
    }

    private static void testDefeatPriority(BossType type)
            throws InterruptedException {

        Boss boss = new Boss(100, 78, type);

        BossDialogue dialogue = new BossDialogue();

        BossDialogueDirector director =
                new BossDialogueDirector();

        director.onBossEvent(BossEvent.spawned(boss));

        director.onBossEvent(
                BossEvent.phaseChanged(boss, 1, 2));

        director.onBossEvent(BossEvent.defeated(boss));

        expectLine(
                dialogue.getDialogue(type, Trigger.DEFEATED),
                director.getActiveLine());

        Thread.sleep(4600);

        expectLine(null, director.getActiveLine());
    }

    private static void testDamageSpam(BossType type) {
        Boss boss = new Boss(100, 78, type);
        BossDialogue dialogue = new BossDialogue();
        BossDialogueDirector director =
                new BossDialogueDirector();

        director.onBossEvent(BossEvent.spawned(boss));

        String expected = dialogue.getDialogue(
                type, Trigger.SPAWN);

        expectLine(expected, director.getActiveLine());

        for (int i = 0; i < 100; i++) {
            director.onBossEvent(
                    BossEvent.damaged(boss, 1));
            expectLine(expected, director.getActiveLine());
        }
    }
}
