package dev.wayfarer.objectives;

import java.util.Map;

/**
 * A weekend event: its name and one-line pitch, the gameplay modifier it switches on (with its settings), its own
 * server goal, and the reward table every contributor rolls from when the goal is met.
 *
 * @param modifier double_catch, crop_growth, ore_xp or mob_drops
 */
public record EventDefinition(String id, String name, String blurb, String modifier, Map<String, Object> settings,
        ObjectiveDefinition goal, String table) {
    public EventDefinition {
        settings = Map.copyOf(settings);
    }

    public double number(String key, double fallback) {
        return settings.get(key) instanceof Number n ? n.doubleValue() : fallback;
    }
}
