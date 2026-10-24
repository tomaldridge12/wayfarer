package dev.wayfarer.core.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PityTest {
    private static final PityDrop SKULL =
            new PityDrop("wither_skeleton_skull", "wither_skeleton_skull", Set.of("wither_skeleton"), null, 0.025);
    private static final PityDrop TRIDENT = new PityDrop("trident", "trident", Set.of("drowned"), "trident", 0.085);
    private static final ProgressionEngine ENGINE =
            new ProgressionEngine(ProgressionSettings.defaults().withPity(List.of(SKULL, TRIDENT)));

    private static KillFacts kill(String mob) {
        return new KillFacts(KillFacts.Killer.PLAYER, mob, "NATURAL", false, false, 1000);
    }

    private static List<String> skullKill(ProgressionData data, Set<String> drops) {
        return ENGINE.kill(data, kill("wither_skeleton"), "stone_sword", drops).pityItems();
    }

    @Test
    void thresholdIsOneOverTheChanceRoundedUp() {
        assertEquals(40, SKULL.threshold());
        assertEquals(12, TRIDENT.threshold());
        assertEquals(100, new PityDrop("x", "x", Set.of("x"), null, 0.01).threshold());
        assertEquals(3, new PityDrop("x", "x", Set.of("x"), null, 0.4).threshold());
    }

    @Test
    void theDropIsDueExactlyAtTheThresholdThenTheCounterStartsAgain() {
        ProgressionData data = new ProgressionData();
        for (int i = 1; i < 40; i++) {
            assertEquals(List.of(), skullKill(data, Set.of()), "kill " + i);
        }
        assertEquals(39, data.pity("wither_skeleton_skull"));
        assertEquals(List.of("wither_skeleton_skull"), skullKill(data, Set.of()));
        assertEquals(0, data.pity("wither_skeleton_skull"));
        assertEquals(List.of(), skullKill(data, Set.of()));
        assertEquals(1, data.pity("wither_skeleton_skull"));
    }

    @Test
    void aRealDropResetsTheCounter() {
        ProgressionData data = new ProgressionData();
        for (int i = 0; i < 30; i++) {
            skullKill(data, Set.of());
        }
        assertEquals(List.of(), skullKill(data, Set.of("wither_skeleton_skull", "bone")));
        assertEquals(0, data.pity("wither_skeleton_skull"));
    }

    @Test
    void onlyCountedKillsAdvanceTheCounter() {
        ProgressionData data = new ProgressionData();
        KillFacts spawner = new KillFacts(KillFacts.Killer.PLAYER, "wither_skeleton", "SPAWNER", false, false, 1000);
        KillFacts byWolf = new KillFacts(KillFacts.Killer.MOB_OR_PET, "wither_skeleton", "NATURAL", false, false, 1000);
        ENGINE.kill(data, spawner, null, Set.of());
        ENGINE.kill(data, byWolf, null, Set.of());
        assertEquals(0, data.pity("wither_skeleton_skull"));
        ENGINE.kill(data, kill("skeleton"), null, Set.of());
        assertEquals(0, data.pity("wither_skeleton_skull"));
    }

    @Test
    void theHeldItemConditionLimitsWhichMobsCount() {
        ProgressionData data = new ProgressionData();
        ENGINE.kill(data, kill("drowned"), null, Set.of());
        ENGINE.kill(data, kill("drowned"), "fishing_rod", Set.of());
        assertEquals(0, data.pity("trident"));
        List<String> got = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            got.addAll(ENGINE.kill(data, kill("drowned"), "trident", Set.of()).pityItems());
        }
        assertEquals(List.of("trident"), got);
    }

    @Test
    void thePityTableParsesAndSkipsBadEntries() {
        List<String> problems = new ArrayList<>();
        ProgressionSettings.Names names = new ProgressionSettings.Names() {
            @Override
            public boolean isSpawnReason(String name) {
                return true;
            }

            @Override
            public boolean isItem(String name) {
                return !name.equals("nope");
            }

            @Override
            public boolean isMob(String name) {
                return !name.equals("ghost");
            }

            @Override
            public boolean isAdvancement(String name) {
                return true;
            }
        };
        List<PityDrop> drops = PityDrop.parse(Map.of(
                "trident", Map.of("item", "minecraft:trident", "mobs", List.of("drowned"), "held-item", "trident", "chance", 0.085),
                "bad_item", Map.of("item", "nope", "mobs", List.of("zombie"), "chance", 0.1),
                "bad_mob", Map.of("item", "bone", "mobs", List.of("ghost"), "chance", 0.1),
                "bad_chance", Map.of("item", "bone", "mobs", List.of("zombie"), "chance", 0)), names, problems);
        assertEquals(List.of(TRIDENT), drops);
        assertEquals(3, problems.size());
    }
}
