package dev.wayfarer.banners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

/** Keeps relic text current (join, pickup) and blocks smithing upgrades (netherite, alloys): relics never upgrade. */
final class RelicListener implements Listener {
    private final RelicItems relics;

    RelicListener(RelicItems relics) {
        this.relics = relics;
    }

    @EventHandler
    void onJoin(PlayerJoinEvent event) {
        for (ItemStack item : event.getPlayer().getInventory().getContents()) {
            if (item != null) {
                relics.refresh(item);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player) {
            ItemStack stack = event.getItem().getItemStack();
            if (relics.refresh(stack)) {
                event.getItem().setItemStack(stack);
            }
        }
    }

    /** Arrows remember the relic bow that fired them, so their edge does not depend on what is held on impact. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onShoot(org.bukkit.event.entity.EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player && event.getBow() != null) {
            relics.relicId(event.getBow()).ifPresent(id -> relics.tagProjectile(event.getProjectile(), id));
        }
    }

    /** Armour trims still work on relics. After the alloys module has set its upgrade result. */
    @EventHandler(priority = EventPriority.HIGHEST)
    void onSmithing(PrepareSmithingEvent event) {
        if (!(event.getInventory().getRecipe() instanceof org.bukkit.inventory.SmithingTrimRecipe)
                && event.getInventory().getInputEquipment() != null
                && relics.relicId(event.getInventory().getInputEquipment()).isPresent()) {
            event.setResult(null);
        }
    }
}
