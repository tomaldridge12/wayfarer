package dev.wayfarer.core;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;

/**
 * Wayfarer's XP prices, in points whatever a player's level (ADR 0007). Main thread. Creative players pay nothing.
 */
public final class Xp {
    /** Points the player holds. */
    public int points(Player player) {
        return XpPoints.held(player.getLevel(), player.getExp());
    }

    /** Takes the points and returns true, or takes nothing and returns false if the player holds fewer. Free in creative. */
    public boolean take(Player player, int points) {
        if (player.getGameMode() == GameMode.CREATIVE || points <= 0) {
            return true;
        }
        return XpPoints.after(player.getLevel(), player.getExp(), points).map(bar -> {
            player.setLevel(bar.level());
            player.setExp(bar.progress());
            return true;
        }).orElse(false);
    }

    /** "27 XP · about 2 of your levels" for this player. */
    public String price(Player player, int points) {
        return XpPoints.describe(player.getLevel(), player.getExp(), points);
    }

    /** What to tell a player who cannot afford a price: "That costs 315 XP; you have 120." */
    public String cannotAfford(Player player, int points) {
        return "That costs " + points + " XP; you have " + points(player) + ".";
    }
}
