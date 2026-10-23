package dev.wayfarer.core;

import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired by the bosses module when a boss dies; other modules turn it into rewards. Main thread. */
public final class BossDefeatedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final BossMark boss;
    private final Location location;
    private final List<UUID> damagers;

    /** @param damagers every player who damaged the boss, in first-hit order */
    public BossDefeatedEvent(BossMark boss, Location location, List<UUID> damagers) {
        this.boss = boss;
        this.location = location.clone();
        this.damagers = List.copyOf(damagers);
    }

    public BossMark boss() {
        return boss;
    }

    public Location location() {
        return location.clone();
    }

    public List<UUID> damagers() {
        return damagers;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
