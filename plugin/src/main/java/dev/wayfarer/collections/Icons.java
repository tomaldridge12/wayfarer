package dev.wayfarer.collections;

import java.util.List;
import java.util.Map;

/**
 * Picks a fitting item to stand for a biome or a structure, by the words in its key (so datapack biomes such as
 * Terralith's get a sensible icon too), and a hint for biomes the config has no line for. No server types.
 */
final class Icons {
    private record Rule(String word, String icon, String hint) {
    }

    /** First match wins, so the more particular words come first. */
    private static final List<Rule> BIOMES = List.of(
            new Rule("deep_dark", "sculk", "Somewhere deep, dark and listening."),
            new Rule("sulfur", "yellow_dye", "Underground, where the air turns yellow."),
            new Rule("lush", "glow_berries", "Underground, where the caves grow green."),
            new Rule("dripstone", "pointed_dripstone", "Underground, among stone teeth."),
            new Rule("cave", "pointed_dripstone", "Somewhere underground."),
            new Rule("mushroom", "red_mushroom", "An island of mushrooms."),
            new Rule("pale", "pale_oak_sapling", "Somewhere grey and very quiet."),
            new Rule("cherry", "cherry_sapling", "Where the petals fall pink."),
            new Rule("mangrove", "mangrove_propagule", "Roots in warm mud."),
            new Rule("swamp", "lily_pad", "Murky water and lily pads."),
            new Rule("bamboo", "bamboo", "A forest of bamboo."),
            new Rule("jungle", "jungle_sapling", "Somewhere hot, thick and green."),
            new Rule("badlands", "red_sand", "Banded red earth."),
            new Rule("mesa", "red_sand", "Banded red earth."),
            new Rule("canyon", "red_sand", "Deep gorges of coloured stone."),
            new Rule("desert", "dead_bush", "Sand and sun."),
            new Rule("dune", "sand", "Sand and sun."),
            new Rule("savanna", "acacia_sapling", "Dry grass and flat-topped trees."),
            new Rule("frozen", "ice", "Somewhere frozen solid."),
            new Rule("ice", "packed_ice", "Somewhere frozen solid."),
            new Rule("glaci", "packed_ice", "Somewhere frozen solid."),
            new Rule("snow", "snowball", "Where the snow lies all year."),
            new Rule("tundra", "snowball", "A cold, bare plain."),
            new Rule("ocean", "kelp", "Out on the open sea."),
            new Rule("sea", "kelp", "Out on the open sea."),
            new Rule("reef", "brain_coral", "Bright water over coral."),
            new Rule("beach", "sand", "Where the land meets the sea."),
            new Rule("shore", "gravel", "Where the land meets the sea."),
            new Rule("coast", "gravel", "Where the land meets the sea."),
            new Rule("river", "water_bucket", "Along fresh running water."),
            new Rule("lake", "water_bucket", "By still fresh water."),
            new Rule("peak", "stone", "High on a mountain."),
            new Rule("mountain", "stone", "High on a mountain."),
            new Rule("alp", "stone", "High in the mountains."),
            new Rule("crag", "stone", "Among broken rock."),
            new Rule("cliff", "stone", "On the edge of a cliff."),
            new Rule("slope", "powder_snow_bucket", "On a mountainside."),
            new Rule("highland", "stone", "Up in the highlands."),
            new Rule("hill", "stone", "Among the hills."),
            new Rule("volcan", "magma_block", "Where the ground runs hot."),
            new Rule("birch", "birch_sapling", "Among pale birches."),
            new Rule("dark_forest", "dark_oak_sapling", "Under a roof of dark leaves."),
            new Rule("taiga", "spruce_sapling", "Among spruce and ferns."),
            new Rule("pine", "spruce_sapling", "Among tall pines."),
            new Rule("spruce", "spruce_sapling", "Among spruce."),
            new Rule("grove", "spruce_sapling", "In a grove of trees."),
            new Rule("forest", "oak_sapling", "In a forest."),
            new Rule("wood", "oak_sapling", "In the woods."),
            new Rule("orchard", "apple", "Among fruit trees."),
            new Rule("meadow", "cornflower", "A high meadow of flowers."),
            new Rule("flower", "poppy", "Among the flowers."),
            new Rule("steppe", "short_grass", "A dry, open grassland."),
            new Rule("plain", "short_grass", "Open grassland."),
            new Rule("prairie", "short_grass", "Open grassland."),
            new Rule("moor", "short_grass", "A windswept moor."),
            new Rule("crimson", "crimson_fungus", "In the Nether, under red fungus."),
            new Rule("warped", "warped_fungus", "In the Nether, under blue fungus."),
            new Rule("soul", "soul_sand", "In the Nether, over soul sand."),
            new Rule("basalt", "basalt", "In the Nether, among black pillars."),
            new Rule("nether", "netherrack", "Somewhere in the Nether."),
            new Rule("end", "end_stone", "Out on the End's islands."));

    private static final Map<String, String> STRUCTURES = Map.ofEntries(
            Map.entry("village", "bell"), Map.entry("abandoned_camp", "campfire"),
            Map.entry("pillager_outpost", "crossbow"), Map.entry("mansion", "totem_of_undying"),
            Map.entry("desert_pyramid", "sandstone"), Map.entry("jungle_pyramid", "mossy_cobblestone"),
            Map.entry("swamp_hut", "cauldron"), Map.entry("igloo", "snow_block"),
            Map.entry("ruined_portal", "crying_obsidian"), Map.entry("ruined_portal_nether", "crying_obsidian"),
            Map.entry("shipwreck", "oak_boat"), Map.entry("ocean_ruin", "prismarine_shard"),
            Map.entry("buried_treasure", "heart_of_the_sea"), Map.entry("monument", "prismarine_bricks"),
            Map.entry("mineshaft", "rail"), Map.entry("stronghold", "ender_eye"),
            Map.entry("ancient_city", "echo_shard"), Map.entry("trail_ruins", "brush"),
            Map.entry("trial_chambers", "trial_key"), Map.entry("fortress", "nether_bricks"),
            Map.entry("bastion_remnant", "gilded_blackstone"), Map.entry("nether_fossil", "bone_block"),
            Map.entry("end_city", "purpur_block"));

    private Icons() {
    }

    private static Rule biomeRule(String key) {
        String path = key.substring(key.indexOf(':') + 1);
        return BIOMES.stream().filter(r -> path.contains(r.word())).findFirst().orElse(null);
    }

    static String biome(String key) {
        Rule r = biomeRule(key);
        return r == null ? "grass_block" : r.icon();
    }

    static String biomeHint(String key) {
        Rule r = biomeRule(key);
        return r == null ? "Somewhere you have not stood yet." : r.hint();
    }

    static String structure(String group) {
        return STRUCTURES.getOrDefault(group.substring(group.indexOf(':') + 1), "map");
    }
}
