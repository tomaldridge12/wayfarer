package dev.wayfarer.banners;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Boost resolution for one hit. The player's position decides; only the strongest matching banner counts,
 * chosen separately for bonus and reduction, so zones extend coverage but never add together.
 */
public final class BoostRules {
    public record Point(UUID world, double x, double y, double z) {
    }

    /** The banner that supplied a boost and its percentage. */
    public record Boost(String bannerId, int percent) {
    }

    /** One side of a hit: a player, or any other entity (pets included) by type. */
    public record Party(boolean player, String type, Point at) {
    }

    /** The boost for a hit: a bonus when the player attacks, a reduction when the player is hit. */
    public record Resolved(Boost boost, boolean bonus) {
    }

    private BoostRules() {
    }

    /**
     * @param direct the entity that dealt the hit (an arrow, a wolf, a zombie, a player)
     * @param shooter the shooter when {@code direct} is a projectile, else null; the shooter takes the credit
     * @param nearby placed banners that might cover a point
     */
    public static Optional<Resolved> resolve(Party direct, Party shooter, Party victim,
            java.util.function.Function<Point, Stream<PlacedBannerRegistry.Placed>> nearby, BannerSet set) {
        Party attacker = shooter != null ? shooter : direct;
        if (attacker.player() && !victim.player()) {
            return bonus(attacker.at(), set.familyOf(victim.type()), nearby.apply(attacker.at()), set)
                    .map(b -> new Resolved(b, true));
        }
        if (victim.player() && !attacker.player()) {
            return reduction(victim.at(), set.familyOf(attacker.type()), nearby.apply(victim.at()), set)
                    .map(b -> new Resolved(b, false));
        }
        return Optional.empty();
    }

    /** Bonus for a player hitting a mob of this family. */
    public static Optional<Boost> bonus(Point player, String mobFamily, Stream<PlacedBannerRegistry.Placed> nearby,
            BannerSet set) {
        return strongest(player, mobFamily, nearby, set, BannerDefinition::damageBonusPercent);
    }

    /** Reduction for a player being hit by a mob of this family. */
    public static Optional<Boost> reduction(Point player, String mobFamily,
            Stream<PlacedBannerRegistry.Placed> nearby, BannerSet set) {
        return strongest(player, mobFamily, nearby, set, BannerDefinition::damageReductionPercent);
    }

    public static double withBonus(double finalDamage, Boost bonus) {
        return Math.max(0, finalDamage * (1 + bonus.percent() / 100.0));
    }

    public static double withReduction(double finalDamage, Boost reduction) {
        return Math.max(0, finalDamage * (1 - reduction.percent() / 100.0));
    }

    private static Optional<Boost> strongest(Point player, String mobFamily,
            Stream<PlacedBannerRegistry.Placed> nearby, BannerSet set,
            java.util.function.ToIntFunction<BannerDefinition> percent) {
        if (mobFamily == null) {
            return Optional.empty();
        }
        return nearby
                .filter(p -> p.world().equals(player.world()))
                .flatMap(p -> set.find(p.bannerId()).stream() // inert banners do nothing
                        .filter(def -> def.family().equals(mobFamily) && inZone(player, p.pos(), def.radius()))
                        .map(def -> new Boost(def.id(), percent.applyAsInt(def))))
                .filter(b -> b.percent() > 0)
                .max(Comparator.comparingInt(Boost::percent));
    }

    /** Within the radius of the banner block's centre, edge included. */
    static boolean inZone(Point player, PlacedBannerRegistry.BlockPos banner, int radius) {
        double dx = player.x() - (banner.x() + 0.5);
        double dy = player.y() - (banner.y() + 0.5);
        double dz = player.z() - (banner.z() + 0.5);
        return dx * dx + dy * dy + dz * dz <= (double) radius * radius;
    }
}
