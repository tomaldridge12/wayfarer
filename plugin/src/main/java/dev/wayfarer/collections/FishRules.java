package dev.wayfarer.collections;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * The Almanac's rules: which species could bite here and now, a weighted pick by rarity (bait doubles the rare
 * odds), the weight a fish rolls, and personal records. No server types.
 */
public final class FishRules {
    public static final List<String> RARITIES = List.of("common", "uncommon", "rare", "legendary");
    public static final Set<String> TEMPS = Set.of("cold", "cool", "temperate", "warm", "hot");
    public static final Set<String> WATERS = Set.of("fresh", "salt", "brackish", "any");
    public static final Set<String> PLACES = Set.of("jungle", "swamp", "desert", "mangrove", "deep_ocean", "deep_dark",
            "pale_garden", "lush_caves");

    /** Where and when a cast landed: the water's kind and climate, day or night, weather and special places around. */
    public record Facts(String water, String temp, boolean day, boolean rain, boolean thunder, Set<String> places) {
    }

    private FishRules() {
    }

    public static boolean eligible(FishSpecies s, Facts f) {
        if (!s.water().equals("any") && !s.water().equals(f.water())) {
            return false;
        }
        if (!s.temps().isEmpty() && !s.temps().contains(f.temp())) {
            return false;
        }
        if (s.time().equals("day") && !f.day() || s.time().equals("night") && f.day()) {
            return false;
        }
        if (s.weather().equals("rain") && !(f.rain() || f.thunder()) || s.weather().equals("thunder") && !f.thunder()) {
            return false;
        }
        return s.place().isEmpty() || f.places().contains(s.place());
    }

    /** Rare and legendary weights are multiplied by the bait factor (2 when baited). */
    public static double weight(FishSpecies s, Map<String, Double> rarityWeights, double bait) {
        double w = rarityWeights.getOrDefault(s.rarity(), 0.0);
        return s.rarity().equals("rare") || s.rarity().equals("legendary") ? w * bait : w;
    }

    /** A weighted pick among the species that could bite here; null when none can. */
    public static FishSpecies pick(List<FishSpecies> all, Facts facts, Map<String, Double> rarityWeights, double bait,
            RandomGenerator random) {
        List<FishSpecies> eligible = new ArrayList<>();
        double total = 0;
        for (FishSpecies s : all) {
            if (eligible(s, facts)) {
                eligible.add(s);
                total += weight(s, rarityWeights, bait);
            }
        }
        if (total <= 0) {
            return null;
        }
        double roll = random.nextDouble() * total;
        for (FishSpecies s : eligible) {
            roll -= weight(s, rarityWeights, bait);
            if (roll < 0) {
                return s;
            }
        }
        return eligible.getLast();
    }

    /** A weight in the species' range, heavier fish rarer than light ones. */
    public static long grams(FishSpecies s, double roll) {
        double skew = Math.pow(Math.max(0, Math.min(1, roll)), 1.8);
        return s.minGrams() + Math.round((s.maxGrams() - s.minGrams()) * skew);
    }

    /** Whether a catch beats the record (0 when there is none). */
    public static boolean isRecord(long grams, long record) {
        return grams > record;
    }

    /** "850 g" below a kilo, "2.41 kg" above, "112 kg" above a hundred. */
    public static String weight(long grams) {
        if (grams < 1000) {
            return grams + " g";
        }
        double kg = grams / 1000.0;
        return kg >= 100 ? Math.round(kg) + " kg" : String.format(java.util.Locale.ROOT, "%.2f kg", kg);
    }

    /** "Cold or cool fresh water, at night, in the rain", from a species' facts, for a hint. */
    public static String where(FishSpecies s) {
        StringBuilder out = new StringBuilder();
        if (!s.place().isEmpty()) {
            out.append(switch (s.place()) {
                case "deep_dark" -> "Any water in the deep dark";
                case "pale_garden" -> "Water in the pale garden";
                case "lush_caves" -> "The pools of lush caves";
                case "deep_ocean" -> "Far out over the deep ocean";
                case "mangrove" -> "Among mangrove roots";
                default -> CollectionRules.title(s.place()) + " water";
            });
        } else {
            List<String> temps = TEMPS.stream().filter(s.temps()::contains)
                    .sorted(java.util.Comparator.comparingInt(List.of("cold", "cool", "temperate", "warm", "hot")::indexOf)).toList();
            String climate = temps.isEmpty() ? "" : String.join(" or ", temps) + " ";
            out.append(switch (s.water()) {
                case "fresh" -> capital(climate + "fresh water");
                case "salt" -> capital(climate + "seas");
                case "brackish" -> capital(climate + "brackish water, where rivers meet the sea");
                default -> capital(climate.isEmpty() ? "Any water" : climate + "water");
            });
        }
        if (s.time().equals("night")) {
            out.append(", at night");
        } else if (s.time().equals("day")) {
            out.append(", by day");
        }
        if (s.weather().equals("rain")) {
            out.append(", in the rain");
        } else if (s.weather().equals("thunder")) {
            out.append(", in a thunderstorm");
        }
        return out.toString();
    }

    private static String capital(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
