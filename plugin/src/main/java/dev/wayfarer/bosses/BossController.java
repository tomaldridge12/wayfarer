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
        final Set<UUID> minions = new HashSet<>();
        final AbilityPlanner planner;
        long ticks;
        boolean phaseTwo;
        long shieldedUntil = -1;
        int ticksWalkingHome;
        int ticksAlone;

        Live(LivingEntity entity, BossMark mark, BossDefinition boss, BossVariant variant, BossBar bar) {
            this.entity = entity;
            this.mark = mark;
            this.boss = boss;
            this.variant = variant;
            this.bar = bar;
            this.planner = new AbilityPlanner(variant.abilities(), mark.stars());
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

    static final int PERIOD = 10;

    /** Runs twice a second: the fight (abilities, phase, leash, heal), then boss bars; unloaded bosses are let go. */
    void tick() {
        for (Live boss : new ArrayList<>(live.values())) {
            if (!boss.entity.isValid()) {
                release(boss);
                continue;
            }
            boss.ticks += PERIOD;
            fight(boss);
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

    private void fight(Live boss) {
        LivingEntity e = boss.entity;
        boss.minions.removeIf(id -> {
            Entity m = server.getEntity(id);
            return m == null || !m.isValid();
        });
        if (boss.shieldedUntil >= 0 && (boss.minions.isEmpty() || boss.ticks >= boss.shieldedUntil)) {
            boss.shieldedUntil = -1;
            e.setInvulnerable(false);
        }
        if (boss.shieldedUntil >= 0) {
            e.getWorld().spawnParticle(org.bukkit.Particle.ENCHANT, e.getLocation().add(0, 1, 0), 15, 0.6, 1, 0.6, 0.5);
        }
        Player target = target(boss);
        boolean anyone = target != null || e.getWorld().getPlayers().stream().anyMatch(p ->
                p.getLocation().distanceSquared(e.getLocation()) <= FightRules.ABANDON_RANGE * FightRules.ABANDON_RANGE);
        boss.ticksAlone = anyone ? 0 : boss.ticksAlone + PERIOD;
        var maxAttr = e.getAttribute(Attribute.MAX_HEALTH);
        double max = maxAttr == null ? e.getHealth() : maxAttr.getValue();
        if (FightRules.shouldHeal(boss.ticksAlone) && e.getHealth() < max) {
            heal(boss, max);
            return;
        }
        if (!boss.phaseTwo && FightRules.phaseTwo(e.getHealth(), max)) {
            enrage(boss);
        }
        org.bukkit.Location home = new org.bukkit.Location(e.getWorld(), boss.mark.homeX() + 0.5, boss.mark.homeY(),
                boss.mark.homeZ() + 0.5);
        switch (FightRules.leash(e.getLocation().distance(home), boss.ticksWalkingHome)) {
            case TELEPORT_HOME -> {
                e.teleport(home);
                boss.ticksWalkingHome = 0;
                return;
            }
            case WALK_HOME -> {
                if (e instanceof org.bukkit.entity.Mob mob) {
                    mob.setTarget(null);
                    mob.getPathfinder().moveTo(home, 1.3);
                }
                boss.ticksWalkingHome += PERIOD;
                return;
            }
            case STAY -> boss.ticksWalkingHome = 0;
        }
        if (target == null) {
            return;
        }
        double distance = target.getLocation().distance(e.getLocation());
        double damage = boss.boss.damage() * roster.get().stars().damage(boss.mark.stars());
        boss.planner.next(boss.ticks, distance, boss.phaseTwo).ifPresent(ability -> {
            if (AbilityEffects.use(ability, boss, target, damage)) {
                boss.planner.used(ability, boss.ticks);
            }
        });
    }

    /** The boss's player target, picking the nearest within 24 blocks when it has none. */
    private Player target(Live boss) {
        if (!(boss.entity instanceof org.bukkit.entity.Mob mob)) {
            return null;
        }
        if (mob.getTarget() instanceof Player p && AbilityEffects.hittable(p)
                && p.getLocation().distanceSquared(mob.getLocation()) <= 32 * 32) {
            return p;
        }
        Player nearest = null;
        double best = 24 * 24;
        for (Player p : mob.getWorld().getPlayers()) {
            double d = p.getLocation().distanceSquared(mob.getLocation());
            if (AbilityEffects.hittable(p) && d <= best) {
                best = d;
                nearest = p;
            }
        }
        mob.setTarget(nearest);
        return nearest;
    }

    private static final org.bukkit.NamespacedKey ENRAGE_SPEED = new org.bukkit.NamespacedKey("wayfarer", "enrage_speed");
    private static final org.bukkit.NamespacedKey ENRAGE_DAMAGE = new org.bukkit.NamespacedKey("wayfarer", "enrage_damage");

    private void enrage(Live boss) {
        boss.phaseTwo = true;
        addModifier(boss.entity, Attribute.MOVEMENT_SPEED, ENRAGE_SPEED, 0.25);
        addModifier(boss.entity, Attribute.ATTACK_DAMAGE, ENRAGE_DAMAGE, 0.2);
        boss.bar.color(net.kyori.adventure.bossbar.BossBar.Color.PURPLE);
        boss.entity.getWorld().spawnParticle(org.bukkit.Particle.ANGRY_VILLAGER, boss.entity.getLocation().add(0, 2, 0),
                12, 0.6, 0.4, 0.6, 0);
        boss.entity.getWorld().playSound(boss.entity.getLocation(), org.bukkit.Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.4f);
        for (UUID id : boss.viewers) {
            Player p = server.getPlayer(id);
            if (p != null) {
                p.sendActionBar(BossText.enraged(boss.variant));
            }
        }
    }

    private void heal(Live boss, double max) {
        boss.entity.setHealth(max);
        boss.phaseTwo = false;
        boss.ticksAlone = 0;
        removeModifier(boss.entity, Attribute.MOVEMENT_SPEED, ENRAGE_SPEED);
        removeModifier(boss.entity, Attribute.ATTACK_DAMAGE, ENRAGE_DAMAGE);
        boss.bar.color(net.kyori.adventure.bossbar.BossBar.Color.RED);
        boss.damagers.clear();
        removeMinions(boss);
    }

    private static void addModifier(LivingEntity e, Attribute attribute, org.bukkit.NamespacedKey key, double scalar) {
        var a = e.getAttribute(attribute);
        if (a != null && a.getModifier(key) == null) {
            a.addTransientModifier(new org.bukkit.attribute.AttributeModifier(key, scalar,
                    org.bukkit.attribute.AttributeModifier.Operation.ADD_SCALAR));
        }
    }

    private static void removeModifier(LivingEntity e, Attribute attribute, org.bukkit.NamespacedKey key) {
        var a = e.getAttribute(attribute);
        if (a != null && a.getModifier(key) != null) {
            a.removeModifier(key);
        }
    }

    private void removeMinions(Live boss) {
        for (UUID id : boss.minions) {
            Entity m = server.getEntity(id);
            if (m != null) {
                m.remove();
            }
        }
        boss.minions.clear();
    }

    private void release(Live boss) {
        live.remove(boss.entity.getUniqueId());
        removeMinions(boss);
        boss.entity.setInvulnerable(false);
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
