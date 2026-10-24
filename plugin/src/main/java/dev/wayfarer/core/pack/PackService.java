package dev.wayfarer.core.pack;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.wayfarer.core.Core;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Puts Wayfarer's resource pack on every client: offered on join as a required pack once a startup check has found
 * its release, a kick for declining, a warning for a failed download, and the /wayfarer pack commands.
 */
public final class PackService implements Listener {
    public static final String CONFIG_ID = "pack";
    static final String OWNER = "core";
    static final String ADMIN_PERMISSION = "wayfarer.pack.admin";

    /** Whether the startup check found the pack: not yet known, there, or missing. */
    private enum Reach {
        CHECKING, REACHABLE, UNREACHABLE
    }

    private final JavaPlugin plugin;
    private final Core core;
    private final Map<UUID, String> statuses = new ConcurrentHashMap<>();
    private Optional<PackOffer> offer = Optional.empty();
    private volatile Reach reach = Reach.CHECKING;
    private volatile String reachDetail = "";

    public PackService(JavaPlugin plugin, Core core) {
        this.plugin = plugin;
        this.core = core;
    }

    public void enable() {
        List<String> problems = new ArrayList<>();
        Map<String, Object> config;
        try {
            config = core.config().load(CONFIG_ID);
        } catch (Exception e) {
            core.logger().log(Level.SEVERE, "pack.yml could not be read; using the built pack", e);
            config = Map.of();
        }
        offer = PackOffer.from(config, jarProperties(), problems);
        problems.forEach(p -> core.logger().warning("[pack] " + p));
        offer.ifPresentOrElse(o -> {
            core.logger().info("Resource pack offered: " + o.tag() + " sha1 " + o.sha1() + " from " + o.url());
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> check(o));
        }, () -> core.logger().info("Resource pack: none offered"));
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        core.commands().register(OWNER, command());
    }

    public void disable() {
        HandlerList.unregisterAll(this);
        statuses.clear();
    }

    private Properties jarProperties() {
        Properties props = new Properties();
        try (InputStream in = plugin.getResource("wayfarer-pack.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (Exception e) {
            core.logger().log(Level.WARNING, "Could not read wayfarer-pack.properties", e);
        }
        return props;
    }

    /** Async: one HEAD request (following GitHub's redirect to the file), then back on the main thread. */
    private void check(PackOffer o) {
        Reach result;
        String detail;
        try (HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10)).build()) {
            HttpResponse<Void> response = http.send(HttpRequest.newBuilder(URI.create(o.url()))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody()).timeout(Duration.ofSeconds(15)).build(),
                    HttpResponse.BodyHandlers.discarding());
            result = response.statusCode() == 200 ? Reach.REACHABLE : Reach.UNREACHABLE;
            detail = "HTTP " + response.statusCode();
        } catch (Exception e) {
            result = Reach.UNREACHABLE;
            detail = e.getClass().getSimpleName();
        }
        Reach found = result;
        String why = detail;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            reach = found;
            reachDetail = why;
            if (found == Reach.REACHABLE) {
                core.logger().info("Resource pack " + o.tag() + " is reachable; offering it to players");
                plugin.getServer().getOnlinePlayers().forEach(this::send);
            } else {
                core.logger().warning("Resource pack " + o.tag() + " not reachable at " + o.url() + " (" + why
                        + "); not offering it this run. Publish it with scripts/pack-release.sh");
            }
        });
    }

    private boolean send(Player player) {
        if (offer.isEmpty() || reach != Reach.REACHABLE) {
            return false;
        }
        PackOffer o = offer.get();
        player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                .packs(ResourcePackInfo.resourcePackInfo(o.id(), URI.create(o.url()), o.sha1()))
                .required(true)
                .replace(true)
                .prompt(Component.text(o.prompt())));
        return true;
    }

    @EventHandler
    void onJoin(PlayerJoinEvent event) {
        send(event.getPlayer());
    }

    @EventHandler
    void onQuit(PlayerQuitEvent event) {
        statuses.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    void onStatus(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        String status = event.getStatus().name();
        statuses.put(player.getUniqueId(), status);
        core.logger().info("[pack] " + player.getName() + ": " + status);
        switch (PackOffer.onStatus(status)) {
            case KICK -> plugin.getServer().getScheduler().runTask(plugin, () -> player.kick(Component.text(
                    "Wayfarer needs its resource pack. To accept it next time: Multiplayer, select the server, Edit, "
                            + "set Server Resource Packs to Enabled, then join again.", NamedTextColor.YELLOW)));
            case WARN -> player.sendMessage(Component.text("Wayfarer's resource pack failed to load (" + status
                    + "); custom items will look plain. Rejoin to try again.", NamedTextColor.YELLOW));
            case NONE -> {
            }
        }
    }

    // --- commands ---

    private LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("pack")
                .requires(s -> s.getSender().hasPermission(ADMIN_PERMISSION))
                .executes(ctx -> run(() -> status(ctx.getSource().getSender())))
                .then(Commands.literal("send").then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, b) -> {
                            plugin.getServer().getOnlinePlayers().forEach(p -> b.suggest(p.getName()));
                            return b.buildFuture();
                        })
                        .executes(ctx -> run(() -> sendTo(ctx.getSource().getSender(),
                                StringArgumentType.getString(ctx, "player"))))))
                .then(Commands.literal("model").then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> run(() -> model(ctx.getSource().getSender(),
                                StringArgumentType.getString(ctx, "name"))))));
    }

    private void status(CommandSender sender) {
        if (offer.isEmpty()) {
            sender.sendMessage("Resource pack: none offered (switched off or not built)");
            return;
        }
        PackOffer o = offer.get();
        long loaded = plugin.getServer().getOnlinePlayers().stream()
                .filter(p -> "SUCCESSFULLY_LOADED".equals(statuses.get(p.getUniqueId()))).count();
        sender.sendMessage("Resource pack " + o.tag() + ": " + reach.name().toLowerCase(java.util.Locale.ROOT)
                + (reachDetail.isEmpty() ? "" : " (" + reachDetail + ")") + ", loaded by " + loaded + " of "
                + plugin.getServer().getOnlinePlayers().size() + " online; " + o.url());
    }

    private void sendTo(CommandSender sender, String name) {
        Player player = plugin.getServer().getPlayerExact(name);
        if (player == null) {
            sender.sendMessage("No online player named '" + name + "'");
        } else {
            sender.sendMessage(send(player) ? "Sent the resource pack to " + player.getName()
                    : "The resource pack is not reachable or switched off; nothing sent");
        }
    }

    /** Gives a paper item drawn with wayfarer:<name>, for checking pack art in game. */
    private void model(CommandSender sender, String name) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Run this in game");
            return;
        }
        ItemStack item = new ItemStack(Material.PAPER);
        item.setData(DataComponentTypes.ITEM_MODEL, Key.key("wayfarer", name));
        item.setData(DataComponentTypes.ITEM_NAME, Component.text("wayfarer:" + name));
        player.getInventory().addItem(item);
        sender.sendMessage("Gave you an item drawn with wayfarer:" + name);
    }

    private static int run(Runnable action) {
        action.run();
        return Command.SINGLE_SUCCESS;
    }
}
