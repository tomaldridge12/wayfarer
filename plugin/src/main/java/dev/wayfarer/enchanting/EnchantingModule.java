package dev.wayfarer.enchanting;

import dev.wayfarer.core.Core;
import dev.wayfarer.core.ItemLook;
import dev.wayfarer.core.Module;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Enchanting: sneak and right-click an enchanting table holding lapis lazuli to reshuffle its offers for one level
 * (ADR 0007's one level-priced exception). The reshuffle only gives the player a new enchantment seed, so it never
 * edits offers and ExcellentEnchants stay out of them. Main thread.
 */
public final class EnchantingModule implements Module, Listener {
    private static final int GOLD = 0xD9A55B;
    private static final int PAPER = 0xE8D9A8;
    private static final int INK = 0xE4DAC2;

    private final JavaPlugin plugin;
    private Core core;
    private volatile EnchantingConfigParser.Settings settings;

    public EnchantingModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "enchanting";
    }

    @Override
    public void enable(Core core) throws IOException {
        this.core = core;
        EnchantingConfigParser.Result r = EnchantingConfigParser.parse(core.config().load(id()),
                key -> Material.matchMaterial(key) != null && Material.matchMaterial(key).isItem());
        r.problems().forEach(p -> core.logger().warning("[enchanting] " + p));
        settings = r.settings();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        core.logger().info("Enchanting loaded: reshuffle " + (settings.reshuffle() ? "on" : "off") + ", " + settings.levels()
                + (settings.levels() == 1 ? " level" : " levels") + ", trigger " + settings.trigger() + "; " + r.problems().size() + " problems");
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
    }

    @EventHandler(priority = EventPriority.HIGH)
    void onClick(PlayerInteractEvent event) {
        EnchantingConfigParser.Settings s = settings;
        Block table = event.getClickedBlock();
        if (s == null || !s.reshuffle() || table == null) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = event.getItem();
        boolean onTable = event.getHand() == EquipmentSlot.HAND && event.getAction() == Action.RIGHT_CLICK_BLOCK
                && table.getType() == Material.ENCHANTING_TABLE;
        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        EnchantingRules.Verdict verdict = EnchantingRules.verdict(EnchantingRules.triggers(onTable, player.isSneaking(),
                held == null ? null : held.getType().getKey().getKey(), s.trigger()), player.getLevel(), creative, s.levels());
        if (verdict == EnchantingRules.Verdict.IGNORE) {
            return;
        }
        event.setCancelled(true);
        if (verdict == EnchantingRules.Verdict.NEEDS_LEVEL) {
            player.sendActionBar(ItemLook.text(s.levels() == 1 ? "You need a level to reshuffle" : "You need " + s.levels() + " levels to reshuffle", INK));
            return;
        }
        // Whole levels come off; setExp is left alone, so the bar keeps its fill.
        player.setLevel(EnchantingRules.levelAfter(player.getLevel(), s.levels(), creative));
        player.setEnchantmentSeed(ThreadLocalRandom.current().nextInt());
        var at = table.getLocation().add(0.5, 1.0, 0.5);
        table.getWorld().playSound(at, Sound.ITEM_BOOK_PAGE_TURN, 1f, 0.9f);
        table.getWorld().spawnParticle(Particle.ENCHANT, at, 40, 0.5, 0.4, 0.5, 1.0);
        player.openEnchanting(table.getLocation(), true);
        player.sendActionBar(ItemLook.text("✦ ", GOLD).append(ItemLook.text("The table's pages turn, and new offers appear", PAPER)));
        core.logger().info("[enchanting] " + player.getName() + " reshuffled the table at " + table.getX() + "," + table.getY() + ","
                + table.getZ() + (creative ? " (creative, free)" : " for " + s.levels() + (s.levels() == 1 ? " level" : " levels")));
    }
}
