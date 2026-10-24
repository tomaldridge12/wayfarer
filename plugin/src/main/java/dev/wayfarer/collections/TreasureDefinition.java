package dev.wayfarer.collections;

import java.util.List;
import java.util.Map;

/**
 * One treasure: its name, colour, kind and story, the brushing sites it turns up at, the structure chests it can be in
 * (loot table key, without namespace, to a chance per chest), the structure a chart points to ({@code null} for
 * anything else) and its weight among the treasures of a site.
 */
public record TreasureDefinition(String id, String name, int colour, String kind, String flavour, List<String> brush,
        Map<String, Double> chests, String chart, int weight) {
    public TreasureDefinition {
        brush = List.copyOf(brush);
        chests = Map.copyOf(chests);
    }

    public String model() {
        return "treasure/" + id;
    }
}
