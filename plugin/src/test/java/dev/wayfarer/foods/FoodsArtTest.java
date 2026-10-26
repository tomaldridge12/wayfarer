package dev.wayfarer.foods;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** Every forage ingredient and dish in foods.yml has its item definition, model and credited texture in the pack source. */
class FoodsArtTest {
    private static final Path SOURCE = Path.of(System.getProperty("wayfarer.pack.dir", "../resourcepack"));

    @SuppressWarnings("unchecked")
    private static List<String> ids(String section, String key) {
        try (InputStream in = FoodsArtTest.class.getResourceAsStream("/foods.yml")) {
            Map<String, Object> root = new Yaml().load(in);
            Map<String, Object> s = (Map<String, Object>) root.get(section);
            return new ArrayList<>(((Map<String, Object>) s.get(key)).keySet());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> missing(String kind, List<String> ids) {
        List<String> problems = new ArrayList<>();
        String credits = read(SOURCE.resolve("CREDITS.md"));
        for (String id : ids) {
            for (String file : List.of("assets/wayfarer/items/" + kind + "/" + id + ".json",
                    "assets/wayfarer/models/item/" + kind + "/" + id + ".json",
                    "assets/wayfarer/textures/item/" + kind + "/" + id + ".png")) {
                if (!Files.exists(SOURCE.resolve(file))) {
                    problems.add(file + " is missing");
                } else if (file.endsWith(".png") && !credits.contains("`" + file + "`")) {
                    problems.add(file + " is not credited");
                }
            }
        }
        return problems;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void everyDishHasItsArt() {
        List<String> ids = ids("dishes", "items");
        assertEquals(27, ids.size());
        assertEquals(List.of(), missing("dish", ids));
    }

    @Test
    void everyForageIngredientHasItsArt() {
        List<String> ids = ids("forage", "ingredients");
        assertEquals(10, ids.size());
        assertEquals(List.of(), missing("forage", ids));
    }

    @Test
    void theVectorwingLicenceIsKeptWithTheArtItCovers() {
        String credits = read(SOURCE.resolve("CREDITS.md"));
        assertTrue(credits.contains("Copyright (c) 2020 vectorwing"));
        assertTrue(credits.contains("Permission is hereby granted, free of charge"));
        assertTrue(credits.contains("The above copyright notice and this permission notice shall be included in all"));
        assertTrue(credits.contains("`assets/wayfarer/textures/item/dish/miners_stew.png`"));
    }
}
