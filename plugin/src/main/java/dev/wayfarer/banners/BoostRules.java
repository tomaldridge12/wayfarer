package dev.wayfarer.banners;

import java.util.Comparator;
import java.util.List;
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

    /** A relic's family edge: its family (or the all-mobs family) and percent. */
    public record Edge(String relicId, String family, int percent) {
    }

    /**
     * One side of a hit: a player, or any other entity (pets included) by type. {@code edges} are the relic
     * edges it brings: a player's main-hand weapon when attacking, worn armour and off-hand charm when hit,
     * or the relic bow that fired an arrow.
     */
    public record Party(boolean player, String type, Point at, boolean hostile, List<Edge> edges) {
        public Party(boolean player, String type, Point at) {
            this(player, type, at, !player, List.of());
        }
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
            String family = set.familyOf(victim.type());
            String base = set.baseFamilyOf(victim.type()); // a mini boss also counts as its base family
            List<PlacedBannerRegistry.Placed> near = nearby.apply(attacker.at()).toList();
            Optional<Boost> banner = stronger(bonus(attacker.at(), family, near.stream(), set),
                    bonus(attacker.at(), base, near.stream(), set));
            // A projectile carries the edge of the bow that fired it; a melee hit uses the held weapon.
            List<Edge> weapon = shooter != null ? direct.edges() : attacker.edges();
            Optional<Edge> edge = weapon.stream()
                    .filter(e -> matches(e, family, victim.hostile()) || (base != null && matches(e, base, victim.hostile())))
                    .max(java.util.Comparator.comparingInt(Edge::percent));
            return combine(banner, edge.map(List::of).orElse(List.of()), 1000).map(b -> new Resolved(b, true));
        }
        if (victim.player() && !attacker.player()) {
            String family = set.familyOf(attacker.type());
            String base = set.baseFamilyOf(attacker.type());
            List<PlacedBannerRegistry.Placed> near = nearby.apply(victim.at()).toList();
            Optional<Boost> banner = stronger(reduction(victim.at(), family, near.stream(), set),
                    reduction(victim.at(), base, near.stream(), set));
            List<Edge> armour = victim.edges().stream()
                    .filter(e -> matches(e, family, attacker.hostile()) || (base != null && matches(e, base, attacker.hostile())))
                    .toList();
            return combine(banner, armour, 100).map(b -> new Resolved(b, false));
        }
        return Optional.empty();
    }

    private static Optional<Boost> stronger(Optional<Boost> a, Optional<Boost> b) {
        if (a.isEmpty()) {
            return b;
        }
        return b.isPresent() && b.get().percent() > a.get().percent() ? b : a;
    }

    private static boolean matches(Edge edge, String mobFamily, boolean hostile) {
        return MobFamily.ALL.equals(edge.family()) ? hostile : edge.family().equals(mobFamily);
    }

    /**
     * Banner and relic edges add up (banners never stack with each other); capped so damage taken never goes
     * below zero. The source named is the banner when there is one, else the strongest relic.
     */
    private static Optional<Boost> combine(Optional<Boost> banner, List<Edge> edges, int cap) {
        int total = banner.map(Boost::percent).orElse(0) + edges.stream().mapToInt(Edge::percent).sum();
        if (total <= 0) {
            return Optional.empty();
        }
        String source = banner.map(Boost::bannerId).orElseGet(() -> edges.stream()
                .max(java.util.Comparator.comparingInt(Edge::percent)).map(Edge::relicId).orElse(null));
        return Optional.of(new Boost(source, Math.min(total, cap)));
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
