package dev.wayfarer.bosses;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleSupplier;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class BossConfigTest {
    private static final BossVocabulary ANY = new BossVocabulary() {
        public boolean isBossEntity(String n) { return !n.equals("armor_stand") && !n.isEmpty(); }
        public boolean isItem(String n) { return !n.equals("not_an_item"); }
        public boolean isGlowColour(String n) { return !n.equals("plaid"); }
        @Override public java.util.Set<String> biomeTag(String key) {
            return key.startsWith("minecraft:") ? java.util.Set.of(key.replace("minecraft:", "minecraft:tagged_")) : null;
        }
    };

    private static BossRoster shipped() throws Exception {
        try (InputStream in = BossConfigTest.class.getResourceAsStream("/bosses.yml")) {
            Map<String, Object> root = new Yaml().load(in);
            return BossConfigParser.parse(root, ANY);
        }
    }

    @Test
    void shippedRosterHasNineBossesAndTwentyThreeVariantsWithoutProblems() throws Exception {
        BossRoster roster = shipped();
        assertEquals(List.of(), roster.problems());
        assertEquals(9, roster.bosses().size());
        assertEquals(23, roster.bosses().values().stream().mapToInt(b -> b.variants().size()).sum());
        roster.bosses().values().forEach(b -> b.variants().forEach(v ->
                assertTrue(v.abilities().size() >= 2, b.id() + "/" + v.id() + " needs two abilities")));
        assertEquals("rotting_warlord", roster.find("rotting_warlord").orElseThrow().family());
        assertEquals(8, roster.bosses().values().stream().filter(b -> b.spawn().chance() > 0).count());
        assertEquals(7, roster.bosses().values().stream().filter(b -> !b.spawn().structures().isEmpty()).count());
        assertTrue(roster.find("blast_prophet").orElseThrow().spawn().anyBiome());
        assertEquals(Set.of("minecraft:desert_pyramid"), roster.find("sand_pharaoh").orElseThrow().spawn().structures());
    }

    @Test
    void starsScaleHealthAndDamage() {
        StarScale s = StarScale.defaults();
        assertEquals(1.0, s.health(1));
        assertEquals(8.0, s.health(5));
        assertEquals(3.0, s.damage(9)); // clamped to five stars
        assertEquals(1.0, s.damage(0));
    }

    @Test
    void variantsArePickedEvenly() {
        BossDefinition boss = new BossDefinition("b", "B", 100, 6, List.of(
                new BossVariant("a", "A", "zombie", "white", 1, Map.of(), List.of(), 0, 0),
                new BossVariant("b", "B", "zombie", "white", 1, Map.of(), List.of(), 0, 0),
                new BossVariant("c", "C", "zombie", "white", 1, Map.of(), List.of(), 0, 0)), 60);
        Deque<Double> rolls = new ArrayDeque<>(List.of(0.0, 0.34, 0.99));
        DoubleSupplier random = rolls::removeFirst;
        assertEquals("a", BossRoster.pickVariant(boss, random).id());
        assertEquals("b", BossRoster.pickVariant(boss, random).id());
        assertEquals("c", BossRoster.pickVariant(boss, random).id());
    }

    @Test
    void badBossesAndVariantsAreSkippedWithMessages() {
        Map<String, Object> root = Map.of("bosses", Map.of(
                "good", Map.of("health", 100, "damage", 6, "variants", Map.of(
                        "ok", Map.of("entity", "zombie", "abilities", List.of("charge", "fly")),
                        "bad", Map.of("entity", "armor_stand"))),
                "nohealth", Map.of("damage", 6, "variants", Map.of("v", Map.of("entity", "zombie"))),
                "novariants", Map.of("health", 1, "damage", 1)),
                "stars", Map.of("health", List.of(1, 2)));
        BossRoster roster = BossConfigParser.parse(root, ANY);
        assertEquals(Set.of("good"), roster.bosses().keySet());
        assertEquals(List.of(Ability.CHARGE), roster.find("good").orElseThrow().variants().getFirst().abilities());
        assertEquals(StarScale.defaults(), roster.stars());
        assertEquals(5, roster.problems().size());
    }

    @Test
    void nameShowsStars() {
        BossVariant v = new BossVariant("p", "Plague Bearer", "zombie", "green", 1.4, Map.of(), List.of(), 0xD6FF8A, 0x5E8C2A);
        assertEquals("Plague Bearer ★★★", PlainTextComponentSerializer.plainText().serialize(BossText.name(v, 3)));
    }

    @Test
    void biomeNamesGetTheirNamespaceAndTagsExpand() {
        Map<String, Object> root = Map.of("bosses", Map.of("b", Map.of("health", 1, "damage", 1,
                "variants", Map.of("v", Map.of("entity", "zombie")),
                "spawn", Map.of("chance", 0.1, "biomes", List.of("plains", "terralith:desert_canyon",
                        "#minecraft:is_forest", "#c:is_cave"), "structures", List.of("desert_pyramid")))));
        BossRoster roster = BossConfigParser.parse(root, ANY);
        var spawn = roster.find("b").orElseThrow().spawn();
        assertEquals(Set.of("minecraft:plains", "terralith:desert_canyon", "minecraft:tagged_is_forest"), spawn.biomes());
        assertEquals(Set.of("minecraft:desert_pyramid"), spawn.structures());
        assertEquals(List.of(), roster.problems());
        assertEquals(List.of("Boss b: biome tag #c:is_cave is not on this server; skipped"), roster.notices());
    }
}
