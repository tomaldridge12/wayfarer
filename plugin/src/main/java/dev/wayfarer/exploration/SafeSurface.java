package dev.wayfarer.exploration;

import dev.wayfarer.core.BossMark;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.bukkit.World;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Safe surface: once the first ender dragon falls, natural hostile spawns under open sky or above sea level in the
 * Overworld are stopped. HIGHEST, after the bosses module has turned some natural spawns into roaming bosses (which
 * cancels the spawn and brings the boss in with a plugin spawn, untouched here). Main thread.
 */
final class SafeSurface implements Listener {
    private final BooleanSupplier on;
    private final Consumer<String> dragonDown;

    SafeSurface(BooleanSupplier on, Consumer<String> dragonDown) {
        this.on = on;
        this.dragonDown = dragonDown;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onSpawn(CreatureSpawnEvent event) {
        var at = event.getLocation();
        boolean hostile = event.getEntity() instanceof Enemy;
        if (ExplorationRules.stopsSpawn(on.getAsBoolean(), at.getWorld().getEnvironment() == World.Environment.NORMAL,
                event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL, hostile, at.getBlock().getLightFromSky(),
                at.getBlockY(), at.getWorld().getSeaLevel()) && BossMark.read(event.getEntity()).isEmpty()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    void onDeath(EntityDeathEvent event) {
        if (event.getEntityType() == EntityType.ENDER_DRAGON) {
            dragonDown.accept(event.getEntity().getKiller() == null ? "the dragon" : event.getEntity().getKiller().getName());
        }
    }
}
