package dev.wayfarer.banners;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
    private final Supplier<BannerSet> banners;

    BannerLabels(Supplier<BannerSet> banners) {
        this.banners = banners;
    }

    void show(Block block, String bannerId) {
        Key key = key(block);
        hide(key);
        World world = block.getWorld();
        // A standing banner is two blocks tall; a wall banner hangs within its own block.
        boolean wall = block.getBlockData() instanceof Directional; // standing banners are Rotatable
        Location at = block.getLocation().add(0.5, wall ? 1.25 : 2.4, 0.5);
        Component text = text(bannerId);
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

    /** Re-renders every label, for /mobbanners reload. */
    int refresh(PlacedBannerRegistry registry) {
        int count = 0;
        for (Map.Entry<Key, TextDisplay> e : shown.entrySet()) {
            String id = registry.at(e.getKey().world(), e.getKey().pos()).orElse(null);
            if (id != null && e.getValue().isValid()) {
                e.getValue().text(text(id));
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

    private Component text(String bannerId) {
        List<String> lines = LabelText.lines(bannerId, banners.get().find(bannerId));
        return Component.text(lines.get(0), NamedTextColor.WHITE)
                .append(Component.newline())
                .append(Component.text(lines.get(1), NamedTextColor.GRAY));
    }

    private static Key key(Block block) {
        return new Key(block.getWorld().getUID(),
                new PlacedBannerRegistry.BlockPos(block.getX(), block.getY(), block.getZ()));
    }
}
