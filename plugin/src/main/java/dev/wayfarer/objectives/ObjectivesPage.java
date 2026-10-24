package dev.wayfarer.objectives;

import dev.wayfarer.core.ItemLook;
import dev.wayfarer.core.JournalPages;
import dev.wayfarer.core.Menus;
import dev.wayfarer.core.progression.Progression;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * The Journal's Objectives page: tokens and streak, the three dailies and the weekly with their progress and what
 * they pay, when they turn over, and a reroll button for each daily not yet done. Main thread.
 */
final class ObjectivesPage {
    static final Map<String, Material> THEME_ICONS = Map.ofEntries(Map.entry("mining", Material.IRON_PICKAXE),
            Map.entry("digging", Material.IRON_SHOVEL), Map.entry("farming", Material.WHEAT), Map.entry("fishing", Material.FISHING_ROD),
            Map.entry("hunting", Material.IRON_SWORD), Map.entry("exploring", Material.COMPASS), Map.entry("crafting", Material.CRAFTING_TABLE),
            Map.entry("archaeology", Material.BRUSH), Map.entry("nether", Material.NETHERRACK), Map.entry("end", Material.END_STONE),
            Map.entry("event", Material.FIREWORK_ROCKET));
    private static final int FADED = 0x8C8475;

    private final ObjectivesService service;
    private final Supplier<ObjectivesService.State> state;
    private final JournalPages journal;
    private final ServerGoals goals;

    ObjectivesPage(ObjectivesService service, Supplier<ObjectivesService.State> state, JournalPages journal, ServerGoals goals) {
        this.service = service;
        this.state = state;
        this.journal = journal;
        this.goals = goals;
    }

    JournalPages.Page page() {
        return new JournalPages.Page("objectives", ItemLook.text("Objectives", ObjectivesService.GOLD),
                ItemLook.text("Three dailies, one weekly, tokens and your streak.", ObjectivesService.INK), 10, this::open);
    }

    void open(Player player) {
        service.with(player.getUniqueId(), (data, snap) -> {
            service.refresh(player.getUniqueId(), data, snap, service.stage(player));
            show(player, data, snap);
        });
    }

    private void show(Player player, ObjectivesPlayerData data, Progression.Snapshot snap) {
        ObjectivesService.State s = state.get();
        Instant now = Instant.now();
        List<DialogBody> body = new ArrayList<>();
        body.add(Menus.text(Component.text("Three a day, one a week. Everything you already do counts towards them.",
                TextColor.color(FADED)).decoration(TextDecoration.ITALIC, true)));
        body.add(Menus.text(ItemLook.text("✦ " + data.tokens + " tokens", ObjectivesService.GOLD)
                .append(ItemLook.text("   Streak " + data.streak + " day" + (data.streak == 1 ? "" : "s"), ObjectivesService.DONE))
                .append(ItemLook.text("   New dailies in " + Schedule.until(now, s.settings().schedule().nextDaily(now)), ObjectivesService.INK))));
        List<Menus.Button> buttons = new ArrayList<>();
        for (int i = 0; i < data.dailies.size(); i++) {
            ObjectivesPlayerData.Slot slot = data.dailies.get(i);
            ObjectiveDefinition d = s.daily().get(slot.id);
            if (d == null) {
                continue;
            }
            body.add(row(d, slot, snap, "Daily"));
            int cost = service.rerollCost(data, i);
            if (cost >= 0) {
                int index = i;
                buttons.add(new Menus.Button(ItemLook.text("Reroll " + (i + 1), ObjectivesService.PAPER),
                        ItemLook.text(cost == 0 ? "Free (once a day)" : "Costs " + cost + " levels", ObjectivesService.INK), 100, p ->
                        service.with(p.getUniqueId(), (dd, ss) -> {
                            service.reroll(p, dd, ss, index, false);
                            show(p, dd, ss);
                        })));
            }
        }
        if (data.weekly != null && s.weekly().containsKey(data.weekly.id)) {
            body.add(Menus.text(ItemLook.text("The weekly · turns over in " + Schedule.until(now, s.settings().schedule().nextWeekly(now)),
                    ObjectivesService.INK)));
            body.add(row(s.weekly().get(data.weekly.id), data.weekly, snap, "Weekly"));
        }
        body.add(Menus.text(ItemLook.text("The server goal · everyone, together", ServerGoals.BLUE)));
        body.add(Menus.item(ItemStack.of(Material.BELL), goals.line(player.getUniqueId())));
        Menus.show(player, ItemLook.text("Objectives", ObjectivesService.GOLD), body, buttons, Math.max(1, buttons.size()), journal.back());
    }

    private DialogBody row(ObjectiveDefinition d, ObjectivesPlayerData.Slot slot, Progression.Snapshot snap, String kind) {
        long progress = Math.min(d.target(), ObjectiveRules.progress(d, snap.counters(), slot.baseline));
        TextComponent.Builder line = Component.text();
        line.append(ItemLook.text(d.name(), slot.done ? ObjectivesService.DONE : ObjectivesService.PAPER))
                .append(ItemLook.text(" · " + d.task(), ObjectivesService.INK)).append(Component.newline());
        if (slot.done) {
            line.append(ItemLook.text("✔ Done", ObjectivesService.DONE));
        } else {
            int filled = (int) Math.round(12.0 * progress / d.target());
            line.append(ItemLook.text("▰".repeat(filled) + "▱".repeat(12 - filled), ObjectivesService.GOLD))
                    .append(ItemLook.text("  " + FeatRules.number(progress) + " / " + FeatRules.number(d.target()), ObjectivesService.INK));
        }
        line.append(Component.newline()).append(ItemLook.text("Pays " + (d.tokens() > 0 ? d.tokens() + " tokens and " : "")
                + "a " + kind.toLowerCase(java.util.Locale.ROOT) + " " + words(d.table()) + " reward", FADED));
        return Menus.item(ItemStack.of(THEME_ICONS.getOrDefault(d.table(), Material.BOOK)), line.build());
    }

    private static String words(String table) {
        return table.replace('_', ' ');
    }
}
