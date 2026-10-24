package dev.wayfarer.curios;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Tag;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

/**
 * Charms at work: once a second, each online player's carried curios are read and the ones whose conditions hold are
 * kept as "working" (potions and attribute modifiers follow them); a quick task handles falls and magnets; event
 * handlers apply immunities, ignoring mobs, drop and experience bonuses. Main thread.
 */
final class CurioEffects implements Listener {
    /** Long enough that night vision never flickers between refreshes. */
    private static final int POTION_TICKS = 300;

    private final Plugin plugin;
    private final Supplier<CurioRules> rules;
    private final CurioItems items;
    private final Predicate<ItemStack> alloyGear;
    private final Map<UUID, List<CurioDefinition>> working = new HashMap<>();
    private final Map<UUID, Set<PotionEffectType>> potions = new HashMap<>();
    private final Map<UUID, Set<String>> modifiers = new HashMap<>();
    private long seconds;
    private BukkitTask slow;
    private BukkitTask fast;

    CurioEffects(Plugin plugin, Supplier<CurioRules> rules, CurioItems items, Predicate<ItemStack> alloyGear) {
        this.plugin = plugin;
        this.rules = rules;
        this.items = items;
        this.alloyGear = alloyGear;
    }

    void start() {
        slow = plugin.getServer().getScheduler().runTaskTimer(plugin, this::everySecond, 20, 20);
        fast = plugin.getServer().getScheduler().runTaskTimer(plugin, this::quick, 4, 4);
    }

    /** Cancels the tasks and takes back every potion and modifier curios gave. */
    void stop() {
        if (slow != null) {
            slow.cancel();
        }
        if (fast != null) {
            fast.cancel();
        }
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            clear(p);
        }
        working.clear();
    }

    /** The charms working for this player as of the last second's check. */
    List<CurioDefinition> working(UUID player) {
        return working.getOrDefault(player, List.of());
    }

    /** Curio ids anywhere in the player's inventory and on their cursor, in slot order. */
    List<String> carried(Player player) {
        List<String> ids = new ArrayList<>();
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == CurioItems.BASE) {
                items.idOf(stack).ifPresent(ids::add);
            }
        }
        items.idOf(player.getItemOnCursor()).ifPresent(ids::add);
        return ids;
    }

    private void everySecond() {
        seconds++;
        CurioRules r = rules.get();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            List<String> carried = carried(player);
            List<CurioDefinition> now = carried.isEmpty() ? List.of()
                    : r.working(carried, new PaperCurioFacts(player, alloyGear));
            working.put(player.getUniqueId(), now);
            applyPotions(player, now);
            applyModifiers(player, now);
            for (Effect.Grow grow : CurioRules.effects(now, Effect.Grow.class)) {
                if (seconds % grow.everySeconds() == 0) {
                    grow(player, grow.radius());
                }
            }
        }
    }

    private void applyPotions(Player player, List<CurioDefinition> now) {
        Set<PotionEffectType> wanted = new HashSet<>();
        for (Effect.Potion p : CurioRules.effects(now, Effect.Potion.class)) {
            for (String key : p.potions()) {
                PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(key));
                if (type == null) {
                    continue;
                }
                wanted.add(type);
                PotionEffect current = player.getPotionEffect(type);
                if (current == null || (current.isAmbient() && current.getDuration() < POTION_TICKS - 40)) {
                    player.addPotionEffect(new PotionEffect(type, POTION_TICKS, p.amplifier(), true, false, true));
                }
            }
        }
        Set<PotionEffectType> given = potions.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());
        for (PotionEffectType type : new ArrayList<>(given)) {
            if (!wanted.contains(type)) {
                PotionEffect current = player.getPotionEffect(type);
                if (current != null && current.isAmbient() && current.getDuration() <= POTION_TICKS) {
                    player.removePotionEffect(type);
                }
                given.remove(type);
            }
        }
        given.addAll(wanted);
    }

    private void applyModifiers(Player player, List<CurioDefinition> now) {
        Set<String> wanted = new HashSet<>();
        for (CurioDefinition c : now) {
            if (c.effect() instanceof Effect.Attribute a) {
                wanted.add(c.id());
                AttributeInstance instance = instance(player, a.attribute());
                NamespacedKey key = modifierKey(c.id());
                if (instance != null && instance.getModifier(key) == null) {
                    instance.addTransientModifier(new AttributeModifier(key, a.amount(), operation(a.operation())));
                }
            }
        }
        Set<String> given = modifiers.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());
        for (String id : new ArrayList<>(given)) {
            if (!wanted.contains(id)) {
                removeModifier(player, id);
                given.remove(id);
            }
        }
        given.addAll(wanted);
    }

    private void removeModifier(Player player, String curio) {
        rules.get().curio(curio).ifPresentOrElse(c -> {
            if (c.effect() instanceof Effect.Attribute a) {
                AttributeInstance instance = instance(player, a.attribute());
                if (instance != null) {
                    instance.removeModifier(modifierKey(curio));
                }
            }
        }, () -> Registry.ATTRIBUTE.forEach(attr -> {
            AttributeInstance instance = player.getAttribute(attr);
            if (instance != null) {
                instance.removeModifier(modifierKey(curio));
            }
        }));
    }

    private void clear(Player player) {
        applyPotions(player, List.of());
        applyModifiers(player, List.of());
        potions.remove(player.getUniqueId());
        modifiers.remove(player.getUniqueId());
    }

    private static AttributeInstance instance(Player player, String attribute) {
        var attr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(attribute));
        return attr == null ? null : player.getAttribute(attr);
    }

    private static NamespacedKey modifierKey(String curio) {
        return new NamespacedKey("wayfarer", "curio_" + curio);
    }

    private static AttributeModifier.Operation operation(String op) {
        return switch (op) {
            case "multiply_base" -> AttributeModifier.Operation.ADD_SCALAR;
            case "multiply_total" -> AttributeModifier.Operation.MULTIPLY_SCALAR_1;
            default -> AttributeModifier.Operation.ADD_NUMBER;
        };
    }

    /** One random crop grows a stage, or a sapling gets a nudge, somewhere near the player. */
    private void grow(Player player, int radius) {
        Block centre = player.getLocation().getBlock();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 24; attempt++) {
            Block b = centre.getRelative(random.nextInt(-radius, radius + 1), random.nextInt(-2, 3),
                    random.nextInt(-radius, radius + 1));
            if (b.getBlockData() instanceof Ageable crop && Tag.CROPS.isTagged(b.getType())
                    && crop.getAge() < crop.getMaximumAge()) {
                crop.setAge(crop.getAge() + 1);
                b.setBlockData(crop);
                b.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, b.getLocation().add(0.5, 0.5, 0.5), 4, 0.25, 0.2, 0.25);
                return;
            }
            if (Tag.SAPLINGS.isTagged(b.getType())) {
                if (random.nextInt(4) == 0) {
                    b.applyBoneMeal(BlockFace.UP);
                }
                return;
            }
        }
    }

    private void quick() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            List<CurioDefinition> now = working(player.getUniqueId());
            if (now.isEmpty()) {
                continue;
            }
            for (Effect.FallGuard guard : CurioRules.effects(now, Effect.FallGuard.class)) {
                if (player.getFallDistance() > guard.distance() && !player.isGliding()
                        && !player.hasPotionEffect(PotionEffectType.SLOW_FALLING)) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 60, 0, true, false, true));
                    player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 12, 0.4, 0.1, 0.4, 0.02);
                }
            }
            if (player.isSneaking()) {
                continue;
            }
            for (Effect.Magnet magnet : CurioRules.effects(now, Effect.Magnet.class)) {
                Location target = player.getLocation().add(0, 0.6, 0);
                for (Entity e : player.getNearbyEntities(magnet.radius(), magnet.radius(), magnet.radius())) {
                    if (e instanceof Item item && item.getPickupDelay() <= 0 && item.isValid()) {
                        var pull = target.toVector().subtract(item.getLocation().toVector());
                        if (pull.lengthSquared() > 0.25) {
                            item.setVelocity(pull.normalize().multiply(0.35));
                        }
                    }
                }
            }
        }
    }

    // --- event effects ---

    @EventHandler
    void onQuit(PlayerQuitEvent event) {
        clear(event.getPlayer());
        working.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onPotion(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getNewEffect() == null) {
            return;
        }
        String type = event.getModifiedType().getKey().getKey();
        String cause = event.getCause().name().toLowerCase(java.util.Locale.ROOT);
        String source = sourceMob(event.getSource());
        for (Effect.Immunity im : CurioRules.effects(working(player.getUniqueId()), Effect.Immunity.class)) {
            boolean byEffect = im.effects().contains(type) && (im.causes().isEmpty() || im.causes().contains(cause));
            boolean bySplash = source != null && im.splashFrom().contains(source);
            if (byEffect || bySplash) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /** A splash potion thrown by a mob some carrier is immune to touches them not at all. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onSplash(PotionSplashEvent event) {
        String thrower = sourceMob(event.getPotion());
        if (thrower == null) {
            return;
        }
        for (LivingEntity hit : event.getAffectedEntities()) {
            if (hit instanceof Player player && CurioRules.effects(working(player.getUniqueId()), Effect.Immunity.class)
                    .stream().anyMatch(im -> im.splashFrom().contains(thrower))) {
                event.setIntensity(player, 0);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            String cause = event.getCause().name().toLowerCase(java.util.Locale.ROOT);
            if (CurioRules.effects(working(player.getUniqueId()), Effect.Immunity.class).stream()
                    .anyMatch(im -> im.damage().contains(cause))) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getTarget() instanceof Player player)) {
            return;
        }
        String mob = event.getEntityType().getKey().getKey();
        boolean struck = event.getReason() == EntityTargetEvent.TargetReason.TARGET_ATTACKED_ENTITY
                || event.getReason() == EntityTargetEvent.TargetReason.TARGET_ATTACKED_NEARBY_ENTITY;
        for (Effect.Ignored ig : CurioRules.effects(working(player.getUniqueId()), Effect.Ignored.class)) {
            if (ig.mobs().contains(mob) && (ig.evenWhenHit() || !struck)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onDrop(BlockDropItemEvent event) {
        if (event.getItems().isEmpty()) {
            return; // the wrong tool, or creative: nothing dropped, so no bonus either
        }
        String block = event.getBlockState().getType().getKey().getKey();
        for (Effect.DropBonus bonus : CurioRules.effects(working(event.getPlayer().getUniqueId()), Effect.DropBonus.class)) {
            int extra = CurioRules.dropBonus(bonus, block, ThreadLocalRandom.current().nextDouble());
            org.bukkit.Material item = org.bukkit.Material.matchMaterial(bonus.item());
            if (extra > 0 && item != null) {
                Location at = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
                at.getWorld().dropItemNaturally(at, ItemStack.of(item, extra));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            event.setExpToDrop(CurioRules.xp(event.getExpToDrop(),
                    CurioRules.effects(working(event.getPlayer().getUniqueId()), Effect.XpBonus.class), "fishing"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onBreak(BlockBreakEvent event) {
        if (event.getExpToDrop() > 0) {
            event.setExpToDrop(CurioRules.xp(event.getExpToDrop(),
                    CurioRules.effects(working(event.getPlayer().getUniqueId()), Effect.XpBonus.class), "ores"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null && !(event.getEntity() instanceof Player)) {
            event.setDroppedExp(CurioRules.xp(event.getDroppedExp(),
                    CurioRules.effects(working(killer.getUniqueId()), Effect.XpBonus.class), "mobs"));
        }
    }

    /** The mob behind a potion: its thrower for a thrown potion, else the entity itself. */
    private static String sourceMob(Entity source) {
        Entity mob = source instanceof Projectile p && p.getShooter() instanceof Entity shooter ? shooter : source;
        return mob == null || mob instanceof Player ? null : mob.getType().getKey().getKey();
    }
}
