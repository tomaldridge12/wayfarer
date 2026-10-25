package dev.wayfarer.objectives;

import dev.wayfarer.core.PlayerStore;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * A worn hat: an item display riding on the player's head, seen by everyone and leaving the real helmet slot free.
 * It is kept small and a little above the head so the wearer's camera, which sits inside the head's width, never has
 * it in view in first person.
 * A rider is shaken off by teleports, so it is taken down before one and put back after, and a slow check puts back
 * any that went missing. Main thread.
 */
final class HatDisplay implements Listener {
    private static final long CHECK_TICKS = 40;
    /**
     * How big hats are and how far their base floats above the rider's anchor (the top of the head). A hat model fills a
     * 16-unit cube, so {@code scale} is its width in blocks; the camera is 0.18 below the anchor, so a gap of about 0.1
     * keeps the hat's underside out of the wearer's view.
     */
    record Settings(float scale, float raise) {
        static final Settings DEFAULT = new Settings(0.45f, 0.1f);

        /** The {@code hats} section of objectives.yml: scale 0.2 to 1, raise 0 to 0.6; bad values are reported and ignored. */
        static Settings parse(Object raw, java.util.List<String> problems) {
            if (!(raw instanceof java.util.Map<?, ?> m)) {
                return DEFAULT;
            }
            return new Settings(number(m.get("scale"), 0.2, 1.0, DEFAULT.scale(), "hats.scale", problems),
                    number(m.get("raise"), 0, 0.6, DEFAULT.raise(), "hats.raise", problems));
        }

        private static float number(Object o, double min, double max, float fallback, String name, java.util.List<String> problems) {
            if (o == null) {
                return fallback;
            }
            if (o instanceof Number n && n.doubleValue() >= min && n.doubleValue() <= max) {
                return n.floatValue();
            }
            problems.add(name + " must be a number from " + min + " to " + max + "; using " + fallback);
            return fallback;
        }
    }

    private final JavaPlugin plugin;
    private final PlayerStore<ObjectivesPlayerData> store;
    private final Supplier<Map<String, Cosmetic>> cosmetics;
    private final Supplier<Settings> settings;
    private final Map<UUID, ItemDisplay> worn = new HashMap<>();
    private BukkitTask check;

    HatDisplay(JavaPlugin plugin, PlayerStore<ObjectivesPlayerData> store, Supplier<Map<String, Cosmetic>> cosmetics,
            Supplier<Settings> settings) {
        this.settings = settings;
        this.plugin = plugin;
        this.store = store;
        this.cosmetics = cosmetics;
    }

    void start() {
        plugin.getServer().getOnlinePlayers().forEach(this::refresh);
        check = plugin.getServer().getScheduler().runTaskTimer(plugin, () ->
                plugin.getServer().getOnlinePlayers().forEach(p -> {
                    ItemDisplay shown = worn.get(p.getUniqueId());
                    boolean missing = shown != null && (!shown.isValid() || !p.getPassengers().contains(shown));
                    if (wanted(p).isPresent() != (shown != null) || missing) {
                        refresh(p);
                    }
                }), CHECK_TICKS, CHECK_TICKS);
    }

    void stop() {
        if (check != null) {
            check.cancel();
        }
        new ArrayList<>(worn.keySet()).forEach(this::remove);
    }

    /** Puts every online player's hat on again, after the settings changed. */
    void refreshAll() {
        plugin.getServer().getOnlinePlayers().forEach(this::refresh);
    }

    /** The hat the player has chosen and still has config for. */
    private Optional<Cosmetic.Hat> wanted(Player player) {
        ObjectivesPlayerData data = store.cached(player.getUniqueId());
        Cosmetic c = data == null || data.hat == null ? null : cosmetics.get().get(data.hat);
        return c == null || c.hat() == null ? Optional.empty() : Optional.of(c.hat());
    }

    /** Puts the player's chosen hat on (or takes the old one off); a dead or absent player wears none. */
    void refresh(Player player) {
        remove(player.getUniqueId());
        Optional<Cosmetic.Hat> hat = wanted(player);
        if (hat.isEmpty() || !player.isOnline() || player.isDead()) {
            return;
        }
        ItemDisplay display = player.getWorld().spawn(player.getLocation(), ItemDisplay.class, d -> {
            d.setItemStack(stack(hat.get()));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.HEAD);
            d.setBillboard(Display.Billboard.FIXED);
            Settings look = settings.get();
            d.setTransformation(new Transformation(new Vector3f(0, look.raise() + look.scale() / 2, 0), new AxisAngle4f(),
                    new Vector3f(look.scale(), look.scale(), look.scale()), new AxisAngle4f()));
            d.setPersistent(false); // never saved with the chunk, so a crash leaves no stray hats
            d.setInterpolationDuration(0);
        });
        if (player.addPassenger(display)) {
            worn.put(player.getUniqueId(), display);
        } else {
            display.remove();
        }
    }

    void remove(UUID id) {
        ItemDisplay display = worn.remove(id);
        if (display != null) {
            display.remove();
        }
    }

    static ItemStack stack(Cosmetic.Hat hat) {
        if (hat.model()) {
            ItemStack stack = ItemStack.of(Material.PAPER);
            stack.setData(DataComponentTypes.ITEM_MODEL, Key.key(hat.item()));
            return stack;
        }
        Material material = Material.matchMaterial(hat.item());
        return ItemStack.of(material == null ? Material.PAPER : material);
    }

    private void later(Player player) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                refresh(player);
            }
        }, 2);
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

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    void onTeleport(PlayerTeleportEvent event) {
        remove(event.getPlayer().getUniqueId());
        later(event.getPlayer());
    }

    @EventHandler
    void onWorldChange(PlayerChangedWorldEvent event) {
        later(event.getPlayer());
    }
}
