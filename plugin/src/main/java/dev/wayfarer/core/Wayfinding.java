package dev.wayfarer.core;

import org.bukkit.entity.Player;

/** Offered by the exploration module: Wayfinder pointings given away by other modules (curios, charts). */
public interface Wayfinding {
    /** Opens the free Wayfinder choice for the player; false if nothing can be offered right now. */
    boolean offerFree(Player player);

    /**
     * Points the player at the nearest structure of this key ({@code minecraft:shipwreck}) for a while, as a used-up
     * chart does; false if none was found within range.
     */
    boolean pointToStructure(Player player, String structure, int seconds);
}
