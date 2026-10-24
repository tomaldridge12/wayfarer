package dev.wayfarer.core.progression;

import java.util.List;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired on the main thread after a player's counters went up or were set, so modules can redraw (plaques). */
public final class ProgressChangedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID player;
    private final List<String> raised;

    public ProgressChangedEvent(UUID player, List<String> raised) {
        this.player = player;
        this.raised = List.copyOf(raised);
    }

    public UUID player() {
        return player;
    }

    public List<String> raised() {
        return raised;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
