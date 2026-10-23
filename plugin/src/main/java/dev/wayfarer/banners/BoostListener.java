package dev.wayfarer.banners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/** Hands every entity hit to the boost glue. HIGH so most damage changes by other plugins come first. */
final class BoostListener implements Listener {
    private final BoostGlue boosts;

    BoostListener(BoostGlue boosts) {
        this.boosts = boosts;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onDamage(EntityDamageByEntityEvent event) {
        boosts.apply(event);
    }
}
