package dev.wayfarer.banners;

import dev.wayfarer.core.ChunkDataService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/** Keeps the placed banner registry in step with loaded chunks and turns broken placed banners back into items. */
final class PlacedBanners {
    private static final String MODULE = "banners";

    private final PlacedBannerRegistry registry = new PlacedBannerRegistry();
    private final ChunkDataService chunkData;
    private final BannerItems items;
    private final Logger log;

    PlacedBanners(ChunkDataService chunkData, BannerItems items, Logger log) {
        this.chunkData = chunkData;
        this.items = items;
        this.log = log;
    }

    PlacedBannerRegistry registry() {
        return registry;
    }

    void loadAll(Server server) {
        for (World world : server.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                loadChunk(chunk);
            }
        }
    }

    void loadChunk(Chunk chunk) {
        Optional<String> text = chunkData.read(chunk, MODULE);
        if (text.isEmpty()) {
            return;
        }
        PlacedBannerRegistry.ChunkRef ref = ref(chunk);
        PlacedBannerRegistry.Decoded decoded = PlacedBannerRegistry.decode(text.get());
        decoded.problems().forEach(p -> log.warning("[banners] Chunk " + ref.x() + "," + ref.z() + ": " + p));
        registry.load(ref, decoded.banners());
        // Safety net: a record whose block is no longer a banner (removed by a path we did not see) is dropped.
        boolean changed = false;
        for (PlacedBannerRegistry.BlockPos pos : List.copyOf(decoded.banners().keySet())) {
            Material type = chunk.getWorld().getBlockAt(pos.x(), pos.y(), pos.z()).getType();
            if (!Tag.BANNERS.isTagged(type)) {
                registry.remove(ref.world(), pos);
                log.warning("[banners] Placed banner " + decoded.banners().get(pos) + " at " + pos
                        + " is no longer a banner block (" + type + "); record removed");
                changed = true;
            }
        }
        if (changed) {
            save(chunk);
        }
    }

    void unloadChunk(Chunk chunk) {
        registry.unload(ref(chunk));
    }

    void clear() {
        registry.clear();
    }

    void place(Block block, String bannerId) {
        registry.add(block.getWorld().getUID(), pos(block), bannerId);
        save(block.getChunk());
    }

    /**
     * If this block is a placed banner, unregisters it and returns the tagged item it should drop.
     * Call while the block is still there, so an inert banner can be rebuilt from the block's own drop.
     */
    Optional<ItemStack> takeDrop(Block block, BannerSet set) {
        Optional<String> id = registry.at(block.getWorld().getUID(), pos(block));
        if (id.isEmpty()) {
            return Optional.empty();
        }
        ItemStack item = set.find(id.get()).map(items::create).orElseGet(() -> inertItem(block, id.get()));
        registry.remove(block.getWorld().getUID(), pos(block));
        save(block.getChunk());
        return Optional.ofNullable(item);
    }

    /** Breaks a placed banner ourselves (for explosions, pistons, water) and drops its tagged item. */
    boolean breakAndDrop(Block block, BannerSet set) {
        if (registry.at(block.getWorld().getUID(), pos(block)).isEmpty()) {
            return false;
        }
        Optional<ItemStack> item = takeDrop(block, set);
        Location at = block.getLocation().add(0.5, 0.5, 0.5);
        block.setType(Material.AIR);
        item.ifPresent(i -> block.getWorld().dropItemNaturally(at, i));
        return true;
    }

    /** Placed banner ids in loaded chunks that the config no longer defines. */
    Set<String> inertIds(BannerSet set) {
        Set<String> inert = new TreeSet<>();
        registry.all().filter(p -> set.find(p.bannerId()).isEmpty()).forEach(p -> inert.add(p.bannerId()));
        return inert;
    }

    private ItemStack inertItem(Block block, String id) {
        // Config no longer knows this id: keep the vanilla banner drop (colour and patterns) and re-tag it.
        ItemStack drop = block.getDrops().stream().filter(i -> Tag.ITEMS_BANNERS.isTagged(i.getType()))
                .findFirst().orElse(null);
        if (drop == null) {
            log.warning("[banners] Inert placed banner " + id + " at " + pos(block) + " had no drop to re-tag");
            return null;
        }
        items.tag(drop, id);
        return drop;
    }

    private void save(Chunk chunk) {
        chunkData.write(chunk, MODULE, registry.encode(ref(chunk)));
    }

    private static PlacedBannerRegistry.ChunkRef ref(Chunk chunk) {
        return new PlacedBannerRegistry.ChunkRef(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
    }

    private static PlacedBannerRegistry.BlockPos pos(Block block) {
        return new PlacedBannerRegistry.BlockPos(block.getX(), block.getY(), block.getZ());
    }
}
