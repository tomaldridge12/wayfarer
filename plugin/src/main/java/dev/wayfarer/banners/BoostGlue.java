package dev.wayfarer.banners;

import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/** Turns a damage event into plain values, asks {@link BoostRules}, and applies the answer to the event. */
final class BoostGlue {
    /** What a boosted hit did, for feedback in later tickets. */
    record Applied(BoostRules.Resolved resolved, double before, double after) {
    }

    private final PlacedBanners placed;
    private final Supplier<BannerSet> banners;

    BoostGlue(PlacedBanners placed, Supplier<BannerSet> banners) {
        this.placed = placed;
        this.banners = banners;
    }

    Optional<Applied> apply(EntityDamageByEntityEvent event) {
        BannerSet set = banners.get();
        Entity direct = event.getDamager();
        BoostRules.Party shooter = direct instanceof Projectile p && p.getShooter() instanceof Entity e ? party(e) : null;
        Optional<BoostRules.Resolved> resolved = BoostRules.resolve(party(direct), shooter, party(event.getEntity()),
                at -> nearby(at, set), set);
        double before = event.getFinalDamage();
        if (resolved.isEmpty() || before <= 0) {
            return Optional.empty(); // nothing applies, or the hit was blocked or fully absorbed
        }
        BoostRules.Boost boost = resolved.get().boost();
        double target = resolved.get().bonus() ? BoostRules.withBonus(before, boost) : BoostRules.withReduction(before, boost);
        double raw = DamageSolver.rawFor(target, event.getDamage(), r -> {
            event.setDamage(r);
            return event.getFinalDamage();
        });
        event.setDamage(raw);
        return Optional.of(new Applied(resolved.get(), before, event.getFinalDamage()));
    }

    private Stream<PlacedBannerRegistry.Placed> nearby(BoostRules.Point at, BannerSet set) {
        return placed.registry().within(at.world(), at.x(), at.z(), set.maxRadius());
    }

    private static BoostRules.Party party(Entity entity) {
        Location at = entity.getLocation();
        return new BoostRules.Party(entity instanceof Player, entity.getType().getKey().getKey(),
                new BoostRules.Point(entity.getWorld().getUID(), at.getX(), at.getY(), at.getZ()));
    }
}
