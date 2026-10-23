package dev.wayfarer.bosses;

import dev.wayfarer.core.BossMark;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.PiglinAbstract;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/** Builds a boss from its definition: stats scaled by stars, look per variant, the core boss mark. */
final class BossSpawner {
    private static final Map<String, EquipmentSlot> SLOTS = Map.of("head", EquipmentSlot.HEAD, "chest",
            EquipmentSlot.CHEST, "legs", EquipmentSlot.LEGS, "feet", EquipmentSlot.FEET, "hand", EquipmentSlot.HAND,
            "offhand", EquipmentSlot.OFF_HAND);

    private final Scoreboard scoreboard;

    BossSpawner(Scoreboard scoreboard) {
        this.scoreboard = scoreboard;
    }

    LivingEntity spawn(BossDefinition boss, BossVariant variant, int stars, StarScale scale, Location at) {
        EntityType type = Registry.ENTITY_TYPE.get(NamespacedKey.minecraft(variant.entity()));
        Class<? extends LivingEntity> cls = type.getEntityClass().asSubclass(LivingEntity.class);
        int s = StarScale.clamp(stars);
        return at.getWorld().spawn(at, cls, CreatureSpawnEvent.SpawnReason.CUSTOM, e -> {
            set(e, Attribute.MAX_HEALTH, boss.health() * scale.health(s));
            e.setHealth(boss.health() * scale.health(s));
            set(e, Attribute.ATTACK_DAMAGE, boss.damage() * scale.damage(s));
            set(e, Attribute.SCALE, variant.scale());
            set(e, Attribute.KNOCKBACK_RESISTANCE, 0.6);
            set(e, Attribute.FOLLOW_RANGE, 40);
            e.customName(BossText.name(variant, s));
            e.setCustomNameVisible(true);
            e.setPersistent(true);
            e.setRemoveWhenFarAway(false);
            e.setCanPickupItems(false);
            e.setGlowing(true);
            if (e instanceof Ageable baby) {
                baby.setAdult();
            }
            if (e instanceof Zombie z) {
                z.setShouldBurnInDay(false);
            }
            if (e instanceof AbstractSkeleton sk) {
                sk.setShouldBurnInDay(false);
            }
            if (e instanceof PiglinAbstract piglin) {
                piglin.setImmuneToZombification(true);
            }
            equip(e, variant);
            glow(e, variant.glow());
            new BossMark(boss.id(), boss.family(), variant.id(), s, at.getBlockX(), at.getBlockY(), at.getBlockZ()).write(e);
        });
    }

    private static void equip(LivingEntity e, BossVariant variant) {
        EntityEquipment gear = e.getEquipment();
        if (gear == null) {
            return;
        }
        variant.equipment().forEach((slot, item) -> {
            EquipmentSlot s = SLOTS.get(slot);
            gear.setItem(s, new ItemStack(Material.matchMaterial(item)));
            gear.setDropChance(s, 0f); // boss loot comes from Wayfarer's rolls, not dropped gear
        });
    }

    /** Glow colour comes from a scoreboard team per colour. */
    private void glow(LivingEntity e, String colour) {
        String name = "wayfarer_glow_" + colour.toLowerCase(Locale.ROOT);
        Team team = scoreboard.getTeam(name);
        if (team == null) {
            team = scoreboard.registerNewTeam(name);
            team.color(NamedTextColor.NAMES.value(colour));
        }
        team.addEntity(e);
    }

    void unglow(LivingEntity e) {
        Team team = scoreboard.getEntityTeam(e);
        if (team != null && team.getName().startsWith("wayfarer_glow_")) {
            team.removeEntity(e);
        }
    }

    private static void set(LivingEntity e, Attribute attribute, double value) {
        AttributeInstance a = e instanceof Mob ? e.getAttribute(attribute) : null;
        if (a != null) {
            a.setBaseValue(value);
        }
    }
}
