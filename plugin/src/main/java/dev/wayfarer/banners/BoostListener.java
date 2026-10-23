package dev.wayfarer.banners;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Two steps per hit: at HIGH the boost changes the damage; at MONITOR, once the outcome is final, the
 * feedback shows it. Other plugins at HIGHEST can still cancel in between, so the boost is kept until MONITOR.
 */
final class BoostListener implements Listener {
    private final BoostGlue boosts;
    private final BannerFeedback feedback;
    private final Supplier<BannerSet> banners;
    private final Map<EntityDamageByEntityEvent, BoostGlue.Applied> pending = new IdentityHashMap<>();

    BoostListener(BoostGlue boosts, BannerFeedback feedback, Supplier<BannerSet> banners) {
        this.boosts = boosts;
        this.feedback = feedback;
        this.banners = banners;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onDamage(EntityDamageByEntityEvent event) {
        boosts.apply(event).ifPresent(applied -> pending.put(event, applied));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    void onDamageFinal(EntityDamageByEntityEvent event) {
        BoostGlue.Applied applied = pending.remove(event);
        if (event.isCancelled()) {
            return;
        }
        Entity victim = event.getEntity();
        if (victim instanceof Player player) {
            if (applied != null && !applied.resolved().bonus()) {
                feedback.onBlocked(player, applied, banners.get());
            }
            return;
        }
        if (!(victim instanceof LivingEntity mob) || victim instanceof ArmorStand || !byPlayer(event.getDamager())) {
            return;
        }
        double after = event.getFinalDamage();
        double before = applied != null && applied.resolved().bonus() ? Math.min(applied.before(), after) : after;
        DamageNumbers.Split split = DamageNumbers.split(before, after, mob.getHealth() + mob.getAbsorptionAmount());
        feedback.onPlayerHit(mob, split, event.isCritical());
    }

    private static boolean byPlayer(Entity damager) {
        return damager instanceof Player
                || damager instanceof Projectile p && p.getShooter() instanceof Player;
    }
}
