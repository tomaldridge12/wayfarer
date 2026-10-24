package dev.wayfarer.banners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.wayfarer.core.progression.KillFacts;
import dev.wayfarer.core.progression.Ladder;
import dev.wayfarer.core.progression.Ladders;
import dev.wayfarer.core.progression.ProgressionData;
import dev.wayfarer.core.progression.ProgressionEngine;
import dev.wayfarer.core.progression.ProgressionSettings;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The banners module's ladders, run by core's engine, behave as the old banner reward rules did. */
class BannerLaddersTest {
    private static final Set<String> HOSTILE = Set.of("zombie", "husk", "creeper", "witch");
    private static final Set<String> EVERY = Set.of("zombie", "husk", "creeper", "witch", "cow");

    private static final BannerDefinition ZOMBIE_BANNER =
            new BannerDefinition("zombie", "Zombie", "green", List.of(), "zombie", 3, 10, 10, 16);
    private static final BannerDefinition ELITE_BANNER =
            new BannerDefinition("zombie2", "Zombie 2", "green", List.of(), "zombie", 5, 10, 10, 16);

    private final Ladders ladders = new Ladders();
    private final ProgressionEngine engine =
            new ProgressionEngine(ProgressionSettings.defaults(), ladders, () -> 1_800_000_000_000L);

    private static Map<String, MobFamily> families(String zombieMobs) {
        Map<String, MobFamily> f = new LinkedHashMap<>();
        f.put("zombie", new MobFamily("zombie", Set.of(zombieMobs.split(","))));
        f.put("creeper", new MobFamily("creeper", Set.of("creeper")));
        f.put("rotting_warlord", new MobFamily("rotting_warlord", Set.of()));
        return f;
    }

    private static BannerSet set(Map<String, MobFamily> families, boolean countTowardBase) {
        Map<String, BannerDefinition> banners = new LinkedHashMap<>();
        banners.put("zombie", ZOMBIE_BANNER);
        banners.put("zombie2", ELITE_BANNER);
        banners.put("rotting_warlord", new BannerDefinition("rotting_warlord", "Warlord", "green", List.of(),
                "rotting_warlord", 1, 10, 10, 16));
        Map<String, RelicDefinition> relics = new LinkedHashMap<>();
        relics.put("zombie_weapon", new RelicDefinition("zombie_weapon", "Zombie Cleaver", "zombie", 6, 1,
                RelicDefinition.Slot.WEAPON));
        relics.put("zombie_ascended", new RelicDefinition("zombie_ascended", "Ascended", "zombie",
                RelicDefinition.DROP_ONLY, 3, RelicDefinition.Slot.WEAPON));
        relics.put("wayfarers_blade", new RelicDefinition("wayfarers_blade", "Wayfarer's Blade", MobFamily.ALL, 7, 2,
                RelicDefinition.Slot.WEAPON));
        BossLoot.Settings d = BossLoot.Settings.defaults();
        return new BannerSet(families, banners, Map.of("zombie", new HeadDefinition("zombie", "Zombie Trophy", "zombie", 4)),
                relics, CountingSettings.defaults(), Map.of(), List.of(), List.of(), Map.of("rotting_warlord", "zombie"),
                new BossLoot.Settings(d.ascendedChance(), d.legendaryChance(), d.xpPerStar(), d.materials(), countTowardBase),
                Map.of());
    }

    private void register(BannerSet set, Set<String> allMobTypes) {
        ladders.set("banners", BannerLadders.build(set, allMobTypes, true));
    }

    private static KillFacts kill(String mob) {
        return new KillFacts(KillFacts.Killer.PLAYER, mob, "NATURAL", false, false, 1000);
    }

    private long progress(ProgressionData data, String ladder) {
        return engine.progress(data, ladders.find(ladder).orElseThrow());
    }

    private List<String> earned(ProgressionEngine.Outcome outcome) {
        return outcome.earned().stream().map(ProgressionEngine.Earned::reward).toList();
    }

    @Test
    void aKillCountsTowardItsFamilyAndAllMobs() {
        register(set(families("zombie,husk"), false), HOSTILE);
        ProgressionData data = new ProgressionData();
        engine.kill(data, kill("husk"));
        assertEquals(1, progress(data, "zombie"));
        assertTrue(ladders.find("creeper").isEmpty()); // a family with no rewards has no ladder
        assertEquals(1, progress(data, MobFamily.ALL));
    }

    @Test
    void hostileMobsOutsideEveryFamilyStillCountTowardAllMobs() {
        register(set(families("zombie,husk"), false), HOSTILE);
        ProgressionData data = new ProgressionData();
        engine.kill(data, kill("witch"));
        engine.kill(data, kill("cow"));
        assertEquals(1, progress(data, MobFamily.ALL));
    }

    @Test
    void passiveMobsCountTowardAllMobsOnlyWhenConfiguredAny() {
        register(set(families("zombie,husk"), false), EVERY);
        ProgressionData data = new ProgressionData();
        engine.kill(data, kill("cow"));
        assertEquals(1, progress(data, MobFamily.ALL));
    }

    @Test
    void theLadderOrderIsBannersHeadsAndRelicsByKillCountEachOnce() {
        register(set(families("zombie,husk"), false), HOSTILE);
        ProgressionData data = new ProgressionData();
        List<String> got = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            got.addAll(earned(engine.kill(data, kill("zombie"))));
        }
        assertEquals(List.of("banner:zombie", "head:zombie", "banner:zombie2", "relic:zombie_weapon",
                "relic:wayfarers_blade"), got);
        assertFalse(data.isEarned("relic:zombie_ascended"));
    }

    @Test
    void setCountAwardsCrossedThresholdsOnce() {
        register(set(families("zombie,husk"), false), HOSTILE);
        ProgressionData data = new ProgressionData();
        assertEquals(List.of("relic:wayfarers_blade", "banner:zombie", "head:zombie", "banner:zombie2",
                "relic:zombie_weapon"), earned(engine.set(data, "kill:zombie", 99)));
        assertEquals(List.of(), earned(engine.set(data, "kill:zombie", 200)));
    }

    @Test
    void movingAMobTypeToAnotherFamilyTakesItsPastKillsWithIt() {
        register(set(families("zombie"), false), HOSTILE);
        ProgressionData data = new ProgressionData();
        engine.set(data, "kill:husk", 2);
        assertEquals(0, progress(data, "zombie"));

        register(set(families("zombie,husk"), false), HOSTILE);
        assertEquals(2, progress(data, "zombie"));
        assertEquals(List.of("banner:zombie"), earned(engine.kill(data, kill("husk"))));
    }

    @Test
    void aBossDefeatCountsForItsFamilyAndAllMobsAndItsBaseOnlyWhenSwitchedOn() {
        register(set(families("zombie,husk"), false), HOSTILE);
        ProgressionData off = new ProgressionData();
        assertEquals(List.of("banner:rotting_warlord"), earned(engine.record(off, "defeat:rotting_warlord")));
        assertEquals(1, progress(off, "rotting_warlord"));
        assertEquals(1, progress(off, MobFamily.ALL));
        assertEquals(0, progress(off, "zombie"));

        register(set(families("zombie,husk"), true), HOSTILE);
        ProgressionData on = new ProgressionData();
        engine.record(on, "defeat:rotting_warlord");
        assertEquals(1, progress(on, "zombie"));
    }

    @Test
    void ladderShapes() {
        List<Ladder> built = BannerLadders.build(set(families("zombie,minecraft:husk"), false), HOSTILE, false);
        assertEquals(List.of(MobFamily.ALL, "zombie", "rotting_warlord"), built.stream().map(Ladder::id).toList());
        Ladder zombie = built.get(1);
        assertEquals(Set.of("kill:zombie", "kill:husk"), zombie.selector());
        assertEquals("Zombie", zombie.title());
        assertEquals(Ladder.Rung.NEVER, zombie.rungs().getLast().threshold());
        assertFalse(zombie.announce());
        assertEquals("defeat", built.get(2).unit());
        assertTrue(built.getFirst().selector().contains("defeat:rotting_warlord"));
    }
}
