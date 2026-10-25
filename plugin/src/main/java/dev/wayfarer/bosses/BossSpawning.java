package dev.wayfarer.bosses;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;

/** Bosses appearing on their own: rare natural spawns and structure guardians, with cooldowns and a hint. */
final class BossSpawning implements Listener {
    static final double HINT_RANGE = 64;

    /** Spawns a boss and starts tracking it; returns false if it could not. */
    interface Spawn {
        boolean spawn(BossDefinition boss, BossVariant variant, int stars, Location at, String why, boolean guardian);
    }

    private final Plugin plugin;
    private final Supplier<BossRoster> roster;
    private final BossController controller;
    private final Spawn spawn;
    private final Logger log;

    BossSpawning(Plugin plugin, Supplier<BossRoster> roster, BossController controller, Spawn spawn, Logger log) {
        this.plugin = plugin;
        this.roster = roster;
        this.controller = controller;
        this.spawn = spawn;
        this.log = log;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) {
            return;
        }
        Location at = event.getLocation();
        String entity = event.getEntityType().getKey().getKey();
        NamespacedKey biomeKey = org.bukkit.Registry.BIOME.getKey(at.getBlock().getBiome());
        String biome = biomeKey == null ? "" : biomeKey.asString(); // full key: datapack biomes are not minecraft:
        long now = System.currentTimeMillis();
        for (BossDefinition boss : roster.get().bosses().values()) {
            if (SpawnRules.naturalSpawn(boss.spawn(), entity, biome, night(at.getWorld()), alive(boss),
                    naturalCooldownLeft(at.getWorld(), boss, now),
                    ThreadLocalRandom.current()::nextDouble)) {
                event.setCancelled(true);
                int stars = SpawnRules.stars(at.distance(at.getWorld().getSpawnLocation()), false,
                        roster.get().spawning(), ThreadLocalRandom.current()::nextDouble);
                // Spawning inside the spawn event is not safe; the boss takes the mob's place next tick.
                plugin.getServer().getScheduler().runTask(plugin, () -> appear(boss, stars, at, "roaming", true));
                return;
            }
        }
    }

    /**
     * A defeat starts the guardian cooldown of every structure the boss died in or called home, so a guardian that
     * stood waiting for hours (or a roaming boss killed in a structure) is not replaced the moment it falls.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    void onDefeat(dev.wayfarer.core.BossDefeatedEvent event) {
        long now = System.currentTimeMillis();
        var mark = event.boss();
        Location home = new Location(event.location().getWorld(), mark.homeX(), mark.homeY(), mark.homeZ());
        for (Location point : new Location[] {event.location(), home}) {
            for (GeneratedStructure structure : point.getChunk().getStructures()) {
                if (structure.getBoundingBox().contains(point.toVector())) {
                    structure.getPersistentDataContainer().set(GUARDIAN_LAST, PersistentDataType.LONG, now);
                }
            }
        }
    }

    /** Every two seconds: players inside a listed structure wake its guardian. */
    void checkGuardians() {
        long now = System.currentTimeMillis();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Location at = player.getLocation();
            for (GeneratedStructure structure : at.getChunk().getStructures()) {
                BoundingBox box = structure.getBoundingBox();
                if (!box.contains(at.toVector())) {
                    continue;
                }
                var structureKey = org.bukkit.Registry.STRUCTURE.getKey(structure.getStructure());
                if (structureKey == null) {
                    continue;
                }
                String key = structureKey.asString();
                for (BossDefinition boss : roster.get().bosses().values()) {
                    long structureLeft = SpawnRules.cooldownLeft(structureLast(structure), boss.cooldownMinutes(), now);
                    if (SpawnRules.guardian(boss.spawn(), key, aliveNear(boss, at), structureLeft)) {
                        int stars = SpawnRules.stars(at.distance(at.getWorld().getSpawnLocation()), true,
                                roster.get().spawning(), ThreadLocalRandom.current()::nextDouble);
                        if (appear(boss, stars, spotNear(player, box), "guardian of " + structureKey.getKey(), false)) {
                            structure.getPersistentDataContainer().set(GUARDIAN_LAST, PersistentDataType.LONG, now);
                        }
                        return; // one guardian per check is plenty
                    }
                }
            }
        }
    }

    private boolean appear(BossDefinition boss, int stars, Location at, String why, boolean roaming) {
        BossVariant variant = BossRoster.pickVariant(boss, ThreadLocalRandom.current()::nextDouble);
        if (!spawn.spawn(boss, variant, stars, at, why, !roaming)) {
            return false;
        }
        if (roaming) {
            at.getWorld().getPersistentDataContainer().set(lastKey(boss), PersistentDataType.LONG, System.currentTimeMillis());
        }
        for (Player p : at.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(at) <= HINT_RANGE * HINT_RANGE) {
                p.sendActionBar(Component.text("⚔ You sense a powerful presence nearby…", NamedTextColor.GOLD)
                        .decoration(TextDecoration.ITALIC, true));
                p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.35f, 0.6f);
            }
        }
        return true;
    }

    /** A clear spot 5-9 blocks from the player inside the structure, else just in front of them. */
    private static Location spotNear(Player player, BoundingBox box) {
        Location base = player.getLocation();
        for (int attempt = 0; attempt < 12; attempt++) {
            double a = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            double r = 5 + ThreadLocalRandom.current().nextDouble(4);
            Location at = base.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r);
            for (int dy = 3; dy >= -4; dy--) {
                Block feet = at.clone().add(0, dy, 0).getBlock();
                if (box.contains(feet.getLocation().toVector()) && feet.isPassable()
                        && feet.getRelative(0, 1, 0).isPassable() && !feet.getRelative(0, -1, 0).isPassable()) {
                    return feet.getLocation().add(0.5, 0, 0.5);
                }
            }
        }
        return base.clone().add(base.getDirection().setY(0).normalize().multiply(4));
    }

    private boolean alive(BossDefinition boss) {
        return controller.live().values().stream().anyMatch(l -> l.boss.id().equals(boss.id()));
    }

    /** A boss of this kind within hint range of a point, e.g. a guardian already awake in this structure. */
    private boolean aliveNear(BossDefinition boss, Location at) {
        return controller.live().values().stream().anyMatch(l -> l.boss.id().equals(boss.id())
                && l.entity.getWorld().equals(at.getWorld())
                && l.entity.getLocation().distanceSquared(at) <= HINT_RANGE * HINT_RANGE);
    }

    private long naturalCooldownLeft(World world, BossDefinition boss, long now) {
        return SpawnRules.cooldownLeft(lastSpawn(world, boss), roster.get().spawning().naturalCooldownMinutes(), now);
    }

    /** What /wayfarer boss here reports: this spot's biome and structures, and which bosses could appear. */
    java.util.List<Component> here(Player player) {
        Location at = player.getLocation();
        long now = System.currentTimeMillis();
        BossRoster r = roster.get();
        NamespacedKey biomeKey = org.bukkit.Registry.BIOME.getKey(at.getBlock().getBiome());
        String biome = biomeKey == null ? "?" : biomeKey.asString();
        java.util.List<Component> lines = new java.util.ArrayList<>();
        lines.add(Component.text("⚔ Bosses here", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        lines.add(Component.text("Biome: ", NamedTextColor.GRAY).append(Component.text(biome, NamedTextColor.WHITE))
                .append(Component.text(night(at.getWorld()) ? "  (night)" : "  (day)", NamedTextColor.DARK_GRAY)));
        boolean any = false;
        for (BossDefinition boss : r.bosses().values()) {
            SpawnRules.BossSpawn s = boss.spawn();
            if (s.chance() > 0 && (s.anyBiome() || s.biomes().contains(biome))) {
                any = true;
                long left = naturalCooldownLeft(at.getWorld(), boss, now);
                lines.add(Component.text(" Roams here: ", NamedTextColor.GRAY)
                        .append(Component.text(boss.name(), NamedTextColor.WHITE))
                        .append(Component.text(String.format(java.util.Locale.ROOT, "  1 in %,d %s spawns%s, up to %d★%s%s",
                                Math.round(1 / s.chance()), s.entity(), s.nightOnly() ? " at night" : "",
                                r.spawning().naturalMaxStars(), alive(boss) ? ", one is alive" : "",
                                left > 0 ? ", ready in " + minutes(left) : ", ready"), NamedTextColor.DARK_GRAY)));
            }
        }
        for (GeneratedStructure structure : at.getChunk().getStructures()) {
            var key = org.bukkit.Registry.STRUCTURE.getKey(structure.getStructure());
            if (key == null) {
                continue;
            }
            boolean inside = structure.getBoundingBox().contains(at.toVector());
            for (BossDefinition boss : r.bosses().values()) {
                if (boss.spawn().structures().contains(key.asString())) {
                    any = true;
                    long left = SpawnRules.cooldownLeft(structureLast(structure), boss.cooldownMinutes(), now);
                    lines.add(Component.text(" Guards " + key.getKey() + ": ", NamedTextColor.GRAY)
                            .append(Component.text(boss.name(), NamedTextColor.WHITE))
                            .append(Component.text(String.format(java.util.Locale.ROOT, "  %d★+, %s%s",
                                    r.spawning().guardianMinStars(), left > 0 ? "ready in " + minutes(left) : "ready",
                                    inside ? "" : " (step inside to wake it)"), NamedTextColor.DARK_GRAY)));
                }
            }
        }
        if (!any) {
            lines.add(Component.text(" No boss roams or guards here.", NamedTextColor.DARK_GRAY));
        }
        controller.live().values().stream()
                .filter(l -> l.entity.getWorld().equals(at.getWorld()) && l.entity.getLocation().distanceSquared(at) <= 128 * 128)
                .forEach(l -> lines.add(Component.text(" Alive nearby: ", NamedTextColor.GRAY)
                        .append(BossText.name(l.variant, l.mark.stars()))
                        .append(Component.text(String.format(java.util.Locale.ROOT, "  %.0f blocks",
                                l.entity.getLocation().distance(at)), NamedTextColor.DARK_GRAY))));
        return lines;
    }

    private static String minutes(long millis) {
        long m = (millis + 59_999) / 60_000;
        return m >= 60 ? (m / 60) + "h " + (m % 60) + "m" : m + "m";
    }

    private static boolean night(World world) {
        long t = world.getTime() % 24000;
        return t >= 13000 && t <= 23000;
    }

    private static final NamespacedKey GUARDIAN_LAST = new NamespacedKey("wayfarer", "guardian_last");

    private static NamespacedKey lastKey(BossDefinition boss) {
        return new NamespacedKey("wayfarer", "boss_last_" + boss.id());
    }

    private static long lastSpawn(World world, BossDefinition boss) {
        Long last = world.getPersistentDataContainer().get(lastKey(boss), PersistentDataType.LONG);
        return last == null ? 0 : last;
    }

    private static long structureLast(GeneratedStructure structure) {
        Long last = structure.getPersistentDataContainer().get(GUARDIAN_LAST, PersistentDataType.LONG);
        return last == null ? 0 : last;
    }

    static int countRules(BossRoster roster, boolean guardians) {
        return (int) roster.bosses().values().stream().filter(b -> guardians ? !b.spawn().structures().isEmpty()
                : b.spawn().chance() > 0).count();
    }
}
