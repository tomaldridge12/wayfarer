package dev.wayfarer.core;

import org.bukkit.entity.Player;

/** Offered by the exploration module: Wayfinder pointings given away by other modules (curios, charts). */
public interface Wayfinding {
    /** Opens the free Wayfinder choice for the player; false if nothing can be offered right now. */
    boolean offerFree(Player player);

    /**
     * Points the player at the nearest structure of this key ({@code minecraft:shipwreck}) for a while, as a used-up
     * chart does. The search waits its turn in the Wayfinder's queue; {@code done} hears whether one was found in range.
     */
    void pointToStructure(Player player, String structure, int seconds, java.util.function.Consumer<Boolean> done);
}
