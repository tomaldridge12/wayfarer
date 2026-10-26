package dev.wayfarer.foods;

import java.util.List;
import java.util.Set;

/**
 * One forage ingredient: where it is found and how often. {@code biomes} entries are biome keys without the minecraft:
 * namespace, or {@code #tag} for a biome tag (which includes datapack biomes such as Terralith's); {@code plants} are
 * block keys without the namespace.
 */
record ForageDefinition(String id, String name, int colour, List<String> description, String flavour, List<String> biomes,
        Set<String> plants, double chance, int pity) {
    /** The item model in the resource pack, {@code wayfarer:forage/<id>}. */
    String model() {
        return "forage/" + id;
    }
}
