package dev.wayfarer.banners;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;

/** Records who damaged mobs that can drop banners, and hands their deaths to the module. */
final class DropListener implements Listener {
    private final BannersModule module;

    DropListener(BannersModule module) {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim) || victim instanceof Player) {
            return;
        }
        Entity direct = event.getDamager();
        DropRules.Hitter shooter = direct instanceof Projectile p && p.getShooter() instanceof Entity e ? hitter(e) : null;
        DropRules.credited(hitter(direct), shooter).ifPresent(player -> module.recordDropHit(victim, player));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    void onDeath(EntityDeathEvent event) {
        module.onDropMobDeath(event.getEntity());
    }

    private static DropRules.Hitter hitter(Entity entity) {
        return new DropRules.Hitter(entity.getUniqueId(), entity instanceof Player);
    }
}
