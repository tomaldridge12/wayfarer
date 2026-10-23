package dev.wayfarer.banners;

import dev.wayfarer.core.ItemTagService;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/** Builds trophy head items and stands heads in the world. */
final class TrophyHeads {
    private static final String MODULE = "banners";
    private static final String EARNER = "earner";
    private static final String EARNED = "earned";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ROOT)
            .withZone(ZoneId.systemDefault());

    /** Mobs with a vanilla head; every other family uses a player head showing Mojang's MHF_ skin for the mob. */
    private static final Map<String, Material> VANILLA = Map.of(
            "zombie", Material.ZOMBIE_HEAD, "skeleton", Material.SKELETON_SKULL, "creeper", Material.CREEPER_HEAD,
            "wither_skeleton", Material.WITHER_SKELETON_SKULL, "piglin", Material.PIGLIN_HEAD,
            "ender_dragon", Material.DRAGON_HEAD);

    private final ItemTagService tags;
    private final Supplier<BannerSet> banners;
    private final Server server;

    TrophyHeads(ItemTagService tags, Supplier<BannerSet> banners, Server server) {
        this.tags = tags;
        this.banners = banners;
        this.server = server;
    }

    ItemStack create(HeadDefinition def, UUID earner, String earnerName, long earnedAtMillis) {
        ItemStack item = new ItemStack(material(def.family()));
        String colour = BannerText.familyColour(banners.get(), def.family());
        item.editMeta(SkullMeta.class, meta -> {
            if (item.getType() == Material.PLAYER_HEAD) {
                // Only a name: the 26.x client resolves the skin itself (unverified in game).
                meta.setPlayerProfile(server.createProfile(mhfName(def.family())));
            }
            meta.displayName(BannerText.themed(def.name(), colour));
            meta.lore(BannerText.headLore(def, earnerName, earnedAtMillis > 0 ? date(earnedAtMillis) : null));
            meta.setEnchantmentGlintOverride(true);
        });
        tags.write(item, MODULE, new RewardKey(RewardKey.HEAD, def.id()).toString());
        tags.write(item, MODULE, EARNER, earner.toString());
        tags.write(item, MODULE, EARNED, Long.toString(earnedAtMillis));
        return item;
    }

    /** When the head on this item was earned, in epoch milliseconds, or 0 if unknown. */
    long earnedAtOf(ItemStack item) {
        return tags.read(item, MODULE, EARNED).map(text -> {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException e) {
                return 0L;
            }
        }).orElse(0L);
    }

    /** The earner recorded on a head item. */
    Optional<UUID> earnerOf(ItemStack item) {
        return tags.read(item, MODULE, EARNER).flatMap(text -> {
            try {
                return Optional.of(UUID.fromString(text));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        });
    }

    /** Re-tags a vanilla head drop (for heads whose id config no longer knows). */
    void tag(ItemStack item, String headId, UUID earner) {
        tags.write(item, MODULE, new RewardKey(RewardKey.HEAD, headId).toString());
        if (earner != null) {
            tags.write(item, MODULE, EARNER, earner.toString());
        }
    }

    /** Operator tool: stands a floor head of this family at the block. */
    void stand(Block block, HeadDefinition def) {
        Material material = material(def.family());
        block.setType(material);
        if (material == Material.PLAYER_HEAD && block.getState() instanceof Skull skull) {
            skull.setPlayerProfile(server.createProfile(mhfName(def.family())));
            skull.update();
        }
    }

    static boolean isHeadBlock(Material type) {
        String name = type.name();
        return name.endsWith("_HEAD") || name.endsWith("_SKULL");
    }

    static String date(long epochMillis) {
        return DATE.format(Instant.ofEpochMilli(epochMillis));
    }

    private static Material material(String family) {
        return VANILLA.getOrDefault(family, Material.PLAYER_HEAD);
    }

    /** cave_spider → MHF_CaveSpider */
    private static String mhfName(String family) {
        return "MHF_" + BannerText.title(family).replace(" ", "");
    }
}
