package dev.wayfarer.exploration;

import dev.wayfarer.core.ItemLook;
import dev.wayfarer.core.ItemTagService;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.logging.Logger;
import net.kyori.adventure.key.Key;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Campfire;
import org.bukkit.entity.EntityType;
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
 * Beacon kindling: dropped onto a lit campfire it burns as a tall signal for a while, and a wandering trader comes to
 * see what the fuss is about. The campfire is put back as it was. Main thread.
 */
final class BeaconKindling implements Listener {
    static final String TAG = "kindling";
    private static final int COLOUR = 0xE8743C;

    private final Plugin plugin;
    private final ItemTagService tags;
    private final Supplier<ExplorationConfigParser.Settings> settings;
    private final Logger log;
    private final List<BukkitTask> burning = new ArrayList<>();
    private final List<Block> lit = new ArrayList<>();

    BeaconKindling(Plugin plugin, ItemTagService tags, Supplier<ExplorationConfigParser.Settings> settings, Logger log) {
        this.plugin = plugin;
        this.tags = tags;
        this.settings = settings;
        this.log = log;
    }

    ItemStack item() {
        ItemStack item = ItemStack.of(Material.BRICK);
        item.setData(DataComponentTypes.ITEM_MODEL, Key.key("wayfarer", "beacon_kindling"));
        item.setData(DataComponentTypes.ITEM_NAME, ItemLook.name("Beacon Kindling", COLOUR));
        item.setData(DataComponentTypes.MAX_STACK_SIZE, 16);
        item.lore(ItemLook.lore("Kindling", List.of("Drop it on a lit campfire", "to call a wandering trader."), COLOUR,
                "Damp straw and something that smells of distant markets.", List.of("Right-click a lit campfire")));
        tags.write(item, "exploration", TAG);
        return item;
    }

    boolean is(ItemStack item) {
        return item != null && !item.isEmpty() && tags.read(item, "exploration").filter(TAG::equals).isPresent();
    }

    /** Puts every signal fire back while the module stops. */
    void stop() {
        burning.forEach(BukkitTask::cancel);
        burning.clear();
        lit.forEach(BeaconKindling::unsignal);
        lit.clear();
    }

    @EventHandler(priority = EventPriority.HIGH)
    void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK || !is(event.getItem())) {
            return;
        }
        event.setCancelled(true);
        Block block = event.getClickedBlock();
        Player player = event.getPlayer();
        if (block == null || !(block.getBlockData() instanceof Campfire fire) || !fire.isLit()) {
            player.sendActionBar(ItemLook.text("It needs a lit campfire to catch.", 0xB8AC94));
            return;
        }
        if (lit.contains(block)) {
            player.sendActionBar(ItemLook.text("That fire is already calling.", 0xB8AC94));
            return;
        }
        event.getItem().setAmount(event.getItem().getAmount() - 1);
        boolean wasSignal = fire.isSignalFire();
        fire.setSignalFire(true);
        block.setBlockData(fire);
        lit.add(block);
        block.getWorld().playSound(block.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1f, 0.7f);
        Location top = block.getLocation().add(0.5, 1, 0.5);
        int seconds = settings.get().kindlingSeconds();
        int[] ticks = {0};
        BukkitTask[] task = new BukkitTask[1];
        task[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            ticks[0] += 10;
            block.getWorld().spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, top, 3, 0.2, 0.3, 0.2, 0.02);
            if (ticks[0] == settings.get().traderDelaySeconds() * 20) {
                trader(block, player.getName());
            }
            if (ticks[0] >= seconds * 20) {
                task[0].cancel();
                burning.remove(task[0]);
                lit.remove(block);
                if (!wasSignal) {
                    unsignal(block);
                }
            }
        }, 10, 10);
        burning.add(task[0]);
        log.info("[exploration] " + player.getName() + " lit beacon kindling at " + block.getX() + "," + block.getY() + "," + block.getZ());
    }

    private void trader(Block fire, String caller) {
        Location spot = fire.getLocation().add(ThreadLocalRandom.current().nextInt(-6, 7), 0, ThreadLocalRandom.current().nextInt(-6, 7));
        Location ground = fire.getWorld().getHighestBlockAt(spot).getLocation().add(0.5, 1, 0.5);
        if (Math.abs(ground.getY() - fire.getY()) > 8) {
            ground = fire.getLocation().add(0.5, 1, 1.5);
        }
        fire.getWorld().spawnEntity(ground, EntityType.WANDERING_TRADER);
        fire.getWorld().playSound(ground, Sound.ENTITY_WANDERING_TRADER_YES, 1f, 1f);
        log.info("[exploration] A wandering trader answered " + caller + "'s signal");
    }

    private static void unsignal(Block block) {
        if (block.getBlockData() instanceof Campfire fire && fire.isSignalFire()) {
            fire.setSignalFire(false);
            block.setBlockData(fire);
        }
    }
}
