package dev.wayfarer.banners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BannerConfigParserTest {
    private static final Set<String> MOBS = Set.of("zombie", "husk", "drowned", "skeleton", "creeper");
    private static final Vocabulary VOCAB = new Vocabulary() {
        @Override
        public boolean isMob(String name) {
            return MOBS.contains(name);
        }

        @Override
        public boolean isPattern(String name) {
            return Set.of("creeper", "border").contains(name);
        }

        @Override
        public boolean isParticle(String name) {
            return name.equals("HAPPY_VILLAGER") || name.equals("FLAME");
        }

        @Override
        public boolean isEffect(String name) {
            return name.equals("luck") || name.equals("glowing");
        }


        @Override
        public boolean isColour(String name) {
            return Set.of("white", "green", "lime", "black").contains(name);
        }
    };

    private static Map<String, Object> root(Map<String, Object> families, Map<String, Object> banners) {
        return Map.of("families", families, "banners", banners);
    }

    private static Map<String, Object> ordered(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    void validBannerLoadsWithAllFields() {
        BannerSet set = BannerConfigParser.parse(root(
                ordered("zombie", List.of("zombie", "husk")),
                ordered("z", ordered("name", "Zed", "colour", "green", "patterns", List.of("creeper:lime"),
                        "family", "zombie", "kills", 50, "damage-bonus", 20, "damage-reduction", 5, "radius", 8))), VOCAB);
        assertEquals(List.of(), set.problems());
        BannerDefinition b = set.banners().get("z");
        assertEquals(new BannerDefinition("z", "Zed", "green",
                List.of(new BannerDefinition.PatternLayer("creeper", "lime")), "zombie", 50, 20, 5, 8), b);
        assertEquals(Set.of("zombie", "husk"), set.families().get("zombie").mobs());
    }

    @Test
    void omittedKeysUseDefaults() {
        BannerSet set = BannerConfigParser.parse(root(
                ordered("zombie", List.of("zombie")), ordered("z", ordered("family", "zombie"))), VOCAB);
        assertEquals(new BannerDefinition("z", "z", "white", List.of(), "zombie", 100, 10, 10, 16),
                set.banners().get("z"));
    }

    @Test
    void mobInTwoFamiliesRejectsTheLaterFamilyAndItsBanners() {
        BannerSet set = BannerConfigParser.parse(root(
                ordered("a", List.of("zombie", "husk"), "b", List.of("husk", "drowned"), "c", List.of("creeper")),
                ordered("ba", ordered("family", "a"), "bb", ordered("family", "b"), "bc", ordered("family", "c"))), VOCAB);
        assertEquals(Set.of("a", "c"), set.families().keySet());
        assertEquals(Set.of("ba", "bc"), set.banners().keySet());
        assertTrue(set.problems().stream().anyMatch(p -> p.contains("husk") && p.contains("already in family a")));
    }

    @Test
    void badEntriesAreSkippedOthersLoad() {
        BannerSet set = BannerConfigParser.parse(root(
                ordered("zombie", List.of("zombie"), "ghost", List.of("ghast_of_nowhere")),
                ordered("ok", ordered("family", "zombie"),
                        "badpattern", ordered("family", "zombie", "patterns", List.of("nope:black")),
                        "nofamily", ordered("name", "x"),
                        "ghostly", ordered("family", "ghost"),
                        "badradius", ordered("family", "zombie", "radius", 0),
                        "badcolour", ordered("family", "zombie", "colour", "plaid"))), VOCAB);
        assertEquals(Set.of("ok"), set.banners().keySet());
        assertFalse(set.families().containsKey("ghost"));
        assertEquals(6, set.problems().size());
        assertTrue(set.problems().stream().anyMatch(p -> p.contains("unknown mob type 'ghast_of_nowhere'")));
        assertTrue(set.problems().stream().anyMatch(p -> p.contains("badpattern") && p.contains("bad pattern 'nope:black'")));
    }

    @Test
    void bannerMissingFromConfigIsInertUntilItsIdReturns() {
        Map<String, Object> families = ordered("zombie", List.of("zombie"));
        BannerSet without = BannerConfigParser.parse(root(families, ordered()), VOCAB);
        assertTrue(without.find("z").isEmpty());
        BannerSet with = BannerConfigParser.parse(root(families, ordered("z", ordered("family", "zombie"))), VOCAB);
        assertTrue(with.find("z").isPresent());
    }

    @Test
    void feedbackDefaultsHaveParticlesAndMarkerOffAndBadValuesFallBack() {
        assertEquals(FeedbackSettings.defaults(), FeedbackSettings.parse(null, VOCAB, new java.util.ArrayList<>()));
        FeedbackSettings d = FeedbackSettings.defaults();
        assertFalse(d.edgeParticles());
        assertFalse(d.markerEffect());
        assertEquals(40, d.numbersMax());
        assertTrue(d.damageNumbers());
        java.util.List<String> problems = new java.util.ArrayList<>();
        FeedbackSettings s = FeedbackSettings.parse(Map.of(
                "marker-effect", Map.of("enabled", true, "effect", "glowing"),
                "edge-particles", Map.of("particle", "nope"),
                "damage-numbers", Map.of("max-active", 0)), VOCAB, problems);
        assertTrue(s.markerEffect());
        assertEquals("glowing", s.effect());
        assertEquals("HAPPY_VILLAGER", s.particle());
        assertEquals(40, s.numbersMax());
        assertEquals(2, problems.size());
    }

    @Test
    void allIsReservedForTheAllMobsCount() {
        BannerSet set = BannerConfigParser.parse(root(
                ordered("all", List.of("zombie"), "zombie", List.of("zombie")),
                ordered("z", ordered("family", "all"), "ok", ordered("family", "zombie"))), VOCAB);
        assertFalse(set.families().containsKey("all"));
        assertEquals(Set.of("ok"), set.banners().keySet());
        assertEquals(2, set.problems().size());
    }

    @Test
    void headsAndRelicsParseAndBadOnesAreSkipped() {
        Map<String, Object> root = new LinkedHashMap<>(root(ordered("zombie", List.of("zombie")), ordered()));
        root.put("heads", ordered(
                "zombie", ordered("family", "zombie", "kills", 250, "name", "Zombie Trophy"),
                "everyone", ordered("family", "all", "kills", 10),
                "nokills", ordered("family", "zombie")));
        root.put("relics", ordered(
                "cleaver", ordered("family", "zombie", "kills", 400, "tier", 1, "slot", "weapon", "item", "iron_axe"),
                "blade", ordered("family", "all", "kills", 5000, "tier", 2, "item", "diamond_sword"),
                "badslot", ordered("family", "zombie", "kills", 1, "slot", "tail"),
                "badtier", ordered("family", "zombie", "kills", 1, "tier", 3)));
        BannerSet set = BannerConfigParser.parse(root, VOCAB);
        assertEquals(Set.of("zombie"), set.heads().keySet());
        assertEquals(Set.of("cleaver", "blade"), set.relics().keySet());
        assertEquals(RelicDefinition.Slot.WEAPON, set.findRelic("blade").orElseThrow().slot());
        assertEquals(4, set.problems().size());
    }

    @Test
    void relicEnchantsAreCheckedAgainstTheServer() {
        Vocabulary vocab = new Vocabulary() {
            public boolean isMob(String n) { return n.equals("zombie"); }
            public boolean isPattern(String n) { return true; }
            public boolean isColour(String n) { return true; }
            public boolean isParticle(String n) { return true; }
            public boolean isEffect(String n) { return true; }
            @Override public boolean isItem(String n) { return n.equals("iron_axe"); }
            @Override public int enchantMax(String k) { return k.equals("minecraft:sharpness") ? 5 : k.equals("minecraft:unbreaking") ? 3 : -1; }
            @Override public boolean isTrimPattern(String n) { return n.equals("wild"); }
            @Override public boolean isTrimMaterial(String n) { return n.equals("emerald"); }
        };
        Map<String, Object> root = new LinkedHashMap<>(root(ordered("zombie", List.of("zombie")), ordered()));
        root.put("relics", ordered(
                "cleaver", ordered("family", "zombie", "kills", 400, "item", "iron_axe", "trim", "wild:gold",
                        "enchants", ordered("sharpness", 2, "unbreaking", 9, "excellentenchants:vampire", 1)),
                "netherite", ordered("family", "zombie", "kills", 400, "item", "netherite_axe")));
        BannerSet set = BannerConfigParser.parse(root, vocab);
        RelicDefinition cleaver = set.findRelic("cleaver").orElseThrow();
        assertEquals(Map.of("minecraft:sharpness", 2, "minecraft:unbreaking", 3), cleaver.enchants());
        assertEquals(30, cleaver.edgePercent());
        assertEquals(null, cleaver.trimPattern());
        assertFalse(set.relics().containsKey("netherite"));
        assertTrue(set.problems().stream().anyMatch(p -> p.contains("excellentenchants:vampire not found")));
        assertTrue(set.problems().stream().anyMatch(p -> p.contains("lowered to its maximum 3")));
        assertTrue(set.problems().stream().anyMatch(p -> p.contains("trim 'wild:gold' ignored")));
        assertTrue(set.problems().stream().anyMatch(p -> p.contains("unknown item 'netherite_axe'")));
    }

    @Test
    void withoutExcellentEnchantsItsEnchantsAreSetAsideNotProblems() {
        Vocabulary noPlugin = new Vocabulary() {
            public boolean isMob(String n) { return n.equals("zombie"); }
            public boolean isPattern(String n) { return true; }
            public boolean isColour(String n) { return true; }
            public boolean isParticle(String n) { return true; }
            public boolean isEffect(String n) { return true; }
            @Override public int enchantMax(String k) { return k.startsWith("minecraft:") ? 5 : -1; }
            @Override public boolean hasExcellentEnchants() { return false; }
        };
        Map<String, Object> root = new LinkedHashMap<>(root(ordered("zombie", List.of("zombie")), ordered()));
        root.put("relics", ordered("cleaver", ordered("family", "zombie", "kills", 400, "item", "iron_axe",
                "enchants", ordered("sharpness", 2, "excellentenchants:vampire", 1, "excellentenchants:rage", 1))));
        BannerSet set = BannerConfigParser.parse(root, noPlugin);
        assertEquals(List.of(), set.problems());
        assertEquals(List.of("excellentenchants:rage", "excellentenchants:vampire"), set.missingOptionalEnchants());
        assertEquals(Map.of("minecraft:sharpness", 2), set.findRelic("cleaver").orElseThrow().enchants());
    }
}
