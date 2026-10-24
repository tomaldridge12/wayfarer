package dev.wayfarer.curios;

import dev.wayfarer.core.ItemLook;
import dev.wayfarer.core.Wayfinding;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Tool curios on right-click (with their cooldowns and the vanilla overlay), and the jukebox chorus. Everything a tool
 * puts in the world (light blocks, glowing outlines) is tracked and taken away when it ends or the module stops.
 * Main thread.
 */
final class CurioTools implements Listener {
    private final Plugin plugin;
    private final Supplier<CurioRules> rules;
    private final CurioItems items;
    private final Function<UUID, List<CurioDefinition>> working;
    private final Supplier<Optional<Wayfinding>> wayfinding;
    private final Logger log;
    private final CurioRules.Cooldowns cooldowns = new CurioRules.Cooldowns();
    /** Light blocks placed by a shining player, by player: where, so they can be put back to air. */
    private final Map<UUID, Block> lights = new HashMap<>();
    private final Map<UUID, BukkitTask> shining = new HashMap<>();
    private final List<BlockDisplay> outlines = new ArrayList<>();
    private final Map<UUID, BukkitTask> channels = new HashMap<>();
    private final List<BukkitTask> choruses = new ArrayList<>();

    CurioTools(Plugin plugin, Supplier<CurioRules> rules, CurioItems items, Function<UUID, List<CurioDefinition>> working,
            Supplier<Optional<Wayfinding>> wayfinding, Logger log) {
        this.plugin = plugin;
        this.rules = rules;
        this.items = items;
        this.working = working;
        this.wayfinding = wayfinding;
        this.log = log;
    }

    /** Takes back every light, outline and channel. */
    void stop() {
        shining.values().forEach(BukkitTask::cancel);
        shining.clear();
        lights.values().forEach(CurioTools::unlight);
        lights.clear();
        outlines.forEach(BlockDisplay::remove);
        outlines.clear();
        channels.values().forEach(BukkitTask::cancel);
        channels.clear();
        choruses.forEach(BukkitTask::cancel);
        choruses.clear();
    }

    @EventHandler(priority = EventPriority.HIGH)
    void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Player player = event.getPlayer();
        Block clicked = event.getClickedBlock();
        if (clicked != null && clicked.getType() == Material.JUKEBOX) {
            chorus(player, clicked, event.getItem());
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        CurioDefinition curio = items.idOf(held).flatMap(id -> rules.get().curio(id)).filter(CurioDefinition::tool).orElse(null);
        if (curio == null || (clicked != null && clicked.getType().isInteractable() && !player.isSneaking())) {
            return; // a chest still opens with a curio in hand; sneak to use the curio on it
        }
        event.setCancelled(true);
        long now = System.currentTimeMillis();
        long left = cooldowns.remaining(player.getUniqueId(), curio.id(), now);
        if (left > 0) {
            player.sendActionBar(ItemLook.text(curio.name() + " is resting · " + ItemLook.duration((int) ((left + 999) / 1000)),
                    ItemLook.MUTED));
            return;
        }
        if (use(player, curio)) {
            cooldowns.tryStart(player.getUniqueId(), curio.id(), curio.cooldownSeconds(), now);
            player.setCooldown(CurioItems.group(curio), curio.cooldownSeconds() * 20);
        }
    }

    /** Does what the tool does; false when it could not (nothing nearby to sense), so no cooldown is spent. */
    private boolean use(Player player, CurioDefinition curio) {
        return switch (curio.effect()) {
            case Effect.Spread s -> spread(player, curio, s);
            case Effect.Light l -> light(player, curio, l);
            case Effect.Sense s -> sense(player, curio, s);
            case Effect.Recall r -> recall(player, curio, r);
            case Effect.Locate l -> locate(player, curio);
            default -> false;
        };
    }

    private boolean spread(Player player, CurioDefinition curio, Effect.Spread s) {
        Block centre = player.getLocation().getBlock();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int turned = 0;
        int r = s.radius();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > r * r) {
                        continue;
                    }
                    Block b = centre.getRelative(dx, dy, dz);
                    String to = s.turns().get(b.getType().getKey().getKey());
                    Material into = to == null ? null : Material.matchMaterial(to);
                    if (into != null && exposed(b) && random.nextDouble() < s.chance()) {
                        b.setType(into);
                        b.getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR, b.getLocation().add(0.5, 1.1, 0.5), 2, 0.3, 0.1, 0.3);
                        turned++;
                    }
                }
            }
        }
        if (turned == 0) {
            player.sendActionBar(ItemLook.text("Nothing here wants to grow", ItemLook.MUTED));
            return false;
        }
        player.playSound(player.getLocation(), Sound.BLOCK_MOSS_PLACE, 1f, 0.8f);
        player.playSound(player.getLocation(), Sound.BLOCK_AZALEA_LEAVES_PLACE, 0.8f, 1.2f);
        log.info("[curios] " + curio.name() + " turned " + turned + " blocks around " + pos(centre) + " for " + player.getName());
        return true;
    }

    /** A block with air on at least one side, so the change can be seen. */
    private static boolean exposed(Block b) {
        for (BlockFace face : new BlockFace[] {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            if (b.getRelative(face).getType().isAir()) {
                return true;
            }
        }
        return false;
    }

    private boolean light(Player player, CurioDefinition curio, Effect.Light l) {
        UUID id = player.getUniqueId();
        Optional.ofNullable(shining.remove(id)).ifPresent(BukkitTask::cancel);
        int[] ticks = {l.seconds() * 4};
        shining.put(id, plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            Block old = lights.remove(id);
            if (!player.isOnline() || --ticks[0] <= 0) {
                unlight(old);
                Optional.ofNullable(shining.remove(id)).ifPresent(BukkitTask::cancel);
                return;
            }
            Block at = player.getEyeLocation().getBlock();
            if (old != null && !old.equals(at)) {
                unlight(old);
            }
            if (at.getType().isAir() || at.getType() == Material.LIGHT) {
                at.setType(Material.LIGHT, false);
                lights.put(id, at);
            }
            if (ticks[0] % 4 == 0) {
                player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1.2, 0), 1, 0.4, 0.5, 0.4, 0.01);
            }
        }, 0, 5));
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.6f);
        return true;
    }

    private static void unlight(Block b) {
        if (b != null && b.getType() == Material.LIGHT) {
            b.setType(Material.AIR, false);
        }
    }

    private boolean sense(Player player, CurioDefinition curio, Effect.Sense s) {
        Block centre = player.getLocation().getBlock();
        List<Block> found = new ArrayList<>();
        int r = s.radius();
        for (int dx = -r; dx <= r && found.size() < 64; dx++) {
            for (int dy = -r; dy <= r && found.size() < 64; dy++) {
                for (int dz = -r; dz <= r && found.size() < 64; dz++) {
                    Block b = centre.getRelative(dx, dy, dz);
                    if (s.blocks().contains(b.getType().getKey().getKey())) {
                        found.add(b);
                    }
                }
            }
        }
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, found.isEmpty() ? 0.6f : 1.4f);
        if (found.isEmpty()) {
            player.sendActionBar(ItemLook.text("The " + curio.name() + " stays still. Nothing here.", ItemLook.MUTED));
            return true; // still a use: knowing there is nothing is the point
        }
        List<BlockDisplay> shown = new ArrayList<>();
        for (Block b : found) {
            BlockDisplay d = b.getWorld().spawn(b.getLocation(), BlockDisplay.class, e -> {
                e.setBlock(b.getBlockData());
                e.setGlowing(true);
                e.setGlowColorOverride(Color.fromRGB(curio.colour()));
                e.setVisibleByDefault(false);
                e.setPersistent(false);
            });
            player.showEntity(plugin, d);
            shown.add(d);
        }
        outlines.addAll(shown);
        player.sendActionBar(ItemLook.text("The " + curio.name() + " dips: " + found.size() + " nearby", curio.colour()));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            shown.forEach(BlockDisplay::remove);
            outlines.removeAll(shown);
        }, s.seconds() * 20L);
        return true;
    }

    private boolean recall(Player player, CurioDefinition curio, Effect.Recall r) {
        UUID id = player.getUniqueId();
        if (channels.containsKey(id)) {
            return false;
        }
        Location start = player.getLocation();
        double health = player.getHealth();
        int[] left = {r.channelSeconds() * 4};
        channels.put(id, plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            boolean moved = !player.isOnline() || player.getWorld() != start.getWorld()
                    || player.getLocation().distanceSquared(start) > 0.25;
            if (moved || player.getHealth() < health) {
                Optional.ofNullable(channels.remove(id)).ifPresent(BukkitTask::cancel);
                cooldowns.cancel(id, curio.id());
                player.setCooldown(CurioItems.group(curio), 0);
                player.sendActionBar(ItemLook.text("The " + curio.name() + " cools. Stand still to go home.", ItemLook.MUTED));
                return;
            }
            player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1, 0), 12, 0.4, 0.8, 0.4, 0.2);
            if (left[0] % 4 == 0) {
                player.sendActionBar(ItemLook.text("Going home · " + (left[0] / 4), curio.colour()));
            }
            if (--left[0] <= 0) {
                Optional.ofNullable(channels.remove(id)).ifPresent(BukkitTask::cancel);
                Location home = Optional.ofNullable(player.getRespawnLocation())
                        .orElse(plugin.getServer().getWorlds().getFirst().getSpawnLocation());
                player.teleport(home);
                player.playSound(home, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);
                log.info("[curios] " + curio.name() + " took " + player.getName() + " home to " + pos(home.getBlock()));
            }
        }, 0, 5));
        return true;
    }

    private boolean locate(Player player, CurioDefinition curio) {
        Optional<Wayfinding> service = wayfinding.get();
        if (service.isEmpty() || !service.get().offerFree(player)) {
            player.sendActionBar(ItemLook.text("The needle spins and settles nowhere.", ItemLook.MUTED));
            return false;
        }
        return true;
    }

    /** Hyacinthus' Blessing: a record started by a carrier sets notes rising from the jukebox while it plays. */
    private void chorus(Player player, Block jukebox, ItemStack disc) {
        if (disc == null || !disc.hasData(DataComponentTypes.JUKEBOX_PLAYABLE)
                || !(jukebox.getState() instanceof Jukebox box) || box.hasRecord()
                || CurioRules.effects(working.apply(player.getUniqueId()), Effect.Chorus.class).isEmpty()) {
            return;
        }
        Location above = jukebox.getLocation().add(0.5, 1.2, 0.5);
        int[] left = {960}; // up to four minutes, the longest record
        BukkitTask[] task = new BukkitTask[1];
        task[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            boolean playing = jukebox.getType() == Material.JUKEBOX && jukebox.getState() instanceof Jukebox j && j.isPlaying();
            if (--left[0] <= 0 || (left[0] < 956 && !playing)) {
                task[0].cancel();
                choruses.remove(task[0]);
                return;
            }
            double angle = left[0] * 0.6;
            Location note = above.clone().add(Math.cos(angle) * 0.8, ThreadLocalRandom.current().nextDouble(0.4), Math.sin(angle) * 0.8);
            jukebox.getWorld().spawnParticle(Particle.NOTE, note, 0, ThreadLocalRandom.current().nextDouble(), 0, 0, 1);
        }, 4, 5);
        choruses.add(task[0]);
    }

    private static String pos(Block b) {
        return b.getX() + "," + b.getY() + "," + b.getZ();
    }
}
