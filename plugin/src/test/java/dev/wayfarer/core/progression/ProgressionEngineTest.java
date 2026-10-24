package dev.wayfarer.core.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProgressionEngineTest {
    private static final ProgressionEngine ENGINE = new ProgressionEngine(ProgressionSettings.defaults());

    private static KillFacts kill(String mob) {
        return new KillFacts(KillFacts.Killer.PLAYER, mob, "NATURAL", false, false, 1000);
    }

    @Test
    void aCountedKillRaisesTheRawTypeCounter() {
        ProgressionData data = new ProgressionData();
        ProgressionEngine.Outcome outcome = ENGINE.kill(data, kill("husk"));
        assertTrue(outcome.counted());
        assertEquals(List.of("kill:husk"), outcome.raised());
        assertEquals(1, data.count("kill:husk"));
        assertEquals(0, data.count("kill:zombie"));
    }

    @Test
    void everyMobTypeCountsNotOnlyHostileOnes() {
        ProgressionData data = new ProgressionData();
        ENGINE.kill(data, kill("cow"));
        ENGINE.kill(data, kill("cow"));
        assertEquals(2, data.count("kill:cow"));
    }

    @Test
    void datapackTypesKeepTheirNamespace() {
        assertEquals("kill:husk", Activity.key("kill", "minecraft", "husk"));
        assertEquals("kill:incendium:inferno_knight", Activity.key("kill", "incendium", "inferno_knight"));
        ProgressionData data = new ProgressionData();
        ENGINE.kill(data, kill("incendium:inferno_knight"));
        assertEquals(1, data.count("kill:incendium:inferno_knight"));
    }

    @Test
    void onlyPlayerCreditCounts() {
        for (KillFacts.Killer other : List.of(KillFacts.Killer.MOB_OR_PET, KillFacts.Killer.ENVIRONMENT)) {
            ProgressionData data = new ProgressionData();
            KillFacts k = new KillFacts(other, "zombie", "NATURAL", false, false, 1000);
            assertFalse(ENGINE.kill(data, k).counted(), other.name());
            assertEquals(Map.of(), data.countersView());
        }
    }

    @Test
    void defaultSpawnReasonsAreExcludedButCustomIsNot() {
        for (String reason : List.of("SPAWNER", "TRIAL_SPAWNER", "OMINOUS_ITEM_SPAWNER", "SPAWNER_EGG",
                "SLIME_SPLIT", "BREEDING", "EGG", "DISPENSE_EGG")) {
            KillFacts k = new KillFacts(KillFacts.Killer.PLAYER, "zombie", reason, false, false, 1000);
            assertFalse(ENGINE.kill(new ProgressionData(), k).counted(), reason);
        }
        KillFacts custom = new KillFacts(KillFacts.Killer.PLAYER, "zombie", "custom", false, false, 1000);
        assertTrue(ENGINE.kill(new ProgressionData(), custom).counted());
    }

    @Test
    void exclusionListIsConfigurable() {
        ProgressionEngine relaxed = new ProgressionEngine(
                ProgressionSettings.defaults().withCredit(new CreditRules(Set.of(), 0)));
        KillFacts k = new KillFacts(KillFacts.Killer.PLAYER, "zombie", "SPAWNER", false, false, 1000);
        assertTrue(relaxed.kill(new ProgressionData(), k).counted());
    }

    @Test
    void tamedAndLeashedMobsDoNotCount() {
        assertFalse(ENGINE.kill(new ProgressionData(),
                new KillFacts(KillFacts.Killer.PLAYER, "wolf", "NATURAL", true, false, 1000)).counted());
        assertFalse(ENGINE.kill(new ProgressionData(),
                new KillFacts(KillFacts.Killer.PLAYER, "cow", "NATURAL", false, true, 1000)).counted());
    }

    @Test
    void mobsYoungerThanTheMinimumLifetimeDoNotCount() {
        ProgressionEngine strict = new ProgressionEngine(
                ProgressionSettings.defaults().withCredit(new CreditRules(Set.of(), 200)));
        assertFalse(strict.kill(new ProgressionData(),
                new KillFacts(KillFacts.Killer.PLAYER, "zombie", "NATURAL", false, false, 199)).counted());
        assertTrue(strict.kill(new ProgressionData(),
                new KillFacts(KillFacts.Killer.PLAYER, "zombie", "NATURAL", false, false, 200)).counted());
    }

    @Test
    void settingACounterReplacesItAndNeverGoesBelowZero() {
        ProgressionData data = new ProgressionData();
        ENGINE.kill(data, kill("zombie"));
        ENGINE.set(data, "kill:zombie", 40);
        assertEquals(40, data.count("kill:zombie"));
        ENGINE.set(data, "kill:zombie", -5);
        assertEquals(0, data.count("kill:zombie"));
    }

    @Test
    void creditRulesParseFromConfigAndReportUnknownSpawnReasons() {
        List<String> problems = new java.util.ArrayList<>();
        CreditRules rules = CreditRules.parse(Map.of(
                "exclude-spawn-reasons", List.of("spawner", "nonsense"),
                "min-mob-lifetime-ticks", 40), name -> !name.equals("NONSENSE"), problems);
        assertEquals(new CreditRules(Set.of("SPAWNER"), 40), rules);
        assertEquals(1, problems.size());
        assertEquals(CreditRules.defaults(), CreditRules.parse(null, name -> true, problems));
    }
}
