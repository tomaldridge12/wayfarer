package dev.wayfarer.collections;

import java.util.Set;

/**
 * One Almanac species: where it lives (water, temperatures, an optional special place), when (time of day, weather),
 * how rare it is, the weight range it rolls in grams, and its line of story.
 *
 * @param water fresh, salt, brackish or any
 * @param temps cold, cool, temperate, warm, hot; empty for any
 * @param time day, night or any
 * @param weather rain, thunder or any
 * @param place a place it lives only in (jungle, swamp, desert, mangrove, deep_ocean, deep_dark, pale_garden, lush_caves),
 *     or empty
 */
public record FishSpecies(String id, String name, String rarity, String water, Set<String> temps, String time,
        String weather, String place, long minGrams, long maxGrams, String flavour, String hint) {
    public FishSpecies {
        temps = Set.copyOf(temps);
    }

    public String model() {
        return "fish/" + id;
    }
}
