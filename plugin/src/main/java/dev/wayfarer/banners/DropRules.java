package dev.wayfarer.banners;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.DoubleSupplier;

/**
 * Banner drops from specific mobs. Each configured drop rolls on its own; a success is earned by every player
 * who damaged the mob. No server types.
 */
public final class DropRules {
    /** One side of a hit, for crediting: a player (by UUID) or anything else. */
    public record Hitter(UUID id, boolean player) {
    }

    private static final long FORGET_AFTER_TICKS = 20L * 60 * 10;

    private record Damage(Set<UUID> players, long lastHitTick) {
    }

    private final Map<UUID, Damage> damaged = new HashMap<>();

    /** The player credited with a hit: a projectile's shooter, else the direct attacker; pets and mobs never. */
    public static Optional<UUID> credited(Hitter direct, Hitter shooter) {
        Hitter attacker = shooter != null ? shooter : direct;
        return attacker.player() ? Optional.of(attacker.id()) : Optional.empty();
    }

    /** Rolls every drop configured for this mob type; {@code random} gives values in [0, 1). */
    public static List<BannerDefinition> roll(String mobType, BannerSet set, DoubleSupplier random) {
        List<BannerDefinition> won = new ArrayList<>();
        for (BannerSet.Drop drop : set.dropsFor(mobType)) {
            if (random.getAsDouble() < drop.chance()) {
                set.find(drop.bannerId()).ifPresent(won::add);
            }
        }
        return won;
    }

    public void recordHit(UUID mob, UUID player, long tick) {
        Damage old = damaged.get(mob);
        Set<UUID> players = old == null ? new LinkedHashSet<>() : old.players();
        players.add(player);
        damaged.put(mob, new Damage(players, tick));
    }

    /** Everyone who damaged the mob, in first-hit order; forgets the mob. */
    public List<UUID> takeDamagers(UUID mob) {
        Damage d = damaged.remove(mob);
        return d == null ? List.of() : List.copyOf(d.players());
    }

    /** Drops records for mobs not hit for ten minutes (despawned or wandered off), so the log cannot grow forever. */
    public void forgetStale(long nowTick) {
        damaged.values().removeIf(d -> nowTick - d.lastHitTick() > FORGET_AFTER_TICKS);
    }

    public int tracked() {
        return damaged.size();
    }
}
