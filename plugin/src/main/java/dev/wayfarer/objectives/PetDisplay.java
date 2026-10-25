package dev.wayfarer.objectives;

import dev.wayfarer.core.ItemLook;
import dev.wayfarer.core.PlayerStore;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * A worn pet: a baby vanilla mob that walks after its owner, or a small pack model that floats beside them. One per
 * player, never saved with the world, gone while the owner is away, dead or riding, and never damaged, leashed,
 * sheared, fed or allowed to change a block. Everything runs on the main thread; following is throttled to a few
 * times a second so it costs nothing.
 */
final class PetDisplay implements Listener {
    private static final NamespacedKey OWNER = new NamespacedKey("wayfarer", "pet_owner");
    private static final long FOLLOW_TICKS = 10;
    private static final long MODEL_TICKS = 2;
    private static final double STROLL_FROM = 3.5;
    private static final double TELEPORT_FROM = 14;

    private record Pet(Entity entity, Cosmetic.Pet look) {
    }

    private final JavaPlugin plugin;
    private final PlayerStore<ObjectivesPlayerData> store;
    private final Supplier<Map<String, Cosmetic>> cosmetics;
    private final Map<UUID, Pet> pets = new HashMap<>();
    private BukkitTask follow;
    private BukkitTask glide;
    private long age;

    PetDisplay(JavaPlugin plugin, PlayerStore<ObjectivesPlayerData> store, Supplier<Map<String, Cosmetic>> cosmetics) {
        this.plugin = plugin;
        this.store = store;
        this.cosmetics = cosmetics;
    }

    void start() {
        plugin.getServer().getOnlinePlayers().forEach(this::refresh);
        follow = plugin.getServer().getScheduler().runTaskTimer(plugin, this::followOwners, FOLLOW_TICKS, FOLLOW_TICKS);
        glide = plugin.getServer().getScheduler().runTaskTimer(plugin, this::glideModels, MODEL_TICKS, MODEL_TICKS);
    }

    void stop() {
        if (follow != null) {
            follow.cancel();
        }
        if (glide != null) {
            glide.cancel();
        }
        new ArrayList<>(pets.keySet()).forEach(this::remove);
    }

    private Optional<Cosmetic> wanted(Player player) {
        ObjectivesPlayerData data = store.cached(player.getUniqueId());
        Cosmetic c = data == null || data.pet == null ? null : cosmetics.get().get(data.pet);
        return c == null || c.pet() == null ? Optional.empty() : Optional.of(c);
    }

    /** Whether the owner can have a pet right now: online, alive and on their own feet. */
    private static boolean present(Player player) {
        return player.isOnline() && !player.isDead() && !player.isInsideVehicle() && player.getGameMode() != org.bukkit.GameMode.SPECTATOR;
    }

    /** Sends the old pet away and, if the player has chosen one and can have it, calls the new one. */
    void refresh(Player player) {
        remove(player.getUniqueId());
        Optional<Cosmetic> chosen = wanted(player);
        if (chosen.isEmpty() || !present(player)) {
            return;
        }
        Cosmetic.Pet look = chosen.get().pet();
        Location at = behind(player);
        Entity entity = look.model() == null ? spawnMob(player, chosen.get(), look, at) : spawnModel(player, look, at);
        if (entity != null) {
            entity.getPersistentDataContainer().set(OWNER, PersistentDataType.STRING, player.getUniqueId().toString());
            pets.put(player.getUniqueId(), new Pet(entity, look));
        }
    }

    void remove(UUID owner) {
        Pet pet = pets.remove(owner);
        if (pet != null) {
            pet.entity().remove();
        }
    }

    private static Location behind(Player player) {
        Location at = player.getLocation();
        at.add(at.getDirection().setY(0).normalize().multiply(-1.5));
        return at;
    }

    private Entity spawnMob(Player owner, Cosmetic cosmetic, Cosmetic.Pet look, Location at) {
        EntityType type = Registry.ENTITY_TYPE.get(NamespacedKey.minecraft(look.entity()));
        if (type == null || type.getEntityClass() == null || !LivingEntity.class.isAssignableFrom(type.getEntityClass())) {
            return null;
        }
        Class<? extends LivingEntity> cls = type.getEntityClass().asSubclass(LivingEntity.class);
        return owner.getWorld().spawn(at, cls, CreatureSpawnEvent.SpawnReason.CUSTOM, e -> {
            e.setPersistent(false);
            e.setRemoveWhenFarAway(false);
            e.setInvulnerable(true);
            e.setSilent(true);
            e.setCollidable(false);
            e.setCanPickupItems(false);
            if (e instanceof Ageable a) {
                if (look.baby()) {
                    a.setBaby();
                }
                a.setAgeLock(true);
                a.setBreed(false);
            } else if (look.baby() && e.getAttribute(Attribute.SCALE) != null) {
                e.getAttribute(Attribute.SCALE).setBaseValue(0.5);
            }
            if (look.customName() != null) {
                e.customName(Component.text(look.customName())); // "jeb_" makes a sheep cycle through every colour
                e.setCustomNameVisible(false);
            } else {
                e.customName(ItemLook.text(cosmetic.name(), 0xC9B8E8));
                e.setCustomNameVisible(true);
            }
        });
    }

    private Entity spawnModel(Player owner, Cosmetic.Pet look, Location at) {
        return owner.getWorld().spawn(at.add(0, 1.0, 0), ItemDisplay.class, d -> {
            ItemStack stack = ItemStack.of(Material.PAPER);
            stack.setData(DataComponentTypes.ITEM_MODEL, Key.key(look.model()));
            d.setItemStack(stack);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setBillboard(Display.Billboard.FIXED);
            float s = (float) look.scale();
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(s, s, s), new AxisAngle4f()));
            d.setPersistent(false);
            d.setTeleportDuration((int) MODEL_TICKS + 1); // the client glides between the spots we send
        });
    }

    /** Every half second: walk each mob pet toward its owner, bring it to them when it has fallen far behind, and call or send away. */
    private void followOwners() {
        for (Player owner : plugin.getServer().getOnlinePlayers()) {
            Pet pet = pets.get(owner.getUniqueId());
            boolean wanted = wanted(owner).isPresent() && present(owner);
            if (wanted != (pet != null) || pet != null && !pet.entity().isValid()) {
                refresh(owner);
                continue;
            }
            if (pet == null || pet.entity() instanceof ItemDisplay) {
                continue;
            }
            double distance = pet.entity().getWorld() != owner.getWorld() ? Double.MAX_VALUE
                    : pet.entity().getLocation().distance(owner.getLocation());
            if (distance > TELEPORT_FROM) {
                pet.entity().teleport(behind(owner));
            } else if (distance > STROLL_FROM && pet.entity() instanceof Mob mob) {
                mob.getPathfinder().moveTo(owner.getLocation(), 1.25);
            }
        }
    }

    /** Model pets float a little behind and above their owner, bobbing gently. */
    private void glideModels() {
        age += MODEL_TICKS;
        for (Map.Entry<UUID, Pet> e : pets.entrySet()) {
            if (!(e.getValue().entity() instanceof ItemDisplay display)) {
                continue;
            }
            Player owner = plugin.getServer().getPlayer(e.getKey());
            if (owner == null) {
                continue;
            }
            Location side = owner.getLocation();
            Location at = side.clone().add(side.getDirection().setY(0).normalize().multiply(-1.2));
            at.add(0, 1.3 + Math.sin(age / 8.0) * 0.12, 0);
            at.setYaw(side.getYaw() + 180);
            at.setPitch(0);
            display.teleport(at);
        }
    }

    // --- keeping a pet a pet ---

    private boolean isPet(Entity entity) {
        return entity.getPersistentDataContainer().has(OWNER, PersistentDataType.STRING);
    }

    @EventHandler
    void onJoin(PlayerJoinEvent event) {
        later(event.getPlayer());
    }

    @EventHandler
    void onQuit(PlayerQuitEvent event) {
        remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    void onDeath(PlayerDeathEvent event) {
        remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    void onRespawn(PlayerRespawnEvent event) {
        later(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onTeleport(PlayerTeleportEvent event) {
        // A short hop keeps the pet (it catches up); another world or a long jump sends it away and calls it again.
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to.getWorld() != from.getWorld() || to.distanceSquared(from) > TELEPORT_FROM * TELEPORT_FROM) {
            remove(event.getPlayer().getUniqueId());
            later(event.getPlayer());
        }
    }

    @EventHandler
    void onMount(org.bukkit.event.entity.EntityMountEvent event) {
        if (event.getEntity() instanceof Player player && pets.containsKey(player.getUniqueId())) {
            remove(player.getUniqueId());
        }
    }

    @EventHandler
    void onDismount(org.bukkit.event.entity.EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player) {
            later(player);
        }
    }

    private void later(Player player) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                refresh(player);
            }
        }, 5);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    void onDamage(EntityDamageEvent event) {
        if (isPet(event.getEntity()) && event.getCause() != EntityDamageEvent.DamageCause.VOID
                && event.getCause() != EntityDamageEvent.DamageCause.KILL) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    void onDeath(EntityDeathEvent event) {
        if (isPet(event.getEntity())) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    @EventHandler
    void onInteract(PlayerInteractEntityEvent event) {
        if (isPet(event.getRightClicked())) {
            event.setCancelled(true); // no feeding, naming, dyeing, saddling or riding
        }
    }

    @EventHandler
    void onLeash(PlayerLeashEntityEvent event) {
        if (isPet(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    void onShear(PlayerShearEntityEvent event) {
        if (isPet(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    void onTarget(EntityTargetEvent event) {
        if (event.getTarget() != null && isPet(event.getTarget())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    void onBlockChange(EntityChangeBlockEvent event) {
        if (isPet(event.getEntity())) {
            event.setCancelled(true); // a sheep eating grass
        }
    }
}
