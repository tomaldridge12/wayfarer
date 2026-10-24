package dev.wayfarer.curios;

import java.util.Map;

/**
 * A special gear preset: named gear on an alloy item ({@code alloy:bronze_hoe}, through core's item makers) or a
 * vanilla one, with fixed enchants set when it is given (ADR 0002) and a line of story. {@code fallback} is the vanilla
 * item used when no module makes the base. {@code model} overrides the base's look (vanilla bases only).
 */
public record GearPreset(String id, String name, int colour, String flavour, String base, String fallback,
        Map<String, Integer> enchants, boolean unbreakable, String model) {
    public GearPreset {
        enchants = Map.copyOf(enchants);
    }

    public boolean onAlloy() {
        return base.startsWith("alloy:");
    }
}
