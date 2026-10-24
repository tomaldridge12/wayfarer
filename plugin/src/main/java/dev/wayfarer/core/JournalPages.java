package dev.wayfarer.core;

import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Offered by the collections module, which owns the Journal: other modules add their pages (objectives, feats, the
 * shop's cosmetics) and open the Journal. Main thread.
 */
public interface JournalPages {
    /**
     * One page: its id, the button on the Journal's cover (label and hover line), where it sorts among the others
     * (lower first) and how it opens for a player.
     */
    record Page(String id, Component label, Component tooltip, int order, Consumer<Player> open) {
    }

    /** Adds or replaces the owner's page. */
    void add(String owner, Page page);

    /** Opens the Journal's cover for the player. */
    void openCover(Player player);

    /** The button that returns to the cover, for the foot of every page. */
    Menus.Button back();
}
