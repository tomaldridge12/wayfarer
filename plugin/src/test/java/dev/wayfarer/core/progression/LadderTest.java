package dev.wayfarer.core.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LadderTest {
    private static final long NOW = 1_800_000_000_000L;

    private final long[] clock = {NOW};
    private final Ladders ladders = new Ladders();
    private final ProgressionEngine engine =
            new ProgressionEngine(ProgressionSettings.defaults(), ladders, () -> clock[0]);

    private static Ladder zombie(Set<String> selector) {
        return new Ladder("zombie", "Zombie", "kill", selector, List.of(
                new Ladder.Rung(5, "relic:zombie_weapon"),
                new Ladder.Rung(3, "banner:zombie"),
                new Ladder.Rung(Ladder.Rung.NEVER, "relic:zombie_drop")), true);
    }

    private static KillFacts kill(String mob) {
        return new KillFacts(KillFacts.Killer.PLAYER, mob, "NATURAL", false, false, 1000);
    }

    private static List<String> rewards(ProgressionEngine.Outcome outcome) {
        return outcome.earned().stream().map(ProgressionEngine.Earned::reward).toList();
    }

    @Test
    void progressIsTheSumOfTheSelectedCounters() {
        ladders.set("banners", List.of(zombie(Set.of("kill:zombie", "kill:husk"))));
        ProgressionData data = new ProgressionData();
        engine.kill(data, kill("zombie"));
        engine.kill(data, kill("husk"));
        engine.kill(data, kill("creeper"));
        assertEquals(2, engine.progress(data, ladders.find("zombie").orElseThrow()));
    }

    @Test
    void rungsAreAwardedOnceInThresholdOrderWithTheTime() {
        ladders.set("banners", List.of(zombie(Set.of("kill:zombie"))));
        ProgressionData data = new ProgressionData();
        engine.kill(data, kill("zombie"));
        engine.kill(data, kill("zombie"));
        assertEquals(List.of("banner:zombie"), rewards(engine.kill(data, kill("zombie"))));
        assertEquals(NOW, data.earnedAt("banner:zombie"));
        assertEquals(List.of(), rewards(engine.kill(data, kill("zombie"))));
        assertEquals(List.of("relic:zombie_weapon"), rewards(engine.kill(data, kill("zombie"))));
        assertEquals(List.of(), rewards(engine.kill(data, kill("zombie"))));
    }

    @Test
    void severalRungsCanBeEarnedAtOnceInOrder() {
        ladders.set("banners", List.of(zombie(Set.of("kill:zombie"))));
        ProgressionData data = new ProgressionData();
        ProgressionEngine.Outcome outcome = engine.set(data, "kill:zombie", 400);
        assertEquals(List.of("banner:zombie", "relic:zombie_weapon"), rewards(outcome));
        assertEquals("zombie", outcome.earned().getFirst().ladder().id());
    }

    @Test
    void dropOnlyRungsAreNeverReachedByCounting() {
        ladders.set("banners", List.of(zombie(Set.of("kill:zombie"))));
        ProgressionData data = new ProgressionData();
        engine.set(data, "kill:zombie", Long.MAX_VALUE - 1);
        assertFalse(data.isEarned("relic:zombie_drop"));
    }

    @Test
    void settingACounterLowerNeverRevokes() {
        ladders.set("banners", List.of(zombie(Set.of("kill:zombie"))));
        ProgressionData data = new ProgressionData();
        engine.set(data, "kill:zombie", 10);
        engine.set(data, "kill:zombie", 0);
        assertTrue(data.isEarned("banner:zombie"));
        assertEquals(List.of(), rewards(engine.set(data, "kill:zombie", 10)));
    }

    @Test
    void regroupingATypeIntoAnotherLadderCreditsPastPlay() {
        ladders.set("banners", List.of(zombie(Set.of("kill:zombie"))));
        ProgressionData data = new ProgressionData();
        engine.set(data, "kill:husk", 2);
        engine.kill(data, kill("zombie"));
        assertFalse(data.isEarned("banner:zombie"));

        ladders.set("banners", List.of(zombie(Set.of("kill:zombie", "kill:husk"))));
        assertEquals(3, engine.progress(data, ladders.find("zombie").orElseThrow()));
        assertEquals(List.of("banner:zombie"), rewards(engine.kill(data, kill("husk"))));
    }

    @Test
    void anActivityClimbsEveryLadderThatReadsIt() {
        Ladder all = new Ladder("all", "All mobs", "kill", Set.of("kill:zombie", "kill:creeper"),
                List.of(new Ladder.Rung(2, "relic:blade")), true);
        ladders.set("banners", List.of(all, zombie(Set.of("kill:zombie"))));
        ProgressionData data = new ProgressionData();
        engine.kill(data, kill("creeper"));
        engine.set(data, "kill:zombie", 2);
        assertTrue(data.isEarned("relic:blade"));
        assertFalse(data.isEarned("banner:zombie"));
        assertEquals(List.of("banner:zombie"), rewards(engine.kill(data, kill("zombie"))));

        ProgressionData fresh = new ProgressionData();
        assertEquals(List.of("relic:blade", "banner:zombie"), rewards(engine.set(fresh, "kill:zombie", 3)));
    }

    @Test
    void anUncountedKillClimbsNothing() {
        ladders.set("banners", List.of(zombie(Set.of("kill:zombie"))));
        ProgressionData data = new ProgressionData();
        engine.set(data, "kill:zombie", 2);
        KillFacts spawner = new KillFacts(KillFacts.Killer.PLAYER, "zombie", "SPAWNER", false, false, 1000);
        assertEquals(List.of(), rewards(engine.kill(data, spawner)));
        assertFalse(data.isEarned("banner:zombie"));
    }

    @Test
    void aLadderIdBelongsToOneOwnerAndDroppingAnOwnerRemovesItsLadders() {
        ladders.set("banners", List.of(zombie(Set.of("kill:zombie"))));
        assertThrows(IllegalArgumentException.class, () -> ladders.set("other", List.of(zombie(Set.of()))));
        ladders.drop("banners");
        assertEquals(List.of(), ladders.all());
        assertEquals(List.of(), ladders.reading("kill:zombie"));
    }

    @Test
    void claimsNeedTheRewardAndWaitADayBetweenCopies() {
        ProgressionData data = new ProgressionData();
        assertEquals(ProgressionEngine.ClaimResult.NOT_EARNED, engine.claim(data, "banner:zombie"));
        engine.award(data, "banner:zombie");
        assertEquals(ProgressionEngine.ClaimResult.OK, engine.claim(data, "banner:zombie"));
        clock[0] += ProgressionEngine.CLAIM_COOLDOWN_MILLIS - 1;
        assertEquals(ProgressionEngine.ClaimResult.COOLDOWN, engine.claim(data, "banner:zombie"));
        assertEquals(1, engine.claimWait(data, "banner:zombie"));
        clock[0] += 1;
        assertEquals(ProgressionEngine.ClaimResult.OK, engine.claim(data, "banner:zombie"));
    }

    @Test
    void aDropAwardsOnce() {
        ProgressionData data = new ProgressionData();
        assertTrue(engine.award(data, "banner:skeleton"));
        assertFalse(engine.award(data, "banner:skeleton"));
    }
}
