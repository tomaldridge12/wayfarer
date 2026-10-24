package dev.wayfarer.marks;

import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

/**
 * The Dune and Reaper marks: breaking one block breaks an area around it as the player (so protection, durability,
 * drops and counters all apply as if they had broken each one). A Reaper hoe also replants every crop it takes from
 * that crop's own drops.
 */
final class AreaMarks implements Listener {
    /** Crops a Reaper hoe harvests and replants. */
    private static final Set<String> CROPS = Set.of("wheat", "carrots", "potatoes", "beetroots", "nether_wart");

    private final Supplier<MarkRules> rules;
    private final MarkItems items;
    private final Map<UUID, BlockFace> lastFace = new HashMap<>();
    /** Players whose extra breaks are in progress; their nested break events start no new area. */
    private final Set<UUID> breaking = new HashSet<>();

    AreaMarks(Supplier<MarkRules> rules, MarkItems items) {
        this.rules = rules;
        this.items = items;
    }

    /** The face a player starts digging, so the area lies on the face they see. */
    @EventHandler(priority = EventPriority.MONITOR)
    void onDig(PlayerInteractEvent event) {
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            lastFace.put(event.getPlayer().getUniqueId(), event.getBlockFace());
        }
    }

    @EventHandler
    void onQuit(PlayerQuitEvent event) {
        lastFace.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (breaking.contains(player.getUniqueId()) || player.isSneaking()) {
            return;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        Optional<MarkRules.OnTool> mark = items.markOf(tool);
        if (mark.isEmpty()) {
            return;
        }
        Block block = event.getBlock();
        String key = block.getType().getKey().getKey();
        MarkRules r = rules.get();
        Face facing = face(player.getFacing());
        if (mark.get().mark().equals("dune") && r.duneDigs(mark.get().rank(), key)) {
            Face face = face(lastFace.getOrDefault(player.getUniqueId(), BlockFace.UP));
            breakAround(player, block, r.dune(mark.get().rank(), face, facing,
                    o -> block.getRelative(o.dx(), o.dy(), o.dz()).getType().getKey().getKey()), false);
        } else if (mark.get().mark().equals("reaper") && ripe(block)) {
            breakAround(player, block, r.reaper(mark.get().rank(), facing).stream()
                    .filter(o -> ripe(block.getRelative(o.dx(), o.dy(), o.dz()))).toList(), true);
        }
    }

    private void breakAround(Player player, Block origin, java.util.List<Offset> offsets, boolean wearTool) {
        breaking.add(player.getUniqueId());
        try {
            for (Offset o : offsets) {
                ItemStack tool = player.getInventory().getItemInMainHand();
                if (wouldBreak(tool) || items.markOf(tool).isEmpty()) {
                    return; // stop before the tool breaks (or if it is gone)
                }
                if (player.breakBlock(origin.getRelative(o.dx(), o.dy(), o.dz())) && wearTool
                        && player.getGameMode() != GameMode.CREATIVE) {
                    player.damageItemStack(org.bukkit.inventory.EquipmentSlot.HAND, 1);
                }
            }
        } finally {
            breaking.remove(player.getUniqueId());
        }
    }

    /** A Reaper hoe replants each crop it takes, using one seed from that crop's own drops. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onDrops(BlockDropItemEvent event) {
        BlockState was = event.getBlockState();
        if (!CROPS.contains(was.getType().getKey().getKey()) || !(was.getBlockData() instanceof Ageable crop)
                || crop.getAge() < crop.getMaximumAge()) {
            return;
        }
        Optional<MarkRules.OnTool> mark = items.markOf(event.getPlayer().getInventory().getItemInMainHand());
        if (mark.isEmpty() || !mark.get().mark().equals("reaper")) {
            return;
        }
        Material seed = was.getBlockData().getPlacementMaterial();
        for (Item drop : event.getItems()) {
            ItemStack stack = drop.getItemStack();
            if (stack.getType() == seed) {
                stack.setAmount(stack.getAmount() - 1);
                drop.setItemStack(stack);
                BlockData young = was.getBlockData().clone();
                ((Ageable) young).setAge(0);
                event.getBlock().setBlockData(young);
                break;
            }
        }
        event.getItems().removeIf(i -> i.getItemStack().isEmpty());
    }

    private static boolean ripe(Block block) {
        return CROPS.contains(block.getType().getKey().getKey()) && block.getBlockData() instanceof Ageable crop
                && crop.getAge() >= crop.getMaximumAge();
    }

    private static boolean wouldBreak(ItemStack tool) {
        if (tool == null || tool.isEmpty() || tool.hasData(DataComponentTypes.UNBREAKABLE)) {
            return tool == null || tool.isEmpty();
        }
        Integer max = tool.getData(DataComponentTypes.MAX_DAMAGE);
        int damage = tool.getDataOrDefault(DataComponentTypes.DAMAGE, 0);
        return max != null && damage + 1 >= max;
    }

    private static Face face(BlockFace face) {
        return Face.parse(face.name()).orElse(Face.UP);
    }
}
