package dev.wayfarer.core.progression;

import dev.wayfarer.core.BossMark;
import dev.wayfarer.core.MinionMark;
import io.papermc.paper.entity.Leashable;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;

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
    void onBossDefeated(dev.wayfarer.core.BossDefeatedEvent event) {
        service.defeated(event);
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
        service.kill(killer, new KillFacts(
                KillFacts.Killer.PLAYER,
                target(victim.getType()),
                victim.getEntitySpawnReason().name(),
                victim instanceof Tameable t && t.isTamed(),
                victim instanceof Leashable l && l.isLeashed(),
                victim.getTicksLived()));
    }

    /** Only a player breaking a block fires this; explosions, pistons and the like never count. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        boolean grown = !(block.getBlockData() instanceof org.bukkit.block.data.Ageable crop)
                || crop.getAge() == crop.getMaximumAge();
        service.act(event.getPlayer(), (engine, data) -> engine.mine(data, target(block.getType()), grown));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH && event.getCaught() instanceof Item item) {
            String caught = Activity.CATCH + ":" + target(item.getItemStack().getType());
            service.act(event.getPlayer(), (engine, data) -> engine.record(data, caught));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onShear(PlayerShearEntityEvent event) {
        String shorn = Activity.SHEAR + ":" + target(event.getEntity().getType());
        service.act(event.getPlayer(), (engine, data) -> engine.record(data, shorn));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onBreed(EntityBreedEvent event) {
        if (event.getBreeder() instanceof Player breeder) {
            String bred = Activity.BREED + ":" + target(event.getEntity().getType());
            service.act(breeder, (engine, data) -> engine.record(data, bred));
        }
    }

    private static String target(Keyed type) {
        NamespacedKey key = type.getKey();
        return Activity.target(key.getNamespace(), key.getKey());
    }
}
