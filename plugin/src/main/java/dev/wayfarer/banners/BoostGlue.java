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
    private final RelicItems relics;
    private final Supplier<WayfarerSet.Settings> setSettings;

    BoostGlue(PlacedBanners placed, Supplier<BannerSet> banners, RelicItems relics,
            Supplier<WayfarerSet.Settings> setSettings) {
        this.placed = placed;
        this.banners = banners;
        this.relics = relics;
        this.setSettings = setSettings;
    }

    Optional<Applied> apply(EntityDamageByEntityEvent event) {
        BannerSet set = banners.get();
        Entity direct = event.getDamager();
        boolean projectile = direct instanceof Projectile;
        BoostRules.Party shooter = direct instanceof Projectile p && p.getShooter() instanceof Entity e
                ? party(e, java.util.List.of()) : null;
        BoostRules.Party attacker = party(direct, projectile ? relics.projectileEdges(direct)
                : direct instanceof Player pl ? relics.weaponEdges(pl) : java.util.List.of());
        Entity hit = event.getEntity();
        java.util.List<BoostRules.Edge> guard = new java.util.ArrayList<>();
        if (hit instanceof Player pl) {
            guard.addAll(relics.armourEdges(pl));
            if (WayfarerSet.complete(relics.wornArmour(pl), setSettings.get())) {
                guard.add(WayfarerSet.edge(setSettings.get()));
            }
            if (projectile) {
                // Projectile-ward perks act like an armour edge against that family's arrows.
                relics.activePerks(pl).stream().filter(p -> p.type() == Perk.Type.PROJECTILE_WARD)
                        .map(p -> new BoostRules.Edge("perk", p.family(), (int) Math.round(p.amount())))
                        .max(java.util.Comparator.comparingInt(BoostRules.Edge::percent)).ifPresent(guard::add);
            }
        }
        BoostRules.Party victim = party(hit, guard);
        Optional<BoostRules.Resolved> resolved = BoostRules.resolve(attacker, shooter, victim, at -> nearby(at, set), set);
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

    private static BoostRules.Party party(Entity entity, java.util.List<BoostRules.Edge> edges) {
        Location at = entity.getLocation();
        String type = dev.wayfarer.core.BossMark.read(entity).map(m -> BannerSet.BOSS_PREFIX + m.family())
                .orElse(entity.getType().getKey().getKey()); // a boss counts as its own family
        return new BoostRules.Party(entity instanceof Player, type,
                new BoostRules.Point(entity.getWorld().getUID(), at.getX(), at.getY(), at.getZ()),
                entity instanceof org.bukkit.entity.Enemy, edges);
    }
}
