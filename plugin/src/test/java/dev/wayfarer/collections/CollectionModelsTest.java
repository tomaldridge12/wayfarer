package dev.wayfarer.collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Every fish and treasure the shipped config names, the Journal and the unfound shape have their model in the pack. */
class CollectionModelsTest {
    private static final Path PACK = Path.of(System.getProperty("wayfarer.pack.dir", "../resourcepack"), "assets/wayfarer");

    @Test
    void everyFishTreasureAndJournalModelExists() {
        var collections = CollectionConfigParser.parse(CollectionsTest.shipped()).collections();
        List<String> models = new ArrayList<>(List.of("journal", "journal/unknown"));
        FishConfigParser.parse(collections.get("almanac").settings()).species().forEach(s -> models.add(s.model()));
        TreasureConfigParser.parse(collections.get("treasures").settings()).treasures().forEach(t -> models.add(t.model()));
        List<String> missing = models.stream().map(m -> "items/" + m + ".json").filter(p -> !Files.exists(PACK.resolve(p))).toList();
        assertEquals(List.of(), missing);
    }
}
