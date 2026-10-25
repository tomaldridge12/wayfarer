package dev.wayfarer.collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The Atlas hint lookup the Wayfinder uses for biomes a player has not found. */
class AtlasHintBookTest {
    private static CollectionEntry biome(String target, String hint) {
        return new CollectionEntry("biome/" + target, "discover:biome/" + target, target, "grass_block", hint, "Overworld", "visited");
    }

    private final AtlasHintBook book = new AtlasHintBook(List.of(
            biome("plains", "Wide open grass and the odd village."),
            biome("terralith:alpine_grove", "Snow-dusted pines."),
            new CollectionEntry("structure/igloo", "discover:structure/igloo", "Igloo", "snow_block", "Cold and domed.", "Overworld", "found")));

    @Test
    void aListedBiomeGivesItsHintWhateverWayItIsSpelled() {
        assertEquals("Wide open grass and the odd village.", book.hint("plains"));
        assertEquals("Wide open grass and the odd village.", book.hint("minecraft:plains"));
        assertEquals("Snow-dusted pines.", book.hint("terralith:alpine_grove"));
    }

    @Test
    void unknownBiomesAndOtherEntriesGiveNothing() {
        assertEquals("", book.hint("not_a_biome"));
        assertEquals("", book.hint("igloo"));
    }
}
