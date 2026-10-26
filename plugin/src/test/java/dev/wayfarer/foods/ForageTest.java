package dev.wayfarer.foods;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** Forage: the roll and its pity ceiling, and how foods.yml reads. */
class ForageTest {
    private static Map<String, Object> shipped() {
        try (InputStream in = ForageTest.class.getResourceAsStream("/foods.yml")) {
            return new Yaml().load(in);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, Object> ingredient(Map<String, Object> over) {
        Map<String, Object> m = new java.util.LinkedHashMap<>(Map.of("name", "Tomato", "biomes", List.of("plains"), "plants", List.of("short_grass")));
        m.putAll(over);
        return Map.of("forage", Map.of("ingredients", Map.of("tomato", m)));
    }

    @Test
    void aLowRollGivesTheIngredientAndAHighOneDoesNot() {
        assertTrue(ForageRules.drops(0, 40, 0.04, 0.039));
        assertFalse(ForageRules.drops(0, 40, 0.04, 0.04));
        assertFalse(ForageRules.drops(39, 40, 0.04, 0.99), "one short of the ceiling");
    }

    @Test
    void aStreakAtTheCeilingIsCertainAndAnyDropStartsItAgain() {
        assertTrue(ForageRules.drops(40, 40, 0.04, 0.99));
        assertTrue(ForageRules.drops(500, 40, 0.0, 0.99));
        assertEquals(0, ForageRules.nextStreak(40, true));
        assertEquals(0, ForageRules.nextStreak(3, true), "a lucky drop resets the streak too");
        assertEquals(4, ForageRules.nextStreak(3, false));
    }

    @Test
    void anEventMultiplierRaisesTheChanceButNeverAboveCertain() {
        assertEquals(0.08, ForageRules.chance(0.04, 2.0), 1e-9);
        assertEquals(0.04, ForageRules.chance(0.04, 1.0), 1e-9);
        assertEquals(1.0, ForageRules.chance(0.6, 2.0), 1e-9);
        assertEquals(0.0, ForageRules.chance(0.04, -3.0), 1e-9);
    }

    @Test
    void aPlantGivesOnlyTheIngredientsListingIt() {
        var rice = new ForageDefinition("wild_rice", "Wild Rice", 0, List.of(), "", List.of("swamp"), Set.of("tall_grass", "seagrass"), 0.04, 40);
        var tomato = new ForageDefinition("tomato", "Tomato", 0, List.of(), "", List.of("plains"), Set.of("short_grass", "tall_grass"), 0.04, 40);
        assertEquals(List.of(rice, tomato), ForageRules.forPlant(List.of(rice, tomato), "tall_grass"));
        assertEquals(List.of(tomato), ForageRules.forPlant(List.of(rice, tomato), "short_grass"));
        assertEquals(List.of(), ForageRules.forPlant(List.of(rice, tomato), "stone"));
    }

    @Test
    void theShippedFileReadsAllTenIngredientsWithNoProblems() {
        FoodsConfigParser.Result r = FoodsConfigParser.parse(shipped(), key -> true, key -> true);
        assertEquals(List.of(), r.problems());
        assertEquals(List.of("wild_rice", "tomato", "onion", "cabbage", "wild_herbs", "chilli", "samphire", "mountain_garlic", "truffle",
                "peppercorn"), r.forage().stream().map(ForageDefinition::id).toList());
        ForageDefinition truffle = r.forage().stream().filter(d -> d.id().equals("truffle")).findFirst().orElseThrow();
        assertEquals(0.02, truffle.chance(), 1e-9);
        assertEquals(80, truffle.pity());
        ForageDefinition tomato = r.forage().stream().filter(d -> d.id().equals("tomato")).findFirst().orElseThrow();
        assertEquals(0.04, tomato.chance(), 1e-9, "the shared default");
        assertEquals(40, tomato.pity());
        assertTrue(tomato.biomes().contains("#is_savanna"), "the minecraft: namespace is dropped");
    }

    @Test
    void aBadChancePityOrColourFallsBackWithAProblemLine() {
        for (Map<String, Object> bad : List.of(Map.<String, Object>of("chance", 0), Map.<String, Object>of("chance", 1.5),
                Map.<String, Object>of("pity", 0), Map.<String, Object>of("pity", "lots"), Map.<String, Object>of("colour", "green"))) {
            FoodsConfigParser.Result r = FoodsConfigParser.parse(ingredient(bad), key -> true, key -> true);
            assertEquals(1, r.problems().size(), bad.toString());
            assertEquals(1, r.forage().size(), "still loaded with the default");
        }
        ForageDefinition d = FoodsConfigParser.parse(ingredient(Map.of("chance", 0)), key -> true, key -> true).forage().getFirst();
        assertEquals(FoodsConfigParser.DEFAULT_CHANCE, d.chance(), 1e-9);
    }

    @Test
    void anIngredientWithNothingUsableIsSkipped() {
        FoodsConfigParser.Result noBiome = FoodsConfigParser.parse(ingredient(Map.of()), key -> true, key -> false);
        assertEquals(List.of(), noBiome.forage());
        assertEquals(2, noBiome.problems().size(), "the unknown biome, then the skip");
        FoodsConfigParser.Result noPlant = FoodsConfigParser.parse(ingredient(Map.of()), key -> false, key -> true);
        assertEquals(List.of(), noPlant.forage());
        FoodsConfigParser.Result noName = FoodsConfigParser.parse(ingredient(Map.of("name", " ")), key -> true, key -> true);
        assertEquals(List.of(), noName.forage());
        assertTrue(noName.problems().getFirst().contains("no name"));
    }

    @Test
    void aNamespaceOnABiomeOrPlantIsDroppedForVanillaKeys() {
        FoodsConfigParser.Result r = FoodsConfigParser.parse(
                ingredient(Map.of("biomes", List.of("minecraft:plains", "#minecraft:is_forest", "terralith:alpine_grove"),
                        "plants", List.of("minecraft:short_grass"))), key -> true, key -> true);
        ForageDefinition d = r.forage().getFirst();
        assertEquals(List.of("plains", "#is_forest", "terralith:alpine_grove"), d.biomes(),
                "vanilla keys lose the namespace; datapack keys keep theirs");
        assertEquals(Set.of("short_grass"), d.plants());
    }
}
