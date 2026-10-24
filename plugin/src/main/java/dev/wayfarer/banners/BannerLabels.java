package dev.wayfarer.banners;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

/**
 * Name-tag-style text displays above placed banners. They are not persistent, so the server never saves them
 * with the chunk and a crash or unload cannot leave one behind; they are spawned again when the chunk loads.
 */
final class BannerLabels {
    private record Key(UUID world, PlacedBannerRegistry.BlockPos pos) {
    }

    private final Map<Key, TextDisplay> shown = new HashMap<>();
    private final java.util.function.Function<String, Component> textFor;

    /** @param textFor the label for a placed-trophy record */
    BannerLabels(java.util.function.Function<String, Component> textFor) {
        this.textFor = textFor;
    }

    void show(Block block, String record) {
        Key key = key(block);
        hide(key);
        World world = block.getWorld();
        // Wall variants are Directional; standing banners and floor heads are Rotatable. A standing banner is two
        // blocks tall, a wall banner hangs within its block, and a head is half a block.
        boolean wall = block.getBlockData() instanceof Directional;
        double height = PlacedTrophy.parse(record).isHead() ? (wall ? 1.0 : 0.85) : (wall ? 1.25 : 2.4);
        Location at = block.getLocation().add(0.5, height, 0.5);
        Component text = textFor.apply(record);
        TextDisplay label = world.spawn(at, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.text(text);
            d.setBillboard(Display.Billboard.CENTER);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setDefaultBackground(true); // the dark translucent name-tag background
            d.setShadowed(false);
            d.setSeeThrough(false);
            d.setViewRange(0.5f); // about 32 blocks
        });
        shown.put(key, label);
    }

    void hide(UUID world, PlacedBannerRegistry.BlockPos pos) {
        hide(new Key(world, pos));
    }

    void hideChunk(PlacedBannerRegistry.ChunkRef chunk) {
        shown.entrySet().removeIf(e -> {
            if (e.getKey().pos().chunk(e.getKey().world()).equals(chunk)) {
                e.getValue().remove();
                return true;
            }
            return false;
        });
    }

    void hideAll() {
        shown.values().forEach(TextDisplay::remove);
        shown.clear();
    }

    /** Re-renders every label, for /wayfarer reload. */
    int refresh(PlacedBannerRegistry registry) {
        return refreshWhere(registry, record -> true);
    }

    /** Re-renders the labels whose placed-trophy record matches. */
    int refreshWhere(PlacedBannerRegistry registry, java.util.function.Predicate<String> which) {
        int count = 0;
        for (Map.Entry<Key, TextDisplay> e : shown.entrySet()) {
            String record = registry.at(e.getKey().world(), e.getKey().pos()).orElse(null);
            if (record != null && which.test(record) && e.getValue().isValid()) {
                e.getValue().text(textFor.apply(record));
                count++;
            }
        }
        return count;
    }

    private void hide(Key key) {
        TextDisplay old = shown.remove(key);
        if (old != null) {
            old.remove();
        }
    }


    private static Key key(Block block) {
        return new Key(block.getWorld().getUID(),
                new PlacedBannerRegistry.BlockPos(block.getX(), block.getY(), block.getZ()));
    }
}
