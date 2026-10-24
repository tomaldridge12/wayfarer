package dev.wayfarer.collections;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** The Almanac's settings and species from collections.yml (almanac section). A bad species is skipped with a problem. */
public final class FishConfigParser {
    public record Settings(double chance, Map<String, Double> rarityWeights) {
    }

    public record Result(Settings settings, List<FishSpecies> species, List<String> problems) {
    }

    private FishConfigParser() {
    }

    public static Result parse(Map<String, Object> almanac) {
        List<String> problems = new ArrayList<>();
        double chance = almanac.get("chance") instanceof Number n ? n.doubleValue() : 0.5;
        if (chance < 0 || chance > 1) {
            problems.add("Almanac chance must be 0 to 1; using 0.5");
            chance = 0.5;
        }
        Map<String, Double> weights = new LinkedHashMap<>(Map.of("common", 60.0, "uncommon", 25.0, "rare", 10.0, "legendary", 2.0));
        CollectionConfigParser.asMap(almanac.get("rarity-weights")).forEach((k, v) -> {
            if (FishRules.RARITIES.contains(k) && v instanceof Number n && n.doubleValue() >= 0) {
                weights.put(k, n.doubleValue());
            } else {
                problems.add("Almanac rarity-weights." + k + " is not a rarity with a weight of 0 or more; ignored");
            }
        });
        List<FishSpecies> out = new ArrayList<>();
        CollectionConfigParser.asMap(almanac.get("species")).forEach((id, value) -> {
            Map<String, Object> m = CollectionConfigParser.asMap(value);
            String where = "Fish " + id + " skipped: ";
            String rarity = text(m.get("rarity"), "common");
            String water = text(m.get("water"), "any");
            String time = text(m.get("time"), "any");
            String weather = text(m.get("weather"), "any");
            String place = text(m.get("place"), "");
            Set<String> temps = new LinkedHashSet<>(CollectionConfigParser.strings(m.get("temp")));
            String weight = String.valueOf(m.get("weight"));
            if (!id.matches("[a-z0-9_]+")) {
                problems.add(where + "ids are lower-case letters, digits and _");
            } else if (!FishRules.RARITIES.contains(rarity)) {
                problems.add(where + "rarity must be common, uncommon, rare or legendary");
            } else if (!FishRules.WATERS.contains(water)) {
                problems.add(where + "water must be fresh, salt, brackish or any");
            } else if (!FishRules.TEMPS.containsAll(temps)) {
                problems.add(where + "temp must list cold, cool, temperate, warm or hot");
            } else if (!Set.of("any", "day", "night").contains(time) || !Set.of("any", "rain", "thunder").contains(weather)) {
                problems.add(where + "time is day, night or any; weather is rain, thunder or any");
            } else if (!place.isEmpty() && !FishRules.PLACES.contains(place)) {
                problems.add(where + "place must be one of " + new java.util.TreeSet<>(FishRules.PLACES));
            } else if (!weight.matches("\\d+-\\d+")) {
                problems.add(where + "weight must be grams as min-max, such as 300-2400");
            } else {
                long min = Long.parseLong(weight.split("-")[0]);
                long max = Long.parseLong(weight.split("-")[1]);
                if (min < 1 || max < min) {
                    problems.add(where + "weight must rise from at least 1 gram");
                } else {
                    out.add(new FishSpecies(id, String.valueOf(m.getOrDefault("name", CollectionRules.title(id))), rarity, water,
                            temps, time, weather, place, min, max, String.valueOf(m.getOrDefault("flavour", "")),
                            m.get("hint") == null ? null : String.valueOf(m.get("hint"))));
                }
            }
        });
        return new Result(new Settings(chance, Map.copyOf(weights)), List.copyOf(out), List.copyOf(problems));
    }

    private static String text(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value).toLowerCase(Locale.ROOT);
    }
}
