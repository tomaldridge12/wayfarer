package dev.wayfarer.curios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Which carried curios work: conditions, no self-stacking, tools never passive; drop odds, xp, boosts, cooldowns. */
class CurioRulesTest {
    private static final CurioFixtures.Facts SURFACE = CurioFixtures.Facts.surface();
    private final CurioRules rules = new CurioRules(
            CurioConfigParser.parse(CurioFixtures.shippedYaml().get("curios"), CurioFixtures.SERVER).curios());

    private List<String> working(List<String> carried, CurioFacts facts) {
        return rules.working(carried, facts).stream().map(CurioDefinition::id).toList();
    }

    @Test
    void theLanternWorksOnlyBelowZero() {
        assertEquals(List.of(), working(List.of("lantern_of_the_deep"), SURFACE));
        assertEquals(List.of("lantern_of_the_deep"), working(List.of("lantern_of_the_deep"), SURFACE.at(-20, true)));
    }

    @Test
    void undergroundMeansNoSkyBelowTheSurfaceInTheOverworld() {
        Condition underground = Condition.parse("underground").orElseThrow();
        assertTrue(underground.holds(SURFACE.at(30, true)));
        assertFalse(underground.holds(SURFACE.at(30, false)), "open sky in a ravine is not underground");
        assertFalse(underground.holds(SURFACE.at(90, true)), "under a roof on a mountain is not underground");
        assertFalse(underground.holds(SURFACE.at(30, true).in("nether")));
    }

    @Test
    void weatherTimeBiomeWaterAndHeldToolConditions() {
        assertEquals(List.of("owl_feather"), working(List.of("owl_feather"), SURFACE.weather(true, false)));
        assertEquals(List.of(), working(List.of("owl_feather"), SURFACE.weather(true, false).in("end")), "no night in the End");
        assertEquals(List.of("stormglass"), working(List.of("stormglass"), SURFACE.weather(false, true)));
        assertEquals(List.of("glaucus_blessing"), working(List.of("glaucus_blessing"), SURFACE.biome("minecraft:is_ocean")));
        assertEquals(List.of(), working(List.of("glaucus_blessing"), SURFACE.biome("minecraft:is_river")));
        assertEquals(List.of("lucky_bobber"), working(List.of("lucky_bobber"), SURFACE.holding("fishing_rod")));
        assertEquals(List.of(), working(List.of("lucky_bobber"), SURFACE.holding("pickaxe")));
        assertTrue(Condition.parse("underwater").orElseThrow().holds(SURFACE.wet(true)));
    }

    @Test
    void twoCopiesWorkOnceAndToolsAreNeverPassive() {
        assertEquals(List.of("puffer_charm"), working(List.of("puffer_charm", "puffer_charm", "moss_heart"), SURFACE));
        assertEquals(List.of("puffer_charm", "flint_tooth"), working(List.of("nope", "puffer_charm", "flint_tooth"), SURFACE));
    }

    @Test
    void dropBonusFollowsBlockAndChance() {
        Effect.DropBonus half = new Effect.DropBonus(Set.of("gravel"), "flint", 1, 0.5);
        assertEquals(1, CurioRules.dropBonus(half, "gravel", 0.49));
        assertEquals(0, CurioRules.dropBonus(half, "gravel", 0.5));
        assertEquals(0, CurioRules.dropBonus(half, "sand", 0.0));
    }

    @Test
    void experienceTakesTheStrongestBonusForItsSource() {
        List<Effect.XpBonus> bonuses = List.of(new Effect.XpBonus(Set.of("fishing"), 1.5),
                new Effect.XpBonus(Set.of("fishing", "ores"), 1.25));
        assertEquals(6, CurioRules.xp(4, bonuses, "fishing"));
        assertEquals(5, CurioRules.xp(4, bonuses, "ores"));
        assertEquals(4, CurioRules.xp(4, bonuses, "mobs"));
    }

    @Test
    void boostsByNameDefaultToOne() {
        var brush = rules.working(List.of("archaeologists_brush"), SURFACE);
        assertEquals(2.0, CurioRules.boost(brush, "treasure"));
        assertEquals(1.0, CurioRules.boost(brush, "fishing"));
        assertEquals(1.0, CurioRules.boost(List.of(), "treasure"));
    }

    @Test
    void cooldownsBlockUntilTheyRunOutAndCanBeGivenBack() {
        CurioRules.Cooldowns cooldowns = new CurioRules.Cooldowns();
        UUID player = UUID.randomUUID();
        assertTrue(cooldowns.tryStart(player, "moss_heart", 120, 0));
        assertFalse(cooldowns.tryStart(player, "moss_heart", 120, 60_000));
        assertEquals(60_000, cooldowns.remaining(player, "moss_heart", 60_000));
        assertTrue(cooldowns.tryStart(player, "glow_jar", 60, 60_000), "each curio cools on its own");
        assertTrue(cooldowns.tryStart(player, "moss_heart", 120, 120_000));
        cooldowns.cancel(player, "moss_heart");
        assertEquals(0, cooldowns.remaining(player, "moss_heart", 120_001));
    }

    @Test
    void anEmptyRulesSetKnowsNothing() {
        assertTrue(new CurioRules(Map.of()).working(List.of("moss_heart"), SURFACE).isEmpty());
    }
}
