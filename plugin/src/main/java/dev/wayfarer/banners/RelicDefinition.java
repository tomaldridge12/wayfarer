package dev.wayfarer.banners;

import java.util.Locale;

/** A relic on a family's ladder: one weapon or armour piece at one tier. */
public record RelicDefinition(String id, String name, String family, int kills, int tier, Slot slot) {
    /** Where a relic is used: weapons from the main hand, armour worn, charms from the off-hand. */
    public enum Slot {
        WEAPON, HEAD, CHEST, LEGS, FEET, OFFHAND;

        static Slot parse(String text) {
            return Slot.valueOf(text.toUpperCase(Locale.ROOT));
        }

        public boolean armour() {
            return this != WEAPON;
        }
    }
}
