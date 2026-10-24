package dev.wayfarer.curios;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.tag.TagKey;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Facts about one player read from the world as a condition asks for them (nothing is computed unasked). */
final class PaperCurioFacts implements CurioFacts {
    /** Biome tag membership never changes while the server runs. */
    private static final Map<String, Boolean> BIOME_TAGS = new ConcurrentHashMap<>();

    private final Player player;
    private final Predicate<ItemStack> alloyGear;
    private final Location at;

    PaperCurioFacts(Player player, Predicate<ItemStack> alloyGear) {
        this.player = player;
        this.alloyGear = alloyGear;
        this.at = player.getLocation();
    }

    @Override
    public int y() {
        return at.getBlockY();
    }

    @Override
    public boolean skyHidden() {
        return player.getEyeLocation().getBlock().getLightFromSky() == 0;
    }

    @Override
    public boolean underwater() {
        return player.isUnderWater();
    }

    @Override
    public String dimension() {
        return switch (at.getWorld().getEnvironment()) {
            case NETHER -> "nether";
            case THE_END -> "end";
            default -> "overworld";
        };
    }

    @Override
    public boolean night() {
        long time = at.getWorld().getTime();
        return time >= 13000 && time < 23000;
    }

    @Override
    public boolean raining() {
        World world = at.getWorld();
        return world.hasStorm() && player.getEyeLocation().getBlock().getLightFromSky() == 15
                && at.getBlock().getTemperature() < 1.0;
    }

    @Override
    public boolean inBiomeTag(String tag) {
        Biome biome = at.getBlock().getBiome();
        NamespacedKey biomeKey = biome.getKey();
        return BIOME_TAGS.computeIfAbsent(tag + "|" + biomeKey, k -> {
            NamespacedKey tagKey = NamespacedKey.fromString(tag);
            var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME);
            TagKey<Biome> key = tagKey == null ? null : TagKey.create(RegistryKey.BIOME, tagKey);
            return key != null && registry.hasTag(key)
                    && registry.getTag(key).contains(TypedKey.create(RegistryKey.BIOME, biomeKey));
        });
    }

    @Override
    public String heldTool() {
        return toolKind(player.getInventory().getItemInMainHand().getType());
    }

    static String toolKind(Material m) {
        String key = m.getKey().getKey();
        for (String suffix : new String[] {"pickaxe", "axe", "shovel", "hoe", "sword", "spear"}) {
            if (key.endsWith("_" + suffix)) {
                return suffix;
            }
        }
        return switch (key) {
            case "fishing_rod", "trident", "bow", "crossbow", "shears", "brush", "mace" -> key;
            default -> "";
        };
    }

    @Override
    public boolean nearWater() {
        Block centre = at.getBlock();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -2; dy <= 1; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (centre.getRelative(dx, dy, dz).getType() == Material.WATER) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean wearingAlloy() {
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            if (piece != null && alloyGear.test(piece)) {
                return true;
            }
        }
        return false;
    }
}
