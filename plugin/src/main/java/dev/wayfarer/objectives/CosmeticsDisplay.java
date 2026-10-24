package dev.wayfarer.objectives;

import dev.wayfarer.core.ItemLook;
import dev.wayfarer.core.PlayerStore;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Wearing cosmetics: a chosen title before the player's name in chat (read from a cache, as chat renders off the main
 * thread) and the player list, and a chosen trail drifting behind them while they move. Main thread except the renderer.
 */
final class CosmeticsDisplay implements Listener {
    private final JavaPlugin plugin;
    private final PlayerStore<ObjectivesPlayerData> store;
    private final Supplier<Map<String, Cosmetic>> cosmetics;
    private final Map<UUID, Component> titles = new ConcurrentHashMap<>();
    private final Map<UUID, Location> last = new ConcurrentHashMap<>();
    private BukkitTask trails;

    CosmeticsDisplay(JavaPlugin plugin, PlayerStore<ObjectivesPlayerData> store, Supplier<Map<String, Cosmetic>> cosmetics) {
        this.plugin = plugin;
        this.store = store;
        this.cosmetics = cosmetics;
    }

    void start() {
        plugin.getServer().getOnlinePlayers().forEach(this::refresh);
        trails = plugin.getServer().getScheduler().runTaskTimer(plugin, this::trails, 4, 4);
    }

    void stop() {
        if (trails != null) {
            trails.cancel();
        }
        plugin.getServer().getOnlinePlayers().forEach(p -> p.playerListName(null));
        titles.clear();
    }

    /** "[Mossborn]" in the title's colour. */
    Component title(String id) {
        Cosmetic c = cosmetics.get().get(id);
        return c == null ? Component.empty() : ItemLook.text("[" + c.name() + "]", c.colour());
    }

    /** Re-reads what the player wears: the chat title cache and their player-list name. */
    void refresh(Player player) {
        ObjectivesPlayerData data = store.cached(player.getUniqueId());
        String title = data == null ? null : data.title;
        if (title == null || !cosmetics.get().containsKey(title)) {
            titles.remove(player.getUniqueId());
            player.playerListName(null);
            return;
        }
        Component prefix = title(title);
        titles.put(player.getUniqueId(), prefix);
        player.playerListName(prefix.append(Component.space()).append(Component.text(player.getName())));
    }

    @EventHandler
    void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                refresh(player);
            }
        }, 20);
    }

    @EventHandler
    void onQuit(PlayerQuitEvent event) {
        titles.remove(event.getPlayer().getUniqueId());
        last.remove(event.getPlayer().getUniqueId());
    }

    /** Wraps whatever renders chat so a title goes in front of the sender's name. */
    @EventHandler(priority = EventPriority.HIGH)
    void onChat(AsyncChatEvent event) {
        Component prefix = titles.get(event.getPlayer().getUniqueId());
        if (prefix == null) {
            return;
        }
        ChatRenderer inner = event.renderer();
        event.renderer((source, name, message, viewer) ->
                inner.render(source, prefix.append(Component.space()).append(name), message, viewer));
    }

    private void trails() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            ObjectivesPlayerData data = store.cached(player.getUniqueId());
            Cosmetic trail = data == null || data.trail == null ? null : cosmetics.get().get(data.trail);
            Location at = player.getLocation();
            Location before = last.put(player.getUniqueId(), at);
            if (trail == null || before == null || before.getWorld() != at.getWorld() || before.distanceSquared(at) < 0.01
                    || player.isInvisible() || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                continue;
            }
            try {
                Particle particle = Particle.valueOf(trail.particle().toUpperCase(Locale.ROOT));
                if (particle.getDataType() == Void.class) {
                    at.getWorld().spawnParticle(particle, at.clone().add(0, 0.15, 0), 2, 0.15, 0.05, 0.15, 0.01);
                }
            } catch (IllegalArgumentException ignored) {
                // a particle this server no longer has; the config check reports it on reload
            }
        }
    }
}
