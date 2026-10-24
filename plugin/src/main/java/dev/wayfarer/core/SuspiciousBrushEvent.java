package dev.wayfarer.core;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * A player started brushing a suspicious block whose loot is not rolled yet; core has counted {@code brush:<block>}.
 * Paper has no brush-finished event, so this is the moment to change what the block holds. Main thread.
 */
public final class SuspiciousBrushEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Block block;

    public SuspiciousBrushEvent(Player player, Block block) {
        this.player = player;
        this.block = block;
    }

    public Player player() {
        return player;
    }

    public Block block() {
        return block;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
