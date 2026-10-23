package dev.wayfarer.banners;

import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import io.papermc.paper.event.block.BlockBreakBlockEvent;
import java.util.List;
import java.util.function.Supplier;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

/**
 * Every way a placed banner can appear or go away. Break handlers run at HIGHEST so most other plugins have
 * already had their chance to cancel; anyone may break a placed banner.
 */
final class PlacedBannerListener implements Listener {
    private final PlacedBanners placed;
    private final BannerItems items;
    private final Supplier<BannerSet> banners;

    PlacedBannerListener(PlacedBanners placed, BannerItems items, Supplier<BannerSet> banners) {
        this.placed = placed;
        this.items = items;
        this.banners = banners;
    }

    @EventHandler
    void onChunkLoad(ChunkLoadEvent event) {
        placed.loadChunk(event.getChunk());
    }

    @EventHandler
    void onChunkUnload(ChunkUnloadEvent event) {
        placed.unloadChunk(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onPlace(BlockPlaceEvent event) {
        // Ground and wall banners both arrive here; an id missing from config is still recorded (inert).
        items.idOf(event.getItemInHand()).ifPresent(id -> placed.place(event.getBlockPlaced(), id));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        placed.takeDrop(block, banners.get()).ifPresent(item -> {
            event.setDropItems(false);
            if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
                block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), item);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onEntityExplode(EntityExplodeEvent event) {
        explode(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onBlockExplode(BlockExplodeEvent event) {
        explode(event.blockList());
    }

    /** Takes placed banners out of the blast so their drop is certain, then breaks them ourselves. */
    private void explode(List<Block> blocks) {
        blocks.removeIf(block -> placed.breakAndDrop(block, banners.get()));
    }

    /** A placed banner losing its support, for example the wall it hangs on exploded. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onDestroy(BlockDestroyEvent event) {
        Block block = event.getBlock();
        placed.takeDrop(block, banners.get()).ifPresent(item -> {
            event.setWillDrop(false);
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), item);
        });
    }

    /** Paper's event for blocks broken by other blocks (flowing water or lava, pistons). */
    @EventHandler
    void onBrokenByBlock(BlockBreakBlockEvent event) {
        placed.takeDrop(event.getBlock(), banners.get()).ifPresent(item -> {
            event.getDrops().clear();
            event.getDrops().add(item);
        });
    }

    // Fallbacks in case 26.3 destroys banners in these cases without BlockBreakBlockEvent (unverified).

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onPistonExtend(BlockPistonExtendEvent event) {
        event.getBlocks().forEach(block -> placed.breakAndDrop(block, banners.get()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onPistonRetract(BlockPistonRetractEvent event) {
        event.getBlocks().forEach(block -> placed.breakAndDrop(block, banners.get()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onFlow(BlockFromToEvent event) {
        placed.breakAndDrop(event.getToBlock(), banners.get());
    }
}
