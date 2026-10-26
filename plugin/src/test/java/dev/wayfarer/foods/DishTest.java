package dev.wayfarer.foods;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** Dishes: how foods.yml reads them, and whether a crafting grid holds the forage and fish a dish asks for. */
class DishTest {
    private static final Set<String> FOODS = Set.of("mushroom_stew", "bread", "cooked_beef");
    private static final Set<String> ITEMS = Set.of("potato", "bowl", "milk_bucket", "wheat", "salmon", "cod", "stick", "mushroom_stew");
    private static final Set<String> EFFECTS = Set.of("haste", "luck", "night_vision");
    private static final Set<String> FORAGE = Set.of("onion", "tomato");
    private static final Set<String> SPECIES = Set.of("northern_pike", "walleye");

    private static final DishConfigParser.Vocabulary VOCAB = new DishConfigParser.Vocabulary() {
        @Override
        public boolean isFoodItem(String name) {
            return FOODS.contains(name);
        }

        @Override
        public boolean isItem(String name) {
            return ITEMS.contains(name);
        }

        @Override
        public boolean isEffect(String name) {
            return EFFECTS.contains(name);
        }

        @Override
        public boolean isForage(String id) {
            return FORAGE.contains(id);
        }

        @Override
        public boolean isFishSpecies(String id) {
            return SPECIES.contains(id);
        }
    };

    private static Map<String, Object> shipped() {
        try (InputStream in = DishTest.class.getResourceAsStream("/foods.yml")) {
            return new Yaml().load(in);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** One dish "x" with these fields over a valid base. */
    private static Map<String, Object> dish(Map<String, Object> over) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", "X");
        m.put("base", "bread");
        m.put("nutrition", 8);
        m.put("saturation", 10.0);
        m.put("effects", List.of(Map.of("type", "haste", "minutes", 5)));
        m.put("ingredients", List.of("potato"));
        m.putAll(over);
        return Map.of("dishes", Map.of("items", Map.of("x", m)));
    }

    private static DishConfigParser.Result parse(Map<String, Object> over) {
        return DishConfigParser.parse(dish(over), VOCAB);
    }

    @Test
    void theShippedDishesReadWithNoProblemsAgainstTheShippedForage() {
        Set<String> forage = new java.util.HashSet<>();
        FoodsConfigParser.parse(shipped(), k -> true, k -> true).forage().forEach(f -> forage.add(f.id()));
        DishConfigParser.Vocabulary real = new DishConfigParser.Vocabulary() {
            public boolean isFoodItem(String name) {
                return true;
            }

            public boolean isItem(String name) {
                return true;
            }

            public boolean isEffect(String name) {
                return true;
            }

            public boolean isForage(String id) {
                return forage.contains(id);
            }

            public boolean isFishSpecies(String id) {
                return true;
            }
        };
        DishConfigParser.Result r = DishConfigParser.parse(shipped(), real);
        assertEquals(List.of(), r.problems());
        assertEquals(27, r.dishes().size());
        assertEquals(r.dishes().size(), r.dishes().stream().map(DishDefinition::id).distinct().count());
        DishDefinition stew = r.dishes().getFirst();
        assertEquals("miners_stew", stew.id());
        assertEquals(List.of(new DishDefinition.Effect("haste", 0, 480)), stew.effects());
        assertEquals(4, stew.recipe().slots());
        // Four families, as the spec lists them
        assertEquals(Map.of("mining", 4L, "fishing", 7L, "travel", 7L, "farming", 9L), r.dishes().stream()
                .collect(java.util.stream.Collectors.groupingBy(DishDefinition::family, java.util.stream.Collectors.counting())));
        // No two dishes may be made from the same set of ingredients: the table could not tell them apart
        long distinct = r.dishes().stream().map(d -> d.recipe().ingredients().stream()
                .map(i -> i.kind() + ":" + i.what() + "*" + i.count()).sorted().toList()).distinct().count();
        assertEquals(r.dishes().size(), distinct);
        // Only rare dishes go above level I (a rare dish may still be level I, like the Harvest Feast)
        for (DishDefinition d : r.dishes()) {
            boolean strong = d.effects().stream().anyMatch(e -> e.amplifier() > r.maxAmplifier());
            assertTrue(!strong || d.rare(), d.id());
        }
        assertEquals(List.of("caviar_toast", "hellfire_skewer", "chorus_dumplings", "truffle_risotto", "harvest_feast"),
                r.dishes().stream().filter(DishDefinition::rare).map(DishDefinition::id).toList());
    }

    @Test
    void aDishReadsItsEffectsAndIngredients() {
        DishConfigParser.Result r = parse(Map.of("ingredients", List.of("wheat*2", "meat:cooked", "forage:onion", "fish:fresh", "fish:northern_pike")));
        assertEquals(List.of(), r.problems());
        DishDefinition d = r.dishes().getFirst();
        assertEquals(List.of(
                new DishDefinition.Ingredient(DishDefinition.Kind.ITEM, "wheat", 2),
                new DishDefinition.Ingredient(DishDefinition.Kind.MEAT, "cooked", 1),
                new DishDefinition.Ingredient(DishDefinition.Kind.FORAGE, "onion", 1),
                new DishDefinition.Ingredient(DishDefinition.Kind.FISH, "fresh", 1),
                new DishDefinition.Ingredient(DishDefinition.Kind.FISH, "northern_pike", 1)), d.recipe().ingredients());
        assertEquals("crafting", d.recipe().type());
        assertEquals(6, d.recipe().slots());
    }

    @Test
    void aDishWithAMissingBaseOrBadEffectOrIngredientIsSkippedWithAProblemLine() {
        for (Map<String, Object> bad : List.of(
                Map.<String, Object>of("base", "dirt"),
                Map.<String, Object>of("name", ""),
                Map.<String, Object>of("effects", List.of(Map.of("type", "sparkle", "minutes", 5))),
                Map.<String, Object>of("effects", List.of(Map.of("type", "haste", "minutes", 0))),
                Map.<String, Object>of("ingredients", List.of("diamond_dust")),
                Map.<String, Object>of("ingredients", List.of("forage:truffle")),
                Map.<String, Object>of("ingredients", List.of("fish:unicorn")),
                Map.<String, Object>of("ingredients", List.of("potato*0")),
                Map.<String, Object>of("ingredients", List.of("potato*10")),
                Map.<String, Object>of("ingredients", List.of("potato*5", "wheat*5")),
                Map.<String, Object>of("ingredients", List.<String>of()),
                Map.<String, Object>of("ingredients", List.of("fish:any", "salmon")))) {
            DishConfigParser.Result r = parse(bad);
            assertEquals(List.of(), r.dishes(), bad.toString());
            assertFalse(r.problems().isEmpty(), bad.toString());
        }
    }

    @Test
    void anEffectAboveTheCapIsClampedUnlessTheDishIsRare() {
        Map<String, Object> strong = Map.of("effects", List.of(Map.of("type", "luck", "amplifier", 1, "minutes", 5)));
        DishConfigParser.Result plain = parse(strong);
        assertEquals(1, plain.problems().size());
        assertEquals(0, plain.dishes().getFirst().effects().getFirst().amplifier());
        Map<String, Object> rare = new LinkedHashMap<>(strong);
        rare.put("rare", true);
        DishConfigParser.Result r = parse(rare);
        assertEquals(List.of(), r.problems());
        assertEquals(1, r.dishes().getFirst().effects().getFirst().amplifier());
        assertTrue(r.dishes().getFirst().rare());
    }

    @Test
    void aSmokerOrCampfireRecipeTakesExactlyOnePlainItem() {
        DishConfigParser.Result ok = parse(Map.of("recipe", "smoker", "ingredients", List.of("potato"), "seconds", 7));
        assertEquals(List.of(), ok.problems());
        assertEquals(7, ok.dishes().getFirst().recipe().seconds());
        assertEquals(30, parse(Map.of("recipe", "campfire")).dishes().getFirst().recipe().seconds(), "campfires cook slowly by default");
        for (List<String> bad : List.of(List.of("potato", "wheat"), List.of("potato*2"), List.of("forage:onion"), List.of("fish:any"))) {
            assertEquals(List.of(), parse(Map.of("recipe", "smoker", "ingredients", bad)).dishes(), bad.toString());
        }
        assertEquals(List.of(), parse(Map.of("recipe", "oven")).dishes());
    }

    @Test
    void theForageInTheGridMustBeExactlyWhatTheRecipeNames() {
        assertTrue(DishMatching.forage(List.of("onion", "tomato"), List.of("tomato", "onion")));
        assertTrue(DishMatching.forage(List.of(), List.of()));
        assertFalse(DishMatching.forage(List.of("onion"), List.of("tomato")), "wrong forage");
        assertFalse(DishMatching.forage(List.of("onion"), List.of()), "missing forage");
        assertFalse(DishMatching.forage(List.of("onion"), List.of("onion", "tomato")), "extra forage");
        assertFalse(DishMatching.forage(List.of("onion", "onion"), List.of("onion")));
    }

    @Test
    void fishMatchByWaterOrSpeciesAndVanillaFishOnlyForAny() {
        DishMatching.Held pike = new DishMatching.Held("northern_pike", "fresh");
        DishMatching.Held tuna = new DishMatching.Held("tuna", "salt");
        DishMatching.Held killifish = new DishMatching.Held("killifish", "brackish");
        DishMatching.Held vanilla = new DishMatching.Held(null, null);
        assertTrue(DishMatching.fish(List.of("any"), List.of(pike)));
        assertTrue(DishMatching.fish(List.of("any"), List.of(vanilla)), "a vanilla fish suits any");
        assertFalse(DishMatching.fish(List.of("fresh"), List.of(vanilla)), "but not a water");
        assertFalse(DishMatching.fish(List.of("northern_pike"), List.of(vanilla)));
        assertTrue(DishMatching.fish(List.of("fresh"), List.of(pike)));
        assertFalse(DishMatching.fish(List.of("fresh"), List.of(tuna)), "salt water is not fresh");
        assertTrue(DishMatching.fish(List.of("salt"), List.of(tuna)));
        assertTrue(DishMatching.fish(List.of("northern_pike"), List.of(pike)));
        assertFalse(DishMatching.fish(List.of("northern_pike"), List.of(new DishMatching.Held("walleye", "fresh"))));
        assertTrue(DishMatching.fish(List.of("fresh"), List.of(killifish)) && DishMatching.fish(List.of("salt"), List.of(killifish)),
                "a brackish fish suits both");
    }

    @Test
    void everyNeedGetsItsOwnFishAndNoneIsLeftOver() {
        DishMatching.Held pike = new DishMatching.Held("northern_pike", "fresh");
        DishMatching.Held killifish = new DishMatching.Held("killifish", "brackish");
        assertTrue(DishMatching.fish(List.of("salt", "fresh"), List.of(pike, killifish)),
                "the brackish fish takes the salt need so the pike can take the fresh one");
        assertTrue(DishMatching.fish(List.of("fresh", "salt"), List.of(killifish, pike)));
        assertFalse(DishMatching.fish(List.of("salt", "salt"), List.of(pike, killifish)));
        assertFalse(DishMatching.fish(List.of("any"), List.of(pike, killifish)), "an extra fish in the grid");
        assertFalse(DishMatching.fish(List.of("any", "any"), List.of(pike)), "a missing fish");
        assertTrue(DishMatching.fish(List.of(), List.of()));
    }
}
