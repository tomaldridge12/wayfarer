package dev.wayfarer.alloys;

import dev.wayfarer.core.ChunkDataService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Server;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Crucibles in the world: loading them by right-click, the once-a-second melt clock, finished ingots, the label and
 * molten surface, and their chunk data. A crucible is any empty cauldron with heat below it; it exists in data only
 * while it holds something.
 */
final class Crucibles implements Listener {
    static final long PERIOD_TICKS = 20;
    private static final String DATA = "alloys";

    private record Pos(UUID world, int x, int y, int z) {
    }

    private record Shown(TextDisplay label, BlockDisplay molten) {
        void remove() {
            label.remove();
            if (molten != null) {
                molten.remove();
            }
        }
    }

    private final Server server;
    private final ChunkDataService chunkData;
    private final Supplier<AlloyRules> rules;
    private final AlloyItems items;
    private final BiConsumer<UUID, String> forged;
    private final Logger log;
    private final Map<Pos, CrucibleState> loaded = new HashMap<>();
    private final Map<Pos, Shown> shown = new HashMap<>();

    /** @param forged records one {@code forge:<alloy>} activity for a player */
    Crucibles(Server server, ChunkDataService chunkData, Supplier<AlloyRules> rules, AlloyItems items,
            BiConsumer<UUID, String> forged, Logger log) {
        this.server = server;
        this.chunkData = chunkData;
        this.rules = rules;
        this.items = items;
        this.forged = forged;
        this.log = log;
    }

    int loadAll() {
        for (World world : server.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                load(chunk);
            }
        }
        return loaded.size();
    }

    void clear() {
        shown.values().forEach(Shown::remove);
        shown.clear();
        loaded.clear();
    }

    @EventHandler
    void onChunkLoad(ChunkLoadEvent event) {
        load(event.getChunk());
    }

    @EventHandler
    void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        UUID world = chunk.getWorld().getUID();
        loaded.keySet().removeIf(p -> p.world().equals(world) && p.x() >> 4 == chunk.getX() && p.z() >> 4 == chunk.getZ());
        shown.entrySet().removeIf(e -> {
            Pos p = e.getKey();
            if (p.world().equals(world) && p.x() >> 4 == chunk.getX() && p.z() >> 4 == chunk.getZ()) {
                e.getValue().remove();
                return true;
            }
            return false;
        });
    }

    private void load(Chunk chunk) {
        Optional<String> text = chunkData.read(chunk, DATA);
        if (text.isEmpty()) {
            return;
        }
        CrucibleState.Decoded d = CrucibleState.decode(text.get());
        d.problems().forEach(p -> log.warning("[alloys] Chunk " + chunk.getX() + "," + chunk.getZ() + ": " + p));
        UUID world = chunk.getWorld().getUID();
        d.crucibles().forEach(c -> loaded.put(new Pos(world, c.x(), c.y(), c.z()), c));
        log.info("[alloys] " + d.crucibles().size() + " crucible(s) loaded in chunk " + chunk.getX() + "," + chunk.getZ());
        // Displays are drawn by the next clock tick: spawning entities during a chunk load is unsafe.
    }

    // --- Loading by hand ------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
                || event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.CAULDRON) {
            return;
        }
        Block block = event.getClickedBlock();
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        Pos pos = pos(block);
        AlloyRules r = rules.get();
        if (hand.isEmpty()) {
            CrucibleState c = loaded.get(pos);
            if (player.isSneaking() && c != null) {
                event.setCancelled(true);
                giveBack(player, c);
                remove(block, pos);
                player.playSound(player.getLocation(), Sound.ITEM_BUNDLE_REMOVE_ONE, 1, 1);
            }
            return;
        }
        String key = hand.getType().getKey().getKey();
        if (!r.isIngredient(key) || items.tag(hand).isPresent() || heat(block).isEmpty()) {
            return; // buckets, bottles and everything else keep their vanilla behaviour
        }
        event.setCancelled(true);
        long now = block.getWorld().getGameTime();
        CrucibleState c = loaded.getOrDefault(pos, CrucibleState.empty(block.getX(), block.getY(), block.getZ(), now));
        CrucibleState.Added added = c.add(key, player.isSneaking() ? hand.getAmount() : 1, r.crucible().maxContents(),
                player.getUniqueId(), now);
        if (added.taken() == 0) {
            player.sendActionBar(Component.text("The crucible is full", NamedTextColor.RED));
            return;
        }
        if (player.getGameMode() != GameMode.CREATIVE) {
            hand.setAmount(hand.getAmount() - added.taken());
        }
        loaded.put(pos, added.state());
        save(block.getChunk());
        draw(block, added.state());
        block.getWorld().playSound(block.getLocation().add(0.5, 1, 0.5), Sound.BLOCK_LAVA_POP, 0.6f, 1.4f);
    }

    /** Testing tool: loads a crucible as a right-click would, for a starter who may be offline. */
    String load(Block block, String item, int count, UUID starter) {
        if (block.getType() != Material.CAULDRON) {
            return "Not a cauldron at " + block.getX() + "," + block.getY() + "," + block.getZ();
        }
        if (!rules.get().isIngredient(item)) {
            return "'" + item + "' is not a crucible ingredient";
        }
        Pos pos = pos(block);
        long now = block.getWorld().getGameTime();
        CrucibleState c = loaded.getOrDefault(pos, CrucibleState.empty(block.getX(), block.getY(), block.getZ(), now));
        CrucibleState.Added added = c.add(item, count, rules.get().crucible().maxContents(), starter, now);
        loaded.put(pos, added.state());
        save(block.getChunk());
        draw(block, added.state());
        AlloyRules.Melt melt = rules.get().melt(added.state().contents(), heat(block));
        return "Crucible at " + block.getX() + "," + block.getY() + "," + block.getZ() + " took " + added.taken() + " "
                + item + "; now " + added.state().contents() + ", " + melt.status().name().toLowerCase(java.util.Locale.ROOT)
                + heat(block).map(h -> " at " + h.label() + " heat").orElse(" with no heat");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onBreak(BlockBreakEvent event) {
        Pos pos = pos(event.getBlock());
        CrucibleState c = loaded.get(pos);
        if (c != null) {
            dropContents(event.getBlock(), c);
            remove(event.getBlock(), pos);
        }
    }

    // --- The clock ------------------------------------------------------------------------------------------------

    /** Once a second: advance every loaded crucible, finish melts, keep displays current. */
    void tick() {
        AlloyRules r = rules.get();
        for (Map.Entry<Pos, CrucibleState> e : new ArrayList<>(loaded.entrySet())) {
            Pos pos = e.getKey();
            World world = server.getWorld(pos.world());
            if (world == null || !world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) {
                continue;
            }
            Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
            CrucibleState c = e.getValue();
            if (block.getType() != Material.CAULDRON) {
                // Broken by something we did not see (explosion, piston) or filled by rain: nothing is lost.
                dropContents(block, c);
                remove(block, pos);
                continue;
            }
            AlloyRules.Melt melt = r.melt(c.contents(), heat(block));
            CrucibleState.Step step = c.step(melt, world.getGameTime());
            if (step.finished().isPresent()) {
                finish(block, c, step.finished().get());
                remove(block, pos);
                continue;
            }
            loaded.put(pos, step.state());
            save(block.getChunk());
            draw(block, step.state());
            if (melt.status() == AlloyRules.Status.MELTS) {
                Location top = block.getLocation().add(0.5, 0.95, 0.5);
                world.spawnParticle(Particle.LAVA, top, 1, 0.2, 0, 0.2, 0);
                world.spawnParticle(Particle.SMOKE, top, 2, 0.2, 0.05, 0.2, 0.01);
                if (step.state().progress() % 60 < PERIOD_TICKS) {
                    world.playSound(top, Sound.BLOCK_LAVA_AMBIENT, 0.5f, 1.2f);
                }
            }
        }
    }

    private void finish(Block block, CrucibleState c, AlloyRules.Melt melt) {
        AlloyDefinition alloy = melt.alloy();
        Location top = block.getLocation().add(0.5, 1.1, 0.5);
        ItemStack ingots = items.ingot(alloy, melt.ingots());
        block.getWorld().dropItem(top, ingots, i -> i.setVelocity(new org.bukkit.util.Vector(0, 0.25, 0)));
        block.getWorld().playSound(top, Sound.BLOCK_LAVA_EXTINGUISH, 0.6f, 1.5f);
        block.getWorld().spawnParticle(Particle.FLAME, top, 12, 0.2, 0.1, 0.2, 0.02);
        if (c.starter() != null) {
            for (int i = 0; i < melt.ingots(); i++) {
                forged.accept(c.starter(), "forge:" + alloy.id());
            }
        }
        log.info("[alloys] Crucible at " + c.x() + "," + c.y() + "," + c.z() + " melted " + melt.ingots() + " "
                + alloy.id() + " for " + c.starter());
    }

    // --- Heat, contents, data, displays -------------------------------------------------------------------------

    Optional<Heat> heat(Block cauldron) {
        Block below = cauldron.getRelative(0, -1, 0);
        boolean lit = below.getBlockData() instanceof Lightable l && l.isLit();
        return rules.get().heatOf(below.getType().getKey().getKey(), lit);
    }

    private void giveBack(Player player, CrucibleState c) {
        for (ItemStack stack : stacks(c)) {
            player.getInventory().addItem(stack).values().forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
        }
    }

    private void dropContents(Block block, CrucibleState c) {
        Location at = block.getLocation().add(0.5, 0.5, 0.5);
        stacks(c).forEach(s -> block.getWorld().dropItemNaturally(at, s));
    }

    private static List<ItemStack> stacks(CrucibleState c) {
        List<ItemStack> out = new ArrayList<>();
        c.contents().forEach((key, count) -> {
            Material m = Material.matchMaterial(key);
            if (m == null) {
                return;
            }
            for (int left = count; left > 0; left -= m.getMaxStackSize()) {
                out.add(ItemStack.of(m, Math.min(left, m.getMaxStackSize())));
            }
        });
        return out;
    }

    private void remove(Block block, Pos pos) {
        loaded.remove(pos);
        Shown s = shown.remove(pos);
        if (s != null) {
            s.remove();
        }
        save(block.getChunk());
    }

    private void save(Chunk chunk) {
        UUID world = chunk.getWorld().getUID();
        List<CrucibleState> here = new ArrayList<>();
        loaded.forEach((p, c) -> {
            if (p.world().equals(world) && p.x() >> 4 == chunk.getX() && p.z() >> 4 == chunk.getZ()) {
                here.add(c);
            }
        });
        chunkData.write(chunk, DATA, here.isEmpty() ? null : CrucibleState.encode(here));
    }

    /** The label (contents and what it is doing) and, while melting, a glowing surface rising with the contents. */
    private void draw(Block block, CrucibleState c) {
        Pos pos = pos(block);
        AlloyRules r = rules.get();
        AlloyRules.Melt melt = r.melt(c.contents(), heat(block));
        Shown old = shown.get(pos);
        if (old == null || !old.label().isValid()) {
            if (old != null) {
                old.remove();
            }
            TextDisplay label = block.getWorld().spawn(block.getLocation().add(0.5, 1.45, 0.5), TextDisplay.class, d -> {
                d.setPersistent(false);
                d.setBillboard(Display.Billboard.CENTER);
                d.setAlignment(TextDisplay.TextAlignment.CENTER);
                d.setDefaultBackground(true);
                d.setViewRange(0.3f);
            });
            old = new Shown(label, null);
        }
        old.label().text(label(c, melt));
        BlockDisplay molten = old.molten();
        if (melt.status() == AlloyRules.Status.MELTS) {
            Material surface = Optional.ofNullable(Material.matchMaterial(melt.alloy().moltenBlock())).orElse(Material.ORANGE_CONCRETE);
            float fill = Math.min(1f, c.total() / (float) r.crucible().maxContents());
            Transformation t = new Transformation(new Vector3f(0.125f, 0.3f + 0.6f * fill, 0.125f), new AxisAngle4f(),
                    new Vector3f(0.75f, 0.04f, 0.75f), new AxisAngle4f());
            if (molten == null || !molten.isValid()) {
                molten = block.getWorld().spawn(block.getLocation(), BlockDisplay.class, d -> {
                    d.setPersistent(false);
                    d.setBrightness(new Display.Brightness(15, 15));
                });
            }
            molten.setBlock(surface.createBlockData());
            molten.setTransformation(t);
        } else if (molten != null) {
            molten.remove();
            molten = null;
        }
        shown.put(pos, new Shown(old.label(), molten));
    }

    private static Component label(CrucibleState c, AlloyRules.Melt melt) {
        Component contents = Component.empty();
        boolean first = true;
        for (Map.Entry<String, Integer> e : new LinkedHashMap<>(c.contents()).entrySet()) {
            Material m = Material.matchMaterial(e.getKey());
            Component name = m == null ? Component.text(e.getKey()) : Component.translatable(m.translationKey());
            contents = contents.append(Component.text((first ? "" : " · ") + e.getValue() + " ")).append(name);
            first = false;
        }
        Component status = switch (melt.status()) {
            case MELTS -> Component.text("Melting " + melt.alloy().name() + " · "
                    + Math.max(1, (melt.ticks() - c.progress() + 19) / 20) + "s", TextColor.color(melt.alloy().colour()));
            case TOO_COLD -> Component.text(melt.alloy().name() + " needs " + melt.alloy().heat().label() + " heat", NamedTextColor.GOLD);
            case NO_HEAT -> Component.text("No heat", NamedTextColor.GRAY);
            case NO_RECIPE -> Component.text("No recipe", NamedTextColor.GRAY);
            case EMPTY -> Component.empty();
        };
        return contents.appendNewline().append(status);
    }

    private static Pos pos(Block block) {
        return new Pos(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    int count() {
        return loaded.size();
    }
}
