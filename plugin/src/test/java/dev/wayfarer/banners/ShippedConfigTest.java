package dev.wayfarer.banners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** The banners.yml shipped in the jar must parse cleanly; catches broken YAML before a server boot does. */
class ShippedConfigTest {
    private static final Vocabulary ANY = new Vocabulary() {
        @Override
        public boolean isMob(String name) {
            return true;
        }

        @Override
        public boolean isPattern(String name) {
            return true;
        }

        @Override
        public boolean isColour(String name) {
            return true;
        }


        @Override
        public boolean isParticle(String name) {
            return true;
        }

        @Override
        public boolean isEffect(String name) {
            return true;
        }
    };

    @Test
    void shippedConfigLoadsFiveStarterBannersWithoutProblems() throws Exception {
        Map<String, Object> root;
        try (InputStream in = getClass().getResourceAsStream("/banners.yml")) {
            root = new Yaml().load(in);
        }
        BannerSet set = BannerConfigParser.parse(root, ANY);
        assertEquals(List.of(), set.problems());
        assertEquals(List.of("zombie", "skeleton", "creeper", "spider", "enderman"),
                set.banners().keySet().stream().filter(id -> !set.isBossFamily(set.banners().get(id).family())).toList());
        set.banners().values().stream().filter(b -> !set.isBossFamily(b.family())).forEach(b -> {
            assertEquals(100, b.kills());
            assertEquals(10, b.damageBonusPercent());
            assertEquals(10, b.damageReductionPercent());
            assertEquals(16, b.radius());
        });
        assertEquals(14, set.heads().size());
        assertEquals(76, set.relics().size());
        // Every relic in an alloy shape at tiers I, II and Ascended names its alloy; nothing else does.
        set.relics().values().forEach(r -> assertEquals(
                r.tier() >= 1 && r.tier() <= RelicDefinition.ASCENDED && RelicDefinition.alloyShape(r.item()) != null,
                r.alloy() != null, r.id()));
        assertEquals(22, set.relics().values().stream().filter(r -> r.alloy() != null).count());
        assertEquals("shakudo", set.findRelic("wayfarers_blade").orElseThrow().alloy());
        assertEquals("adamant", set.findRelic("zombie_weapon_3").orElseThrow().alloy());
        assertEquals(9, set.bossBase().size());
        assertEquals("zombie", set.baseFamilyOf("rotting_warlord"));
        assertEquals(null, set.baseFamilyOf("sand_pharaoh"));
        assertEquals(9, set.legendaries().size());
        assertEquals(10, set.relics().values().stream().filter(r -> r.tier() == RelicDefinition.ASCENDED).count());
        assertTrue(set.findRelic("gravecleaver").orElseThrow().dropOnly());
        assertEquals(10, set.findRelic("rotting_warlord_regalia_head").orElseThrow().kills());
        assertEquals(400, set.findRelic("zombie_weapon").orElseThrow().kills());
        assertEquals(240, set.findRelic("creeper_weapon").orElseThrow().kills());
        assertEquals(MobFamily.ALL, set.findRelic("wayfarers_blade").orElseThrow().family());
        assertEquals(RelicDefinition.Slot.OFFHAND, set.findRelic("enderman_armour").orElseThrow().slot());
        set.relics().values().forEach(r -> {
            assertFalse(r.item().startsWith("netherite"), r.id() + " must not be netherite");
            assertFalse(r.enchants().containsKey("minecraft:mending"), r.id() + " must not ship with Mending");
        });
        assertEquals(Map.of("minecraft:sharpness", 2, "minecraft:unbreaking", 2, "excellentenchants:vampire", 1,
                "excellentenchants:rage", 1), set.findRelic("zombie_weapon").orElseThrow().enchants());
        assertEquals("wild", set.findRelic("zombie_armour").orElseThrow().trimPattern());
        assertEquals(40, set.findRelic("zombie_weapon_2").orElseThrow().edgePercent());
        assertEquals(10, set.findRelic("wayfarers_blade").orElseThrow().edgePercent());
        set.relics().values().stream().filter(r -> !r.id().equals("wayfarers_blade") && r.tier() != RelicDefinition.REGALIA)
                .forEach(r -> assertFalse(r.perks().isEmpty(), r.id() + " should have a perk"));
        assertEquals(2, set.findRelic("spider_armour_2").orElseThrow().perks().size());
        assertEquals(0.45, set.findRelic("skeleton_weapon_2").orElseThrow().perks().getFirst().amount(), 1e-9);
        List<String> problems = new java.util.ArrayList<>();
        FeedbackSettings feedback = FeedbackSettings.parse(root.get("feedback"), ANY, problems);
        assertEquals(List.of(), problems);
        assertFalse(feedback.edgeParticles());
        assertFalse(feedback.markerEffect());
    }
}
