package dev.wayfarer.objectives;

import dev.wayfarer.core.ItemLook;
import dev.wayfarer.core.JournalPages;
import dev.wayfarer.core.Menus;
import dev.wayfarer.core.progression.Progression;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * The Journal's Feats page: every feat with what it pays, earned ones ticked with their date, the rest with how far
 * along you are, and hidden ones as "? ? ?" until earned. Main thread.
 */
final class FeatsPage {
    static final int GOLD = 0xE8B04A;
    private static final int PER_PAGE = 8;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK).withZone(ZoneId.of("Europe/London"));
    private static final int PAPER = 0xE8D9A8;
    private static final int INK = 0xE4DAC2;
    private static final int FADED = 0xB5AD9C;
    private static final int DONE = 0x8FCB6B;

    private final Progression progression;
    private final Supplier<Map<String, Feat>> feats;
    private final JournalPages journal;

    FeatsPage(Progression progression, Supplier<Map<String, Feat>> feats, JournalPages journal) {
        this.progression = progression;
        this.feats = feats;
        this.journal = journal;
    }

    JournalPages.Page page() {
        return new JournalPages.Page("feats", text("Feats", GOLD), text("Odd milestones on everything you do, each with a prize to fit.", INK),
                20, p -> open(p, 0));
    }

    void open(Player player, int page) {
        Progression.Snapshot snap = progression.snapshot(player.getUniqueId()).orElse(null);
        if (snap == null) {
            progression.load(player.getUniqueId()).thenRun(() -> open(player, page));
            return;
        }
        List<Feat> all = List.copyOf(feats.get().values());
        long earned = all.stream().filter(f -> snap.earned().containsKey(f.marker())).count();
        int pages = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
        int at = Math.max(0, Math.min(page, pages - 1));
        List<DialogBody> body = new ArrayList<>();
        body.add(Menus.text(Component.text("Strange little milestones. Some keep themselves secret until you stumble on them.",
                TextColor.color(FADED)).decoration(TextDecoration.ITALIC, true)));
        body.add(Menus.text(text(bar(earned, all.size()), earned == all.size() ? DONE : GOLD)
                .append(text("  " + earned + " of " + all.size() + " feats" + (pages > 1 ? " · page " + (at + 1) + " of " + pages : ""), PAPER))));
        for (Feat f : all.subList(at * PER_PAGE, Math.min(all.size(), (at + 1) * PER_PAGE))) {
            body.add(row(player, f, FeatRules.status(f, snap.counters(), snap.earned()), snap.earned().getOrDefault(f.marker(), 0L)));
        }
        List<Menus.Button> buttons = new ArrayList<>();
        if (at > 0) {
            buttons.add(new Menus.Button(text("◂ Turn back", INK), Component.empty(), 90, p -> open(p, at - 1)));
        }
        if (at < pages - 1) {
            buttons.add(new Menus.Button(text("Turn over ▸", INK), Component.empty(), 90, p -> open(p, at + 1)));
        }
        Menus.show(player, text("Feats", GOLD), body, buttons, 2, journal.back());
    }

    private DialogBody row(Player player, Feat f, FeatRules.Status status, long earnedAt) {
        if (!status.shown(f)) {
            ItemStack shape = ItemStack.of(Material.GRAY_DYE);
            shape.setData(DataComponentTypes.ITEM_MODEL, Key.key("wayfarer", "journal/unknown"));
            return DialogBody.item(shape).description(DialogBody.plainMessage(text("? ? ?", FADED).append(Component.newline())
                    .append(Component.text("A hidden feat. You'll know it when you do it.", TextColor.color(FADED))
                            .decoration(TextDecoration.ITALIC, true)), 220)).showTooltip(false).build();
        }
        TextComponent.Builder line = Component.text();
        line.append(text(f.name(), status.earned() ? GOLD : PAPER)).append(Component.newline());
        if (status.earned()) {
            line.append(text("✔ Earned " + DATE.format(Instant.ofEpochMilli(earnedAt)), DONE));
        } else {
            line.append(text(FeatRules.number(Math.min(status.progress(), status.at())) + " / " + FeatRules.number(status.at()), INK));
        }
        line.append(Component.newline()).append(Component.text(f.flavour(), TextColor.color(FADED)).decoration(TextDecoration.ITALIC, true))
                .append(Component.newline()).append(text("Pays ", FADED));
        for (int i = 0; i < f.rewards().size(); i++) {
            line.append(i > 0 ? text(", ", FADED) : Component.empty()).append(progression.rewardTitle(f.rewards().get(i)));
        }
        return Menus.item(icon(player, f), line.build());
    }

    /** The first reward that is an item, so the row shows what the feat pays; a name tag for cosmetics only. */
    private ItemStack icon(Player player, Feat f) {
        for (String reward : f.rewards()) {
            var item = progression.rewardItem(reward, player.getUniqueId());
            if (item.isPresent()) {
                return item.get();
            }
        }
        return ItemStack.of(Material.NAME_TAG);
    }

    private static String bar(long done, long total) {
        int filled = total <= 0 ? 0 : (int) Math.round(20.0 * done / total);
        return "▰".repeat(filled) + "▱".repeat(20 - filled);
    }

    static Component text(String s, int rgb) {
        return ItemLook.text(s, rgb);
    }
}
