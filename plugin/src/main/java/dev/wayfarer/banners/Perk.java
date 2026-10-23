package dev.wayfarer.banners;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * A relic perk: one type from the fixed library below, with its settings. {@code family} is set for perks aimed
 * at one mob family; {@code amount} is the perk's strength (a chance, percent, block count or multiplier), used
 * both for its effect and to pick the strongest when two relics give the same perk. No server types.
 */
public record Perk(Type type, String family, double amount) {
    /** The perk library. Each type says which settings it needs and how strong it is by default. */
    public enum Type {
        HUNGER_IMMUNITY(false, 1, "Immune to Hunger"),
        POISON_IMMUNITY(false, 1, "Immune to Poison"),
        NO_KNOCKBACK(true, 1, "No knockback from the %s family"),
        ARROW_SAVER(false, 0.3, "%.0f%% chance an arrow is not used up"),
        PROJECTILE_WARD(true, 30, "−%.0f%% damage from %s arrows"),
        COBWEB_WALKER(false, 1, "Cobwebs dissolve as you walk into them"),
        FALL_CUSHION(false, 5, "No fall damage from falls of %.0f blocks or less"),
        KNOCK_AWAY(true, 1.5, "Hits knock the %s family far back"),
        EXPLOSION_WARD(false, 50, "−%.0f%% explosion damage"),
        ENDERMAN_CALM(false, 1, "Looking at endermen does not anger them"),
        PEARL_MASTERY(false, 50, "Ender pearls do no damage; %.0f%% shorter cooldown"),
        KILL_HEAL(false, 1, "Each kill heals %.0f ❤"),
        LIGHTNING_STRIKE(false, 0.15, "%.0f%% chance to call lightning on hit");

        final boolean needsFamily;
        final double defaultAmount;
        final String text;

        Type(boolean needsFamily, double defaultAmount, String text) {
            this.needsFamily = needsFamily;
            this.defaultAmount = defaultAmount;
            this.text = text;
        }
    }

    /** The perk's line in a relic tooltip. Chances are shown as percents. */
    public String describe() {
        return switch (type) {
            case ARROW_SAVER, LIGHTNING_STRIKE -> String.format(Locale.ROOT, type.text, amount * 100);
            case KILL_HEAL -> String.format(Locale.ROOT, type.text, amount);
            case NO_KNOCKBACK, KNOCK_AWAY -> String.format(Locale.ROOT, type.text, family);
            case PROJECTILE_WARD -> String.format(Locale.ROOT, type.text, amount, family);
            case FALL_CUSHION, EXPLOSION_WARD, PEARL_MASTERY -> String.format(Locale.ROOT, type.text, amount);
            default -> type.text;
        };
    }

    /**
     * Reads `perk: {type: arrow_saver, amount: 0.45}` (family is needed by family perks). Returns a problem text
     * instead of a perk when the entry is unusable.
     */
    static Object parse(Object raw, java.util.Set<String> families) {
        if (!(raw instanceof Map<?, ?> m) || m.get("type") == null) {
            return "perk must be a section with a type";
        }
        Type type;
        try {
            type = Type.valueOf(String.valueOf(m.get("type")).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return "unknown perk type '" + m.get("type") + "'";
        }
        String family = m.get("family") == null ? null : String.valueOf(m.get("family"));
        if (type.needsFamily && (family == null || !families.contains(family))) {
            return "perk " + type.name().toLowerCase(Locale.ROOT) + " needs a defined family";
        }
        double amount = type.defaultAmount;
        if (m.get("amount") != null) {
            if (!(m.get("amount") instanceof Number n) || n.doubleValue() <= 0) {
                return "perk amount must be a number above 0";
            }
            amount = n.doubleValue();
        }
        return new Perk(type, type.needsFamily ? family : null, amount);
    }

    /** The strongest of the given perks of one type (and family, for family perks): perks never stack. */
    public static Optional<Perk> strongest(java.util.Collection<Perk> active, Type type, String family) {
        return active.stream().filter(p -> p.type == type && (!type.needsFamily || p.family.equals(family)))
                .max(java.util.Comparator.comparingDouble(Perk::amount));
    }
}
