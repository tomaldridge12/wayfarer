package dev.wayfarer.foods;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** The feats, dailies, tables and Cookbook that mention dishes and forage name ones that exist, so a typo cannot hide. */
class FoodsObjectivesTest {
    private static String text(String resource) {
        try (InputStream in = FoodsObjectivesTest.class.getResourceAsStream(resource)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Set<String> keys(String section, String key) {
        Map<String, Object> root = new Yaml().load(text("/foods.yml"));
        return new LinkedHashSet<>(((Map<String, Object>) ((Map<String, Object>) root.get(section)).get(key)).keySet());
    }

    private static Set<String> found(String source, String regex) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = Pattern.compile(regex).matcher(source);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    @Test
    void everyDishAndForageIdThatConfigNamesExists() {
        Set<String> dishes = keys("dishes", "items");
        Set<String> forage = keys("forage", "ingredients");
        String objectives = text("/objectives.yml");
        String collections = text("/collections.yml");

        Set<String> eaten = found(objectives, "eat:wayfarer:dish/([a-z_]+)");
        eaten.removeIf(id -> id.isEmpty());
        assertTrue(dishes.containsAll(eaten), "feats count dishes that do not exist: " + eaten);

        Set<String> foraged = found(objectives, "forage:([a-z_]+)");
        assertTrue(forage.containsAll(foraged), "feats count forage that does not exist: " + foraged);

        Set<String> paid = found(objectives + collections, "\"?dish:([a-z_]+)(?:\\*\\d+)?\"?");
        assertTrue(!paid.isEmpty(), "some reward pays a dish");
        assertTrue(dishes.containsAll(paid), "rewards pay dishes that do not exist: " + paid);
    }

    @Test
    void theFeatsCoverEveryFamilyAndEveryIngredient() {
        String objectives = text("/objectives.yml");
        for (String family : List.of("stew_enthusiast", "chowder_head", "trail_cook", "pit_cook", "farm_to_table", "head_chef", "forager",
                "hedgerow_hand", "spice_trader", "truffle_hound", "bottomless")) {
            assertTrue(objectives.contains("\n  " + family + ":\n    name:"), "feat " + family);
            assertTrue(objectives.contains("cosmetic:" + family), "cosmetic " + family);
        }
        Set<String> forage = keys("forage", "ingredients");
        String forager = objectives.substring(objectives.indexOf("\n  forager:\n    name:"));
        forager = forager.substring(0, forager.indexOf("rewards:"));
        assertEquals(forage, found(forager, "forage:([a-z_]+)"), "the Forager feat asks for every ingredient");
    }

    @Test
    void theFamilyFeatsNameOnlyDishesOfThatFamily() {
        Map<String, Object> root = new Yaml().load(text("/foods.yml"));
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> dishes = (Map<String, Map<String, Object>>) ((Map<String, Object>) root.get("dishes")).get("items");
        String objectives = text("/objectives.yml");
        Map<String, String> families = Map.of("chowder_head", "fishing", "trail_cook", "travel", "pit_cook", "mining", "farm_to_table", "farming");
        families.forEach((feat, family) -> {
            String block = objectives.substring(objectives.indexOf("\n  " + feat + ":\n    name:"));
            block = block.substring(0, block.indexOf("rewards:"));
            Set<String> ids = found(block, "eat:wayfarer:dish/([a-z_]+)");
            Set<String> expected = new LinkedHashSet<>();
            dishes.forEach((id, d) -> {
                if (family.equals(d.get("family"))) {
                    expected.add(id);
                }
            });
            // fishers_chowder and miners_stew count towards both a family and the stew feat; each family feat lists its own
            assertEquals(expected, ids, feat);
        });
    }
}
