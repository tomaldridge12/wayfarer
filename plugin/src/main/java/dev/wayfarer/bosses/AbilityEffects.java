package dev.wayfarer.bosses;

import dev.wayfarer.core.MinionMark;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/** What each ability does in the world. Abilities hurt players directly; none breaks or places blocks. */
final class AbilityEffects {
    /** The minion each boss entity summons. */
    private static final Map<String, String> MINIONS = Map.ofEntries(
            Map.entry("zombie", "zombie"), Map.entry("husk", "husk"), Map.entry("skeleton", "skeleton"),
            Map.entry("stray", "stray"), Map.entry("wither_skeleton", "skeleton"), Map.entry("spider", "cave_spider"),
            Map.entry("cave_spider", "cave_spider"), Map.entry("creeper", "creeper"), Map.entry("enderman", "endermite"),
            Map.entry("witch", "vex"), Map.entry("drowned", "drowned"), Map.entry("piglin_brute", "piglin"));

    private AbilityEffects() {
    }

    /** Uses an ability on the target; returns false if it could not (so its cooldown is not spent). */
    static boolean use(Ability ability, BossController.Live boss, Player target, double damage) {
        LivingEntity e = boss.entity;
        Location at = e.getLocation();
        return switch (ability) {
            case CHARGE -> {
                Vector dir = target.getLocation().toVector().subtract(at.toVector()).setY(0).normalize();
                e.setVelocity(dir.multiply(1.6).setY(0.35));
                e.getWorld().playSound(at, Sound.ENTITY_RAVAGER_ROAR, 1f, 1.2f);
                e.getWorld().spawnParticle(Particle.CLOUD, at, 20, 0.4, 0.1, 0.4, 0.05);
                yield true;
            }
            case GROUND_SLAM -> {
                e.getWorld().spawnParticle(Particle.EXPLOSION, at, 3, 1, 0.2, 1, 0);
                e.getWorld().playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.7f);
                for (Player p : e.getWorld().getPlayers()) {
                    if (p.getLocation().distanceSquared(at) <= 25 && hittable(p)) {
                        p.damage(damage * 1.2, e);
                        Vector away = p.getLocation().toVector().subtract(at.toVector()).setY(0);
                        p.setVelocity((away.lengthSquared() > 0 ? away.normalize() : away).multiply(1.1).setY(0.6));
                    }
                }
                yield true;
            }
            case SUMMON_MINIONS -> summon(boss);
            case POISON_CLOUD -> {
                e.getWorld().spawn(target.getLocation(), AreaEffectCloud.class, c -> {
                    c.setRadius(3f);
                    c.setDuration(120);
                    c.setRadiusPerTick(-0.01f);
                    c.setSource(e);
                    c.setColor(org.bukkit.Color.fromRGB(0x5E8C2A));
                    c.addCustomEffect(new PotionEffect(PotionEffectType.POISON, 100, 1), true);
                });
                e.getWorld().playSound(at, Sound.ENTITY_WITCH_THROW, 1f, 0.6f);
                yield true;
            }
            case ARROW_VOLLEY -> {
                Vector aim = target.getEyeLocation().toVector().subtract(e.getEyeLocation().toVector()).normalize();
                for (int i = -2; i <= 2; i++) {
                    Vector spread = aim.clone().rotateAroundY(Math.toRadians(i * 7)).multiply(1.8);
                    Arrow arrow = e.launchProjectile(Arrow.class, spread);
                    arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                }
                e.getWorld().playSound(at, Sound.ENTITY_SKELETON_SHOOT, 1f, 0.8f);
                yield true;
            }
            case BLINK -> {
                Location behind = target.getLocation().subtract(target.getLocation().getDirection().setY(0).normalize().multiply(2));
                behind.setDirection(target.getLocation().toVector().subtract(behind.toVector()));
                if (!behind.getBlock().isPassable() || !behind.clone().add(0, 1, 0).getBlock().isPassable()) {
                    yield false;
                }
                e.getWorld().spawnParticle(Particle.PORTAL, at, 40, 0.4, 1, 0.4, 0.5);
                e.teleport(behind);
                e.getWorld().playSound(behind, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.8f);
                yield true;
            }
            case FIRE_RING -> {
                for (int i = 0; i < 36; i++) {
                    double a = Math.toRadians(i * 10);
                    e.getWorld().spawnParticle(Particle.FLAME, at.clone().add(Math.cos(a) * 5, 0.3, Math.sin(a) * 5), 2, 0, 0.2, 0, 0.01);
                }
                e.getWorld().playSound(at, Sound.ITEM_FIRECHARGE_USE, 1f, 0.6f);
                for (Player p : e.getWorld().getPlayers()) {
                    if (p.getLocation().distanceSquared(at) <= 36 && hittable(p)) {
                        p.damage(damage * 0.8, e);
                        p.setFireTicks(80);
                    }
                }
                yield true;
            }
            case PULL -> {
                for (Player p : e.getWorld().getPlayers()) {
                    double d2 = p.getLocation().distanceSquared(at);
                    if (d2 <= 400 && d2 > 9 && hittable(p)) {
                        Vector in = at.toVector().subtract(p.getLocation().toVector()).normalize();
                        p.setVelocity(in.multiply(1.4).setY(0.4));
                    }
                }
                e.getWorld().spawnParticle(Particle.REVERSE_PORTAL, at, 60, 1.5, 1, 1.5, 0.2);
                e.getWorld().playSound(at, Sound.ENTITY_EVOKER_CAST_SPELL, 1f, 0.6f);
                yield true;
            }
            case SHIELD -> {
                if (!summon(boss)) {
                    yield false;
                }
                boss.shieldedUntil = boss.ticks + 300; // up to 15 s, or until its minions die
                e.setInvulnerable(true);
                e.getWorld().playSound(at, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.4f);
                yield true;
            }
            case ENRAGE -> false;
        };
    }

    private static boolean summon(BossController.Live boss) {
        LivingEntity e = boss.entity;
        int room = FightRules.MAX_MINIONS - boss.minions.size();
        int count = Math.min(room, FightRules.minionCount(boss.mark.stars()));
        String kind = MINIONS.get(boss.variant.entity());
        EntityType type = kind == null ? null : Registry.ENTITY_TYPE.get(NamespacedKey.minecraft(kind));
        if (count <= 0 || type == null) {
            return false;
        }
        for (int i = 0; i < count; i++) {
            double a = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            Location at = e.getLocation().add(Math.cos(a) * 2.5, 0, Math.sin(a) * 2.5);
            LivingEntity minion = (LivingEntity) e.getWorld().spawn(at, type.getEntityClass(),
                    CreatureSpawnEvent.SpawnReason.CUSTOM, m -> {
                        MinionMark.write(m, e);
                        if (m instanceof Mob mob) {
                            mob.setRemoveWhenFarAway(true);
                        }
                        if (m instanceof Creeper c) {
                            c.setExplosionRadius(1); // small bangs, not crater-makers
                        }
                    });
            boss.minions.add(minion.getUniqueId());
        }
        e.getWorld().spawnParticle(Particle.SOUL, e.getLocation(), 30, 1, 0.5, 1, 0.02);
        e.getWorld().playSound(e.getLocation(), Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1f, 0.8f);
        return true;
    }

    /** Players in survival or adventure; spectators and creative players are left alone. */
    static boolean hittable(Player p) {
        return !p.isDead() && (p.getGameMode() == org.bukkit.GameMode.SURVIVAL
                || p.getGameMode() == org.bukkit.GameMode.ADVENTURE);
    }
}
