package dev.wayfarer.curios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** Special gear presets and supplies: parsing (shipped and bad entries), supply rolls and bait. */
class GearSupplyTest {
    /** Every item but "nope"; minecraft enchants plus a few ExcellentEnchants ones; real crate variants. */
    static final GearSupplyParser.Names NAMES = new GearSupplyParser.Names() {
        @Override
        public boolean isItem(String key) {
            return !key.startsWith("nope");
        }

        @Override
        public boolean isEnchant(String key) {
            return key.startsWith("minecraft:") && !key.equals("minecraft:nope") || key.startsWith("excellentenchants:");
        }

        @Override
        public boolean isVariant(String mob, String variant) {
            return mob.equals("sheep") ? Set.of("white", "black", "brown", "pink", "light_blue", "lime", "orange").contains(variant)
                    : Set.of("temperate", "cold", "warm").contains(variant);
        }
    };

    private static GearSupplyParser.Result parse(String yaml) {
        Map<String, Object> root = new Yaml().load(yaml);
        return GearSupplyParser.parse(root.get("gear"), root.get("supplies"), NAMES);
    }

    @Test
    void theShippedGearAndSuppliesLoad() {
        Map<String, Object> root = CurioFixtures.shippedYaml();
        GearSupplyParser.Result r = GearSupplyParser.parse(root.get("gear"), root.get("supplies"), NAMES);
        assertEquals(List.of(), r.problems());
        assertEquals(List.of(), r.notices());
        GearPreset hoe = r.gear().get("hoe_of_a_hundred_hoes");
        assertEquals("alloy:bronze_hoe", hoe.base());
        assertEquals("iron_hoe", hoe.fallback());
        assertEquals(5, hoe.enchants().get("minecraft:efficiency"));
        assertEquals(1, hoe.enchants().get("excellentenchants:replanter"));
        assertEquals("netherite_axe", r.gear().get("hogbane").fallback());
        assertTrue(r.supplies().keySet().containsAll(Set.of("seed_bundle", "floral_bundle", "mushy_bundle", "animal_crate",
                "wayfarers_bait", "tome_of_experience")));
        r.gear().values().forEach(g -> assertTrue(!g.flavour().isBlank(), g.id() + " has no flavour"));
    }

    @Test
    void badGearAndSuppliesAreSkippedWithAProblemEach() {
        GearSupplyParser.Result r = parse("""
                gear:
                  good: {base: alloy:steel_axe, enchants: {sharpness: 2}}
                  bad_base: {base: nope_axe}
                  bad_level: {base: iron_axe, enchants: {sharpness: 0}}
                  bad_vanilla_enchant: {base: iron_axe, enchants: {nope: 1}}
                  model_on_alloy: {base: alloy:steel_axe, model: gear/x}
                  missing_ee: {base: iron_axe, enchants: {otherplugin:zap: 1}}
                supplies:
                  good: {opens: loot, loot: [{item: wheat_seeds, amount: 1-3}]}
                  no_loot: {opens: loot}
                  bad_amount: {opens: loot, loot: [{item: wheat_seeds, amount: 3-1}]}
                  bad_choice: {opens: crate, choices: [cow/purple]}
                  wolf_crate: {opens: crate, choices: [wolf/pale]}
                  no_amount: {opens: bait}
                  what: {opens: hatch}
                """);
        assertEquals(Set.of("good", "missing_ee"), r.gear().keySet());
        assertEquals(Set.of("good"), r.supplies().keySet());
        assertEquals(10, r.problems().size(), r.problems().toString());
        assertEquals(1, r.notices().size(), "an enchant from a missing plugin is a notice, not a problem");
    }

    @Test
    void rollsPickByWeightAndMergeByItem() {
        List<SupplyRules.Entry> loot = List.of(new SupplyRules.Entry("wheat_seeds", 2, 2, 1), new SupplyRules.Entry("nope", 1, 1, 0));
        assertEquals(Map.of("wheat_seeds", 8), SupplyRules.roll(loot, 4, new Random(1)));
        List<SupplyRules.Entry> two = List.of(new SupplyRules.Entry("a", 1, 3, 1), new SupplyRules.Entry("b", 1, 3, 1));
        Random random = new Random(7);
        for (int i = 0; i < 200; i++) {
            Map<String, Integer> rolled = SupplyRules.roll(two, 3, random);
            int total = rolled.values().stream().mapToInt(Integer::intValue).sum();
            assertTrue(total >= 3 && total <= 9, "3 rolls of 1-3: " + rolled);
        }
    }

    @Test
    void baitDoublesRareOddsOnceACatchAndIsCapped() {
        assertEquals(new SupplyRules.Cast(2, 9), SupplyRules.cast(10));
        assertEquals(new SupplyRules.Cast(1, 0), SupplyRules.cast(0));
        assertEquals(SupplyRules.MAX_BAIT, SupplyRules.addBait(60, 10));
        assertEquals(20, SupplyRules.addBait(10, 10));
    }
}
