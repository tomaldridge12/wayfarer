package dev.wayfarer.core.progression;

import dev.wayfarer.core.BossMark;
import dev.wayfarer.core.MinionMark;
import io.papermc.paper.entity.Leashable;
import org.bukkit.NamespacedKey;
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

/** Turns server events into activities with their facts for the progression engine. */
final class ProgressionListener implements Listener {
    private final ProgressionService service;

    ProgressionListener(ProgressionService service) {
        this.service = service;
    }

    @EventHandler
    void onPreLogin(AsyncPlayerPreLoginEvent event) {
        service.loadBlocking(event.getUniqueId());
    }

    @EventHandler
    void onQuit(PlayerQuitEvent event) {
        service.quit(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onDeath(EntityDeathEvent event) {
        LivingEntity victim = event.getEntity();
        // Bosses are counted from their defeat (every damager); boss minions never count.
        if (victim instanceof Player || MinionMark.is(victim) || BossMark.read(victim).isPresent()) {
            return;
        }
        Entity cause = event.getDamageSource().getCausingEntity(); // a projectile's cause is its shooter
        if (!(cause instanceof Player killer)) {
            return;
        }
        NamespacedKey type = victim.getType().getKey();
        service.kill(killer, new KillFacts(
                KillFacts.Killer.PLAYER,
                Activity.target(type.getNamespace(), type.getKey()),
                victim.getEntitySpawnReason().name(),
                victim instanceof Tameable t && t.isTamed(),
                victim instanceof Leashable l && l.isLeashed(),
                victim.getTicksLived()));
    }
}
