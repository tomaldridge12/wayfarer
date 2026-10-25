package dev.wayfarer.core;

import java.util.UUID;
import org.bukkit.entity.Player;

/** Offered by the objectives module, which keeps the tracked list: lets any Journal page add a Track button. Main thread. */
public interface TrackerControl {
    boolean isTracked(UUID player, String trackedId);

    /**
     * Tracks the thing if it is not tracked, or untracks it, telling the player (a full list refuses with a message).
     *
     * @param name what to call it in messages
     * @param after runs once it has been done, to redraw the page
     */
    void toggle(Player player, String trackedId, String name, Runnable after);
}
