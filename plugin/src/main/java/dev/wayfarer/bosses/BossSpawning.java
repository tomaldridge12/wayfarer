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
        boolean spawn(BossDefinition boss, BossVariant variant, int stars, Location at, String why);
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
        String biome = at.getBlock().getBiome().getKey().getKey();
        long now = System.currentTimeMillis();
        for (BossDefinition boss : roster.get().bosses().values()) {
            if (SpawnRules.naturalSpawn(boss.spawn(), entity, biome, night(at.getWorld()), alive(boss),
                    SpawnRules.cooldownLeft(lastSpawn(at.getWorld(), boss), boss.cooldownMinutes(), now),
                    ThreadLocalRandom.current()::nextDouble)) {
                event.setCancelled(true);
                int stars = SpawnRules.stars(at.distance(at.getWorld().getSpawnLocation()), false,
                        roster.get().spawning(), ThreadLocalRandom.current()::nextDouble);
                // Spawning inside the spawn event is not safe; the boss takes the mob's place next tick.
                plugin.getServer().getScheduler().runTask(plugin, () -> appear(boss, stars, at, "natural"));
                return;
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
                String key = structureKey.getKey();
                for (BossDefinition boss : roster.get().bosses().values()) {
                    long structureLeft = SpawnRules.cooldownLeft(structureLast(structure), boss.cooldownMinutes(), now);
                    if (SpawnRules.guardian(boss.spawn(), key, alive(boss),
                            SpawnRules.cooldownLeft(lastSpawn(at.getWorld(), boss), boss.cooldownMinutes(), now),
                            structureLeft)) {
                        int stars = SpawnRules.stars(at.distance(at.getWorld().getSpawnLocation()), true,
                                roster.get().spawning(), ThreadLocalRandom.current()::nextDouble);
                        if (appear(boss, stars, spotNear(player, box), "guardian of " + key)) {
                            structure.getPersistentDataContainer().set(GUARDIAN_LAST, PersistentDataType.LONG, now);
                        }
                        return; // one guardian per check is plenty
                    }
                }
            }
        }
    }

    private boolean appear(BossDefinition boss, int stars, Location at, String why) {
        BossVariant variant = BossRoster.pickVariant(boss, ThreadLocalRandom.current()::nextDouble);
        if (!spawn.spawn(boss, variant, stars, at, why)) {
            return false;
        }
        at.getWorld().getPersistentDataContainer().set(lastKey(boss), PersistentDataType.LONG, System.currentTimeMillis());
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
