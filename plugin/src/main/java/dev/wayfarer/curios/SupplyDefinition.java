package dev.wayfarer.curios;

import java.util.List;

/**
 * A supply: consumable reward loot opened by right-click. {@code opens} is what happens: {@code loot} (rolls from a
 * weighted list), {@code crate} (pick a baby animal from {@code choices}, written {@code cow/cold} or
 * {@code sheep/black}), {@code bait} (the next {@code amount} casts double the Almanac's rare odds) or {@code xp}
 * ({@code amount} experience points).
 */
public record SupplyDefinition(String id, String name, int colour, List<String> description, String flavour, String opens,
        int rolls, List<SupplyRules.Entry> loot, List<String> choices, int amount) {
    public SupplyDefinition {
        description = List.copyOf(description);
        loot = List.copyOf(loot);
        choices = List.copyOf(choices);
    }

    public String model() {
        return "supply/" + id;
    }
}
