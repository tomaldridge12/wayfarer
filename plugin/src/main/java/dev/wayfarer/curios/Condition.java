package dev.wayfarer.curios;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * When a carried curio works. Config writes one or a list (all must hold): {@code always}, {@code underground},
 * {@code underwater}, {@code nether}, {@code end}, {@code night}, {@code rain}, {@code near_water},
 * {@code wearing_alloy}, {@code below <y>}, {@code biome #<tag>}, {@code holding <tool>}.
 */
public record Condition(String kind, String argument) {
    public static final Condition ALWAYS = new Condition("always", "");
    static final List<String> PLAIN = List.of("always", "underground", "underwater", "nether", "end", "night", "rain",
            "near_water", "wearing_alloy");
    static final List<String> TOOLS = List.of("pickaxe", "axe", "shovel", "hoe", "sword", "spear", "fishing_rod",
            "trident", "bow", "crossbow", "shears", "brush", "mace");
    /** Below this height with no sky above counts as underground. */
    static final int UNDERGROUND_BELOW = 56;

    /** The condition as written, or empty when it is not one. */
    public static Optional<Condition> parse(String text) {
        String t = text.trim().toLowerCase(Locale.ROOT);
        if (PLAIN.contains(t)) {
            return Optional.of(new Condition(t, ""));
        }
        String[] parts = t.split("\\s+");
        if (parts.length != 2) {
            return Optional.empty();
        }
        return switch (parts[0]) {
            case "below" -> parts[1].matches("-?\\d+") ? Optional.of(new Condition("below", parts[1])) : Optional.empty();
            case "biome" -> parts[1].startsWith("#") && parts[1].length() > 1
                    ? Optional.of(new Condition("biome", parts[1].substring(1))) : Optional.empty();
            case "holding" -> TOOLS.contains(parts[1]) ? Optional.of(new Condition("holding", parts[1])) : Optional.empty();
            default -> Optional.empty();
        };
    }

    public boolean holds(CurioFacts f) {
        return switch (kind) {
            case "always" -> true;
            case "underground" -> f.dimension().equals("overworld") && f.skyHidden() && f.y() < UNDERGROUND_BELOW;
            case "underwater" -> f.underwater();
            case "nether" -> f.dimension().equals("nether");
            case "end" -> f.dimension().equals("end");
            case "night" -> f.dimension().equals("overworld") && f.night();
            case "rain" -> f.raining();
            case "near_water" -> f.nearWater();
            case "wearing_alloy" -> f.wearingAlloy();
            case "below" -> f.y() < Integer.parseInt(argument);
            case "biome" -> f.inBiomeTag(argument);
            case "holding" -> argument.equals(f.heldTool());
            default -> false;
        };
    }

    /** Every condition holds (none listed is always). */
    public static boolean all(List<Condition> conditions, CurioFacts facts) {
        return conditions.stream().allMatch(c -> c.holds(facts));
    }
}
