package dev.wayfarer.banners;

import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import io.papermc.paper.entity.Leashable;

/** Turns server events into plain values for the banner rules. */
final class BannerListener implements Listener {
    private final BannersModule module;

    BannerListener(BannersModule module) {
        this.module = module;
    }

    @EventHandler
    void onPreLogin(AsyncPlayerPreLoginEvent event) {
        module.loadPlayerBlocking(event.getUniqueId()); // async thread, so blocking file I/O is fine here
    }

    @EventHandler
    void onQuit(PlayerQuitEvent event) {
        module.playerQuit(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onDeath(EntityDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim instanceof Player) {
            return;
        }
        Entity cause = event.getDamageSource().getCausingEntity(); // a projectile's cause is its shooter
        if (!(cause instanceof Player killer)) {
            return;
        }
        Kill kill = new Kill(
                Kill.Killer.PLAYER,
                victim.getType().getKey().getKey(),
                victim.getEntitySpawnReason().name(),
                victim instanceof Tameable t && t.isTamed(),
                victim instanceof Leashable l && l.isLeashed(),
                victim.getTicksLived(),
                victim instanceof Enemy);
        module.onKill(killer, kill);
    }
}
