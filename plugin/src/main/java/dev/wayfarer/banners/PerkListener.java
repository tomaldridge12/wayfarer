package dev.wayfarer.banners;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityKnockbackByEntityEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/** Applies relic perks. Which perk is active and how strong comes from {@link Perk}; this only touches events. */
final class PerkListener implements Listener {
    private final Plugin plugin;
    private final RelicItems relics;
    private final Supplier<BannerSet> banners;

    PerkListener(Plugin plugin, RelicItems relics, Supplier<BannerSet> banners) {
        this.plugin = plugin;
        this.relics = relics;
        this.banners = banners;
    }

    private Optional<Perk> perk(Player player, Perk.Type type, String family) {
        return Perk.strongest(relics.activePerks(player), type, family);
    }

    private String familyOf(Entity entity) {
        return banners.get().familyOf(entity.getType().getKey().getKey());
    }

    @EventHandler(ignoreCancelled = true)
    void onEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getNewEffect() == null) {
            return;
        }
        PotionEffectType type = event.getModifiedType();
        if ((type.equals(PotionEffectType.HUNGER) && perk(player, Perk.Type.HUNGER_IMMUNITY, null).isPresent())
                || (type.equals(PotionEffectType.POISON) && perk(player, Perk.Type.POISON_IMMUNITY, null).isPresent())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    void onKnockback(EntityKnockbackByEntityEvent event) {
        if (event.getEntity() instanceof Player player) {
            String family = familyOf(event.getSourceEntity());
            if (family != null && perk(player, Perk.Type.NO_KNOCKBACK, family).isPresent()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onShoot(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player player && event.shouldConsumeItem()) {
            perk(player, Perk.Type.ARROW_SAVER, null).filter(p -> ThreadLocalRandom.current().nextDouble() < p.amount())
                    .ifPresent(p -> {
                        event.setConsumeItem(false);
                        if (event.getProjectile() instanceof AbstractArrow arrow) {
                            arrow.setPickupStatus(AbstractArrow.PickupStatus.CREATIVE_ONLY); // no free duplicate
                        }
                    });
        }
    }

    @EventHandler(ignoreCancelled = true)
    void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock() || perk(event.getPlayer(), Perk.Type.COBWEB_WALKER, null).isEmpty()) {
            return;
        }
        for (Block block : List.of(event.getTo().getBlock(), event.getTo().getBlock().getRelative(0, 1, 0))) {
            if (block.getType() == Material.COBWEB) {
                block.setType(Material.AIR); // dissolves without dropping string
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getDamageSource().getDamageType().equals(DamageType.ENDER_PEARL)) {
            if (perk(player, Perk.Type.PEARL_MASTERY, null).isPresent()) {
                event.setCancelled(true);
            }
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            perk(player, Perk.Type.FALL_CUSHION, null).filter(p -> player.getFallDistance() <= p.amount())
                    .ifPresent(p -> event.setCancelled(true));
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                || event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            perk(player, Perk.Type.EXPLOSION_WARD, null).ifPresent(p -> {
                double target = Math.max(0, event.getFinalDamage() * (1 - p.amount() / 100));
                event.setDamage(DamageSolver.rawFor(target, event.getDamage(), r -> {
                    event.setDamage(r);
                    return event.getFinalDamage();
                }));
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onHit(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && event.getEntity() instanceof LivingEntity victim) {
            String family = familyOf(victim);
            if (family == null) {
                return;
            }
            perk(player, Perk.Type.KNOCK_AWAY, family).ifPresent(p -> plugin.getServer().getScheduler()
                    .runTask(plugin, () -> {
                        // After vanilla knockback has been applied, push much further.
                        Vector away = victim.getLocation().toVector().subtract(player.getLocation().toVector())
                                .setY(0);
                        if (away.lengthSquared() > 0) {
                            victim.setVelocity(away.normalize().multiply(p.amount()).setY(0.45));
                        }
                    }));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onLightning(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && event.getEntity() instanceof LivingEntity victim
                && !(victim instanceof Player)) {
            perk(player, Perk.Type.LIGHTNING_STRIKE, null)
                    .filter(p -> ThreadLocalRandom.current().nextDouble() < p.amount())
                    .ifPresent(p -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (victim.isValid()) {
                            victim.getWorld().strikeLightningEffect(victim.getLocation()); // no fire, no world damage
                            victim.damage(4, player);
                        }
                    }));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    void onKillHeal(org.bukkit.event.entity.EntityDeathEvent event) {
        if (event.getEntity().getKiller() instanceof Player player) {
            perk(player, Perk.Type.KILL_HEAL, null).ifPresent(p -> {
                var max = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                double cap = max == null ? 20 : max.getValue();
                player.setHealth(Math.min(cap, player.getHealth() + p.amount() * 2));
            });
        }
    }

    @EventHandler(ignoreCancelled = true)
    void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getEntity() instanceof Enderman && event.getTarget() instanceof Player player
                && event.getReason() == EntityTargetEvent.TargetReason.CLOSEST_PLAYER
                && perk(player, Perk.Type.ENDERMAN_CALM, null).isPresent()) {
            event.setCancelled(true); // a stare; being hit still angers them
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onPearl(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof EnderPearl && event.getEntity().getShooter() instanceof Player player) {
            perk(player, Perk.Type.PEARL_MASTERY, null).ifPresent(p -> plugin.getServer().getScheduler()
                    .runTask(plugin, () -> player.setCooldown(Material.ENDER_PEARL,
                            (int) Math.round(player.getCooldown(Material.ENDER_PEARL) * (1 - p.amount() / 100)))));
        }
    }
}
