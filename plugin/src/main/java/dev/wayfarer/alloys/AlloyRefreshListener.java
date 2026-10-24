package dev.wayfarer.alloys;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

/** Alloy gear follows config: its stats are re-applied when a player joins and when they pick it up. */
final class AlloyRefreshListener implements Listener {
    private final AlloyItems items;

    AlloyRefreshListener(AlloyItems items) {
        this.items = items;
    }

    @EventHandler
    void onJoin(PlayerJoinEvent event) {
        for (ItemStack item : event.getPlayer().getInventory().getContents()) {
            if (item != null) {
                items.refresh(item);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player) {
            ItemStack stack = event.getItem().getItemStack();
            if (items.refresh(stack)) {
                event.getItem().setItemStack(stack);
            }
        }
    }
}
