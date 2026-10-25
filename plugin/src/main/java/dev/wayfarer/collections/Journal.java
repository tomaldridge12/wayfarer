package dev.wayfarer.collections;

import dev.wayfarer.core.ItemLook;
import dev.wayfarer.core.ItemMakers;
import dev.wayfarer.core.JournalPages;
import dev.wayfarer.core.Menus;
import dev.wayfarer.core.TextStyle;
import dev.wayfarer.core.progression.Ladder;
import dev.wayfarer.core.progression.Progression;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * The Journal: a cover listing every page, a page per collection (found entries with their item, unfound ones as a
 * grey shape with a hint, tabs and turning pages), the ladders page, and pages other modules add. Main thread.
 */
final class Journal implements JournalPages {
    static final int PAPER = 0xE8D9A8;
    static final int INK = 0xE4DAC2;
    static final int FADED = 0xB5AD9C;
    static final int DONE = 0x8FCB6B;
    private static final int LADDERS_PER_PAGE = 10;

    /** What the Journal shows: settings, collections in order, and each one's entries. */
    record State(CollectionConfigParser.Settings settings, List<CollectionDefinition> collections,
            Map<String, List<CollectionEntry>> entries) {
        static State empty() {
            return new State(new CollectionConfigParser.Settings(true, 16), List.of(), Map.of());
        }

        List<CollectionEntry> entries(String id) {
            return entries.getOrDefault(id, List.of());
        }
    }

    private record OwnedPage(String owner, Page page) {
    }

    private final Progression progression;
    private final ItemMakers<ItemStack> makers;
    private final Supplier<State> state;
    private final Map<String, OwnedPage> pages = new LinkedHashMap<>();
    private final java.util.function.BiFunction<Player, CollectionEntry, String> detail;
    private final Supplier<Optional<dev.wayfarer.core.TrackerControl>> tracker;

    /** @param detail a found entry's line in place of its count ("Heaviest 2.41 kg"), or null for the usual one */
    Journal(Progression progression, ItemMakers<ItemStack> makers, Supplier<State> state,
            java.util.function.BiFunction<Player, CollectionEntry, String> detail,
            Supplier<Optional<dev.wayfarer.core.TrackerControl>> tracker) {
        this.tracker = tracker;
        this.progression = progression;
        this.makers = makers;
        this.state = state;
        this.detail = detail;
    }

    @Override
    public void add(String owner, Page page) {
        pages.put(page.id(), new OwnedPage(owner, page));
    }

    void drop(String owner) {
        pages.values().removeIf(p -> p.owner().equals(owner));
    }

    @Override
    public Menus.Button back() {
        return new Menus.Button(text("◂ Journal", INK), Component.empty(), 120, this::openCover);
    }

    private Menus.Button close() {
        return new Menus.Button(text("Close", INK), Component.empty(), 120, Menus::close);
    }

    /** Runs the screen once the player's progression record is loaded (it almost always already is). */
    private void withSnapshot(Player player, java.util.function.Consumer<Progression.Snapshot> screen) {
        Optional<Progression.Snapshot> snapshot = progression.snapshot(player.getUniqueId());
        if (snapshot.isPresent()) {
            screen.accept(snapshot.get());
        } else {
            progression.load(player.getUniqueId()).thenRun(() -> progression.snapshot(player.getUniqueId()).ifPresent(screen));
        }
    }

    @Override
    public void openCover(Player player) {
        withSnapshot(player, snap -> cover(player, snap));
    }

    private void cover(Player player, Progression.Snapshot snap) {
        State s = state.get();
        List<DialogBody> body = new ArrayList<>();
        body.add(Menus.text(Component.text("The Journal of " + player.getName(), TextColor.color(PAPER))
                .decoration(TextDecoration.ITALIC, true)));
        TextComponent.Builder summary = Component.text();
        List<Menus.Button> buttons = new ArrayList<>();
        for (CollectionDefinition def : s.collections()) {
            List<CollectionEntry> entries = s.entries(def.id());
            if (entries.isEmpty()) {
                continue;
            }
            long found = CollectionRules.found(entries, snap.counters());
            // Bar on its own line under the title: every bar is the same width, so centred bars line up whatever the title is.
            summary.append(text(def.title() + " · " + found + " of " + entries.size(), INK))
                    .append(Component.newline())
                    .append(text(CollectionRules.bar(found, entries.size(), 12), found == entries.size() ? DONE : def.colour()))
                    .append(Component.newline());
            buttons.add(new Menus.Button(text(def.title(), lift(def.colour())).decorate(TextDecoration.BOLD),
                    text(def.blurb() + "\n" + found + " of " + entries.size() + " " + def.unit(entries.size()), INK), 140,
                    p -> openCollection(p, def.id(), null, 0)));
        }
        body.add(Menus.text(summary.build()));
        buttons.add(new Menus.Button(text("Ladders", 0xE8C666).decorate(TextDecoration.BOLD), text("Every ladder you are climbing, and what is next.", INK), 140,
                p -> openLadders(p, 0)));
        pages.values().stream().map(OwnedPage::page).sorted(Comparator.comparingInt(Page::order))
                .forEach(page -> buttons.add(new Menus.Button(page.label(), page.tooltip(), 140, page.open())));
        Menus.show(player, text("✦ Wayfarer's Journal", PAPER), body, buttons, 2, close());
    }

    void openCollection(Player player, String id, String tab, int page) {
        withSnapshot(player, snap -> collection(player, snap, id, tab, page));
    }

    private void collection(Player player, Progression.Snapshot snap, String id, String tab, int page) {
        State s = state.get();
        CollectionDefinition def = s.collections().stream().filter(c -> c.id().equals(id)).findFirst().orElse(null);
        if (def == null) {
            cover(player, snap);
            return;
        }
        List<CollectionEntry> all = s.entries(id);
        List<String> tabs = all.stream().map(CollectionEntry::tab).filter(t -> !t.isEmpty()).distinct().toList();
        String shown = tabs.isEmpty() ? "" : tab != null && tabs.contains(tab) ? tab : tabs.getFirst();
        List<CollectionEntry> inTab = all.stream().filter(e -> e.tab().equals(shown)).toList();
        int perPage = s.settings().entriesPerPage();
        int pageCount = Math.max(1, (inTab.size() + perPage - 1) / perPage);
        int at = Math.max(0, Math.min(page, pageCount - 1));
        long found = CollectionRules.found(all, snap.counters());

        List<DialogBody> body = new ArrayList<>();
        if (!def.blurb().isEmpty()) {
            body.add(Menus.text(Component.text(def.blurb(), TextColor.color(FADED)).decoration(TextDecoration.ITALIC, true)));
        }
        body.add(Menus.text(text(CollectionRules.bar(found, all.size(), 20), found == all.size() ? DONE : def.colour())
                .append(Component.newline())
                .append(text(found + " of " + all.size() + " " + def.unit(all.size()), PAPER))));
        body.add(Menus.text(milestones(def, all.size(), found)));
        if (!shown.isEmpty()) {
            long tabFound = CollectionRules.found(inTab, snap.counters());
            body.add(Menus.text(text(shown + " · " + tabFound + " of " + inTab.size()
                    + (pageCount > 1 ? " · page " + (at + 1) + " of " + pageCount : ""), INK)));
        }
        for (CollectionEntry e : inTab.subList(at * perPage, Math.min(inTab.size(), (at + 1) * perPage))) {
            long n = CollectionRules.count(e, snap.counters());
            body.add(n > 0 ? found(player, e, n, def.colour()) : unfound(e));
        }

        List<Menus.Button> buttons = new ArrayList<>();
        int next = CollectionRules.nextMilestone(def, all.size(), found);
        if (next >= 0 && tracker.get().isPresent()) {
            dev.wayfarer.core.TrackerControl control = tracker.get().get();
            String trackedId = "collection:" + id + ":" + next;
            String name = milestoneName(def, all.size(), next);
            buttons.add(new Menus.Button(text((control.isTracked(player.getUniqueId(), trackedId) ? "Untrack" : "Track") + " next milestone", INK),
                    text(name, INK), 150, p -> control.toggle(p, trackedId, name, () -> openCollection(p, id, shown, at))));
        }
        for (String t : tabs) {
            buttons.add(new Menus.Button(text(t, t.equals(shown) ? lift(def.colour()) : INK), Component.empty(), 90,
                    p -> openCollection(p, id, t, 0)));
        }
        if (at > 0) {
            buttons.add(new Menus.Button(text("◂ Turn back", INK), Component.empty(), 90, p -> openCollection(p, id, shown, at - 1)));
        }
        if (at < pageCount - 1) {
            buttons.add(new Menus.Button(text("Turn over ▸", INK), Component.empty(), 90, p -> openCollection(p, id, shown, at + 1)));
        }
        Menus.show(player, text(def.title(), def.colour()), body, buttons, Math.max(1, Math.min(4, buttons.size())), back());
    }

    private static String milestoneName(CollectionDefinition def, int size, int index) {
        long at = CollectionRules.threshold(def.milestones().get(index), size);
        return def.title() + ": " + at + " " + def.unit(at);
    }

    /** A collection's milestone for the tracker ({@code collection:<id>:<index>}): found so far out of its count. */
    Optional<dev.wayfarer.core.Trackable> milestone(java.util.UUID player, String rest) {
        int colon = rest.lastIndexOf(':');
        Optional<Progression.Snapshot> snap = progression.snapshot(player);
        if (colon < 0 || snap.isEmpty()) {
            return Optional.empty();
        }
        try {
            String id = rest.substring(0, colon);
            int index = Integer.parseInt(rest.substring(colon + 1));
            State s = state.get();
            CollectionDefinition def = s.collections().stream().filter(c -> c.id().equals(id)).findFirst().orElse(null);
            List<CollectionEntry> all = s.entries(id);
            if (def == null || index < 0 || index >= def.milestones().size() || all.isEmpty()) {
                return Optional.empty();
            }
            long at = CollectionRules.threshold(def.milestones().get(index), all.size());
            return Optional.of(new dev.wayfarer.core.Trackable("collection:" + rest, milestoneName(def, all.size(), index),
                    Math.min(at, CollectionRules.found(all, snap.get().counters())), at));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** "✔ 10 · Tome of Experience" for each milestone, reached ones ticked. */
    private Component milestones(CollectionDefinition def, int size, long found) {
        TextComponent.Builder out = Component.text();
        for (CollectionDefinition.Milestone m : def.milestones()) {
            long at = CollectionRules.threshold(m, size);
            boolean reached = found >= at;
            if (!out.children().isEmpty()) {
                out.append(Component.newline());
            }
            out.append(text((reached ? "✔ " : "◇ ") + at + (m.share() ? " (" + m.at() + ")" : "") + "  ", reached ? DONE : FADED));
            for (int i = 0; i < m.rewards().size(); i++) {
                Component title = progression.rewardTitle(m.rewards().get(i));
                out.append(i > 0 ? text(", ", FADED) : Component.empty())
                        .append(reached ? title.decoration(TextDecoration.STRIKETHROUGH, false) : title.color(TextColor.color(INK)));
            }
        }
        return out.build();
    }

    private DialogBody found(Player player, CollectionEntry e, long n, int colour) {
        ItemStack icon = icon(e.icon(), player);
        Component name = name(e.name()).color(TextColor.color(PAPER)).decoration(TextDecoration.ITALIC, false);
        String line = detail.apply(player, e);
        if (line == null) {
            line = switch (e.verb()) {
                case "found" -> "Found";
                case "visited" -> "Visited";
                default -> n + " " + e.verb();
            };
        }
        return Menus.item(icon, name.append(Component.newline()).append(text(line, TextStyle.mix(colour, 0xFFFFFF, 0.4))));
    }

    private DialogBody unfound(CollectionEntry e) {
        ItemStack shape = ItemStack.of(Material.GRAY_DYE);
        shape.setData(DataComponentTypes.ITEM_MODEL, Key.key("wayfarer", "journal/unknown"));
        shape.setData(DataComponentTypes.ITEM_NAME, text("Not yet found", FADED));
        return DialogBody.item(shape).description(DialogBody.plainMessage(text("? ? ?", FADED).append(Component.newline())
                .append(Component.text(e.hint(), TextColor.color(FADED)).decoration(TextDecoration.ITALIC, true)), 200))
                .showTooltip(false).build();
    }

    void openLadders(Player player, int page) {
        withSnapshot(player, snap -> ladders(player, snap, page));
    }

    private void ladders(Player player, Progression.Snapshot snap, int page) {
        List<Ladder> list = progression.ladders().stream()
                .filter(l -> !l.id().startsWith("collection_") && !l.id().startsWith("feat_")).toList();
        int pageCount = Math.max(1, (list.size() + LADDERS_PER_PAGE - 1) / LADDERS_PER_PAGE);
        int at = Math.max(0, Math.min(page, pageCount - 1));
        List<DialogBody> body = new ArrayList<>();
        body.add(Menus.text(Component.text("Every count you are climbing, and the next thing at the top of it.",
                TextColor.color(FADED)).decoration(TextDecoration.ITALIC, true)));
        for (Ladder l : list.subList(at * LADDERS_PER_PAGE, Math.min(list.size(), (at + 1) * LADDERS_PER_PAGE))) {
            long progress = progress(l, snap);
            Ladder.Rung next = l.rungs().stream().filter(r -> r.threshold() != Ladder.Rung.NEVER && !snap.earned().containsKey(r.reward()))
                    .findFirst().orElse(null);
            ItemStack icon = next == null ? ItemStack.of(Material.LIME_DYE)
                    : progression.rewardItem(next.reward(), player.getUniqueId()).orElse(ItemStack.of(Material.PAPER));
            Component line = text(l.title(), PAPER).append(text(" · " + progress + " " + plural(l.unit(), progress), INK))
                    .append(Component.newline())
                    .append(next == null ? text("Every rung climbed", DONE)
                            : text("Next at " + next.threshold() + ": ", FADED).append(progression.rewardTitle(next.reward())));
            body.add(Menus.item(icon, line));
        }
        List<Menus.Button> buttons = new ArrayList<>();
        if (at > 0) {
            buttons.add(new Menus.Button(text("◂ Turn back", INK), Component.empty(), 90, p -> openLadders(p, at - 1)));
        }
        if (at < pageCount - 1) {
            buttons.add(new Menus.Button(text("Turn over ▸", INK), Component.empty(), 90, p -> openLadders(p, at + 1)));
        }
        Menus.show(player, text("Ladders", 0xD9B44A), body, buttons, 2, back());
    }

    /** Ladder progress from a snapshot, the same sum (or distinct count) core reads. */
    static long progress(Ladder l, Progression.Snapshot snap) {
        dev.wayfarer.core.progression.ProgressionData data = new dev.wayfarer.core.progression.ProgressionData();
        snap.counters().forEach(data::set);
        return l.progress(data);
    }

    private static String plural(String unit, long n) {
        String[] parts = unit.split("\\|");
        return n == 1 ? parts[0] : parts.length > 1 ? parts[1] : parts[0] + "s";
    }

    /** An entry's icon: a vanilla item key, a Wayfarer item key (curio:moss_heart) or a reward (reward:head:zombie). */
    private ItemStack icon(String icon, Player player) {
        if (icon.startsWith("reward:")) {
            return progression.rewardItem(icon.substring(7), player.getUniqueId())
                    .orElse(ItemStack.of(Material.WITHER_SKELETON_SKULL));
        }
        if (icon.contains(":")) {
            return makers.make(icon).orElse(ItemStack.of(Material.PAPER));
        }
        Material m = Material.matchMaterial(icon);
        return ItemStack.of(m != null && m.isItem() ? m : Material.PAPER);
    }

    /** "#plain text", "%translation.key|Fallback", or a bare translation key. */
    static Component name(String name) {
        if (name.startsWith("#")) {
            return Component.text(name.substring(1));
        }
        if (name.startsWith("%")) {
            String[] parts = name.substring(1).split("\\|", 2);
            return Component.translatable(parts[0], parts.length > 1 ? parts[1] : parts[0]);
        }
        return Component.translatable(name);
    }

    /** A colour mixed toward white so it stays readable on the dialog's grey buttons. */
    static int lift(int rgb) {
        int r = (rgb >> 16 & 0xFF), g = (rgb >> 8 & 0xFF), b = (rgb & 0xFF);
        return ((r + (255 - r) * 45 / 100) << 16) | ((g + (255 - g) * 45 / 100) << 8) | (b + (255 - b) * 45 / 100);
    }

    static Component text(String s, int rgb) {
        return ItemLook.text(s, rgb);
    }
}
