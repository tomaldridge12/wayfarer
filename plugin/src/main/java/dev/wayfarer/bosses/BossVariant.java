package dev.wayfarer.bosses;

import java.util.List;
import java.util.Map;

/**
 * One version of a boss: its name and look (entity, glow colour, size, equipment, name gradient) and abilities.
 * {@code equipment} maps a slot (head, chest, legs, feet, hand, offhand) to an item name.
 */
public record BossVariant(String id, String name, String entity, String glow, double scale,
        Map<String, String> equipment, List<Ability> abilities, int colourFrom, int colourTo) {
}
