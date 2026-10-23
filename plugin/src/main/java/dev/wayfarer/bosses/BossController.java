package dev.wayfarer.bosses;

import dev.wayfarer.core.BossDefeatedEvent;
import dev.wayfarer.core.BossMark;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.world.EntitiesLoadEvent;

/**
 * Tracks bosses alive in loaded chunks: their boss bars, who damaged them, and their defeat. Bosses are
 * persistent entities carrying the core boss mark, so after a restart or chunk load they are picked up again.
 */
final class BossController implements Listener {
    static final double BAR_RANGE = 48;

    /** One boss alive in a loaded chunk. */
    static final class Live {
        final LivingEntity entity;
        final BossMark mark;
        final BossDefinition boss;
        final BossVariant variant;
        final BossBar bar;
        final Set<UUID> viewers = new HashSet<>();
        final Set<UUID> damagers = new LinkedHashSet<>();

        Live(LivingEntity entity, BossMark mark, BossDefinition boss, BossVariant variant, BossBar bar) {
            this.entity = entity;
            this.mark = mark;
            this.boss = boss;
            this.variant = variant;
            this.bar = bar;
        }
    }

    private final Server server;
    private final Supplier<BossRoster> roster;
    private final BossSpawner spawner;
    private final Logger log;
    private final Map<UUID, Live> live = new HashMap<>();

    BossController(Server server, Supplier<BossRoster> roster, BossSpawner spawner, Logger log) {
        this.server = server;
        this.roster = roster;
        this.spawner = spawner;
        this.log = log;
    }

    Map<UUID, Live> live() {
        return live;
    }

    /** Starts tracking a marked boss entity; bosses whose id config no longer has are left alone. */
    void attach(LivingEntity entity) {
        if (live.containsKey(entity.getUniqueId()) || entity.isDead()) {
            return;
        }
        Optional<BossMark> mark = BossMark.read(entity);
        if (mark.isEmpty()) {
            return;
        }
        Optional<BossDefinition> boss = roster.get().find(mark.get().bossId());
        Optional<BossVariant> variant = boss.flatMap(b -> b.variant(mark.get().variant()));
        if (variant.isEmpty()) {
            log.warning("[bosses] Boss " + mark.get().bossId() + "/" + mark.get().variant()
                    + " is not in config; left as a plain mob");
            return;
        }
        BossBar bar = BossBar.bossBar(BossText.name(variant.get(), mark.get().stars()), 1f, BossBar.Color.RED,
                BossBar.Overlay.NOTCHED_10);
        live.put(entity.getUniqueId(), new Live(entity, mark.get(), boss.get(), variant.get(), bar));
    }

    void attachLoaded() {
        for (World world : server.getWorlds()) {
            for (LivingEntity e : world.getLivingEntities()) {
                attach(e);
            }
        }
    }

    /** Runs twice a second: boss bars follow health and nearby players; unloaded bosses are let go. */
    void tick() {
        for (Live boss : new ArrayList<>(live.values())) {
            if (!boss.entity.isValid()) {
                release(boss);
                continue;
            }
            var max = boss.entity.getAttribute(Attribute.MAX_HEALTH);
            float progress = (float) Math.max(0, Math.min(1, boss.entity.getHealth() / (max == null ? 1 : max.getValue())));
            boss.bar.progress(progress);
            Set<UUID> near = new HashSet<>();
            for (Player p : boss.entity.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(boss.entity.getLocation()) <= BAR_RANGE * BAR_RANGE) {
                    near.add(p.getUniqueId());
                    if (boss.viewers.add(p.getUniqueId())) {
                        p.showBossBar(boss.bar);
                    }
                }
            }
            for (UUID id : new ArrayList<>(boss.viewers)) {
                if (!near.contains(id)) {
                    boss.viewers.remove(id);
                    Player p = server.getPlayer(id);
                    if (p != null) {
                        p.hideBossBar(boss.bar);
                    }
                }
            }
        }
    }

    private void release(Live boss) {
        live.remove(boss.entity.getUniqueId());
        for (UUID id : boss.viewers) {
            Player p = server.getPlayer(id);
            if (p != null) {
                p.hideBossBar(boss.bar);
            }
        }
        boss.viewers.clear();
    }

    /** Removes every tracked boss from the world (operator tool and tests); returns how many. */
    int removeAll() {
        int n = 0;
        for (Live boss : new ArrayList<>(live.values())) {
            release(boss);
            spawner.unglow(boss.entity);
            boss.entity.remove();
            n++;
        }
        return n;
    }

    void clear() {
        new ArrayList<>(live.values()).forEach(this::release);
    }

    @EventHandler
    void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity e : event.getEntities()) {
            if (e instanceof LivingEntity le) {
                attach(le);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onDamage(EntityDamageByEntityEvent event) {
        Live victim = live.get(event.getEntity().getUniqueId());
        if (victim != null) {
            Entity d = event.getDamager();
            Entity credited = d instanceof Projectile p && p.getShooter() instanceof Entity s ? s : d;
            if (credited instanceof Player player) {
                victim.damagers.add(player.getUniqueId());
            }
        }
    }

    /** A boss's arrows and other projectiles hit as hard as its stars say; melee uses its attack attribute. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onBossProjectile(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Projectile p && p.getShooter() instanceof LivingEntity shooter) {
            Live boss = live.get(shooter.getUniqueId());
            if (boss != null) {
                event.setDamage(event.getDamage() * roster.get().stars().damage(boss.mark.stars()));
            }
        }
    }

    /** Boss creepers never blow themselves up; their abilities do the damage. */
    @EventHandler(ignoreCancelled = true)
    void onPrime(ExplosionPrimeEvent event) {
        if (live.containsKey(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    void onDeath(EntityDeathEvent event) {
        Live boss = live.get(event.getEntity().getUniqueId());
        if (boss == null) {
            return;
        }
        release(boss);
        spawner.unglow(boss.entity);
        log.info("[bosses] Defeated " + boss.boss.id() + "/" + boss.variant.id() + " ★" + boss.mark.stars()
                + " by " + boss.damagers.size() + " player(s)");
        server.getPluginManager().callEvent(new BossDefeatedEvent(boss.mark, boss.entity.getLocation(),
                new ArrayList<>(boss.damagers)));
    }
}
