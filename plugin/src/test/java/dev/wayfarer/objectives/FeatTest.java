package dev.wayfarer.objectives;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.wayfarer.core.progression.Ladder;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** Feats: the shipped list, tags and groups, the one-rung ladder with its marker last, status and bad entries. */
class FeatTest {
    private static final ObjectiveConfigParser.Result SHIPPED = ObjectiveFixtures.shipped();

    @Test
    void theShippedFeatsAllLoadEachWithAStoryAndAPrize() {
        assertEquals(List.of(), SHIPPED.problems());
        assertTrue(SHIPPED.feats().size() >= 80, "feats are added over time, never lost");
        assertTrue(SHIPPED.feats().values().stream().filter(Feat::hidden).count() >= 7, "hidden feats stay in");
        SHIPPED.feats().values().forEach(f -> {
            assertFalse(f.flavour().isBlank(), f.id());
            assertFalse(f.rewards().isEmpty(), f.id());
        });
    }

    @Test
    void tagsGroupsPrefixesAndAllExpand() {
        Feat hoes = SHIPPED.feats().get("hundredth_hoe");
        assertTrue(hoes.selector().contains("craft:netherite_hoe"));
        Feat records = SHIPPED.feats().get("record_keeper");
        assertEquals(3, records.at(), "at: all is every record");
        assertTrue(records.distinct());
        assertTrue(SHIPPED.feats().get("deep_delver").selector().contains("mine:deepslate*"));
    }

    @Test
    void thePetFeatsNeedEverySheepColourAndADragonRespawn() {
        Feat sheep = SHIPPED.feats().get("rainbow_shepherd");
        assertEquals(16, sheep.at());
        assertTrue(sheep.distinct() && sheep.selector().contains("dye:sheep/light_blue"));
        assertEquals(List.of("cosmetic:rainbow_sheep"), sheep.rewards());
        Feat dragon = SHIPPED.feats().get("dragon_encore");
        assertTrue(dragon.selector().contains("respawn:ender_dragon"));
        assertEquals(List.of("cosmetic:mini_ender_dragon"), dragon.rewards());
    }

    @Test
    void aFeatIsOneRungOfEveryRewardThenItsMarker() {
        Feat moss = SHIPPED.feats().get("moss_tender");
        Ladder ladder = FeatRules.ladder(moss);
        assertEquals("feat_moss_tender", ladder.id());
        assertEquals(List.of("curio:moss_heart", "cosmetic:mossborn", "cosmetic:spores", "feat:moss_tender"),
                ladder.rungs().stream().map(Ladder.Rung::reward).toList());
        assertTrue(ladder.rungs().stream().allMatch(r -> r.threshold() == 10_000));
        assertTrue(ladder.announces("feat:moss_tender"));
        assertFalse(ladder.announces("curio:moss_heart"));
    }

    @Test
    void statusReadsProgressAndEarnedAndHidesSecrets() {
        Feat moss = SHIPPED.feats().get("moss_tender");
        FeatRules.Status s = FeatRules.status(moss, Map.of("mine:moss_block", 4000L, "mine:pale_moss_block", 213L), Map.of());
        assertEquals(4213, s.progress());
        assertFalse(s.earned());
        assertTrue(s.shown(moss));
        Feat warden = SHIPPED.feats().get("unheard");
        assertFalse(FeatRules.status(warden, Map.of(), Map.of()).shown(warden));
        assertTrue(FeatRules.status(warden, Map.of("kill:warden", 1L), Map.of("feat:unheard", 5L)).shown(warden));
        assertEquals("10,000", FeatRules.number(10_000));
    }

    @Test
    void cartographerCountsDifferentBiomes() {
        Feat cartographer = SHIPPED.feats().get("cartographer");
        FeatRules.Status s = FeatRules.status(cartographer, Map.of("discover:biome/plains", 1L, "discover:biome/desert", 1L), Map.of());
        assertEquals(2, s.progress());
    }

    @Test
    void badFeatsAndCosmeticsAreSkipped() {
        ObjectiveConfigParser.Result r = ObjectiveConfigParser.parse(new Yaml().load("""
                cosmetics:
                  shiny: {kind: trail, particle: nope_dust}
                  hat: {kind: hat}
                  ok: {kind: title, name: "Ok"}
                feats:
                  good: {counts: [mine:stone], at: 10, rewards: [curio:moss_heart, cosmetic:ok]}
                  twice: {counts: [mine:dirt], at: 10, rewards: [curio:moss_heart]}
                  no_counts: {at: 10, rewards: [curio:a]}
                  no_at: {counts: [mine:dirt], rewards: [curio:b]}
                  all_sum: {counts: [mine:dirt], at: all, rewards: [curio:c]}
                  bad_cosmetic: {counts: [mine:dirt], at: 1, rewards: [cosmetic:shiny]}
                """), ObjectiveFixtures.SERVER);
        assertEquals(List.of("good"), List.copyOf(r.feats().keySet()));
        assertEquals(List.of("ok"), List.copyOf(r.cosmetics().keySet()));
        assertEquals(9, r.problems().size(), r.problems().toString());
    }
}
