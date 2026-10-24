package dev.wayfarer.alloys;

import dev.wayfarer.core.ItemTagService;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Builds alloy ingots, templates and gear (ADR 0004): Wayfarer-tagged items with their own model and stats. Each
 * carries one tag value under the module: {@code ingot:<alloy>}, {@code template:<alloy>} or
 * {@code gear:<alloy>:<shape>}.
 */
final class AlloyItems {
    static final String MODULE = "alloys";
    /** What ingots and templates are built on: no vanilla recipe, trade, furnace or brewing stand wants it for them. */
    static final Material BASE = Material.BRICK;

    private final ItemTagService tags;
    private final Supplier<AlloyRules> rules;
    private final AlloyGear gear;

    AlloyItems(ItemTagService tags, Supplier<AlloyRules> rules) {
        this.tags = tags;
        this.rules = rules;
        this.gear = new AlloyGear();
    }

    /** What a tag value says an item is. */
    record Tag(String kind, String alloy, GearShape shape) {
        static final String INGOT = "ingot";
        static final String TEMPLATE = "template";
        static final String GEAR = "gear";

        static Optional<Tag> parse(String text) {
            String[] parts = text.split(":");
            if (parts.length == 2 && (parts[0].equals(INGOT) || parts[0].equals(TEMPLATE))) {
                return Optional.of(new Tag(parts[0], parts[1], null));
            }
            if (parts.length == 3 && parts[0].equals(GEAR)) {
                return GearShape.parse(parts[2]).map(s -> new Tag(GEAR, parts[1], s));
            }
            return Optional.empty();
        }

        AlloyRules.Gear gear() {
            return new AlloyRules.Gear(alloy, shape);
        }
    }

    Optional<Tag> tag(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return Optional.empty();
        }
        return tags.read(item, MODULE).flatMap(Tag::parse);
    }

    /** The alloy of an ingot, or empty for anything else. */
    Optional<String> ingotAlloy(ItemStack item) {
        return tag(item).filter(t -> t.kind().equals(Tag.INGOT)).map(Tag::alloy);
    }

    Optional<String> templateAlloy(ItemStack item) {
        return tag(item).filter(t -> t.kind().equals(Tag.TEMPLATE)).map(Tag::alloy);
    }

    Optional<AlloyRules.Gear> gearOf(ItemStack item) {
        return tag(item).filter(t -> t.kind().equals(Tag.GEAR)).map(Tag::gear);
    }

    ItemStack ingot(AlloyDefinition a, int amount) {
        ItemStack item = ItemStack.of(BASE, amount);
        item.setData(DataComponentTypes.ITEM_MODEL, Key.key("wayfarer", a.id() + "_ingot"));
        item.setData(DataComponentTypes.ITEM_NAME, Component.text(a.name() + " Ingot", TextColor.color(a.colour())));
        tags.write(item, MODULE, Tag.INGOT + ":" + a.id());
        return item;
    }

    /** Looks like a vanilla smithing template: what it upgrades and with what. */
    ItemStack template(AlloyDefinition a, int amount) {
        ItemStack item = ItemStack.of(BASE, amount);
        item.setData(DataComponentTypes.ITEM_MODEL, Key.key("wayfarer", a.id() + "_template"));
        item.setData(DataComponentTypes.ITEM_NAME, Component.text("Smithing Template"));
        String tier = a.upgrades().substring(0, 1).toUpperCase(java.util.Locale.ROOT) + a.upgrades().substring(1);
        item.lore(List.of(
                plain(a.name() + " Upgrade", TextColor.color(a.colour())),
                Component.empty(),
                plain("Applies to:", NamedTextColor.GRAY),
                plain(" " + tier + " Equipment or other alloys", NamedTextColor.BLUE),
                plain("Ingredients:", NamedTextColor.GRAY),
                plain(" " + a.name() + " Ingot", NamedTextColor.BLUE)));
        tags.write(item, MODULE, Tag.TEMPLATE + ":" + a.id());
        return item;
    }

    /** A new alloy piece, or empty for an alloy config does not have. */
    Optional<ItemStack> gear(AlloyRules.Gear g) {
        return rules.get().alloy(g.alloy()).flatMap(a -> rules.get().stats(g).map(stats -> {
            ItemStack item = ItemStack.of(Material.matchMaterial(stats.item()));
            gear.apply(item, a, g, stats, 0);
            tags.write(item, MODULE, Tag.GEAR + ":" + g.key());
            return item;
        }));
    }

    /**
     * Turns another item into this alloy's piece (a smithing upgrade): same enchants, trim, name, other modules'
     * tags and share of durability used, on the alloy's material and stats.
     */
    Optional<ItemStack> convert(ItemStack from, AlloyRules.Gear g) {
        return rules.get().alloy(g.alloy()).flatMap(a -> rules.get().stats(g).map(stats -> {
            double used = AlloyGear.used(from); // before withType: a plain diamond pickaxe's max durability is its type's
            ItemStack item = from.withType(Material.matchMaterial(stats.item()));
            item.setAmount(1);
            gear.apply(item, a, g, stats, used);
            tags.write(item, MODULE, Tag.GEAR + ":" + g.key());
            return item;
        }));
    }

    /** Re-applies an alloy piece's stats from current config; true if the item is alloy gear that changed. */
    boolean refresh(ItemStack item) {
        Optional<AlloyRules.Gear> g = gearOf(item);
        if (g.isEmpty()) {
            return false;
        }
        Optional<AlloyDefinition> a = rules.get().alloy(g.get().alloy());
        Optional<AlloyRules.Stats> stats = rules.get().stats(g.get());
        if (a.isEmpty() || stats.isEmpty() || !stats.get().item().equals(item.getType().getKey().getKey())) {
            return false; // removed from config, or its material changed: left as it is
        }
        gear.apply(item, a.get(), g.get(), stats.get(), AlloyGear.used(item));
        return true;
    }

    /** Whatever an item tag names: {@code ingot:steel}, {@code template:steel} or {@code steel_pickaxe}. */
    Optional<ItemStack> byName(String name, int amount) {
        AlloyRules r = rules.get();
        if (name.startsWith(Tag.INGOT + ":")) {
            return r.alloy(name.substring(Tag.INGOT.length() + 1)).map(a -> ingot(a, amount));
        }
        if (name.startsWith(Tag.TEMPLATE + ":")) {
            return r.alloy(name.substring(Tag.TEMPLATE.length() + 1)).map(a -> template(a, amount));
        }
        int cut = name.lastIndexOf('_');
        if (cut <= 0) {
            return Optional.empty();
        }
        return GearShape.parse(name.substring(cut + 1)).flatMap(s -> gear(new AlloyRules.Gear(name.substring(0, cut), s)));
    }

    /** Every name {@link #byName} accepts, for suggestions. */
    List<String> names() {
        List<String> out = new java.util.ArrayList<>();
        for (AlloyDefinition a : rules.get().alloys()) {
            out.add(Tag.INGOT + ":" + a.id());
            out.add(Tag.TEMPLATE + ":" + a.id());
            for (GearShape s : GearShape.values()) {
                out.add(a.id() + "_" + s.id());
            }
        }
        return out;
    }

    /** A built item's real components, for the start-up check: material, model, durability, tool speed, attributes. */
    static String describe(ItemStack item) {
        StringBuilder out = new StringBuilder(item.getType().getKey().getKey());
        out.append(" model ").append(item.getData(DataComponentTypes.ITEM_MODEL).asString());
        out.append(", ").append(item.getData(DataComponentTypes.MAX_DAMAGE)).append(" durability");
        var tool = item.getData(DataComponentTypes.TOOL);
        if (tool != null) {
            out.append(", speed ").append(tool.rules().stream().map(r -> r.speed()).filter(java.util.Objects::nonNull)
                    .findFirst().orElse(tool.defaultMiningSpeed()));
        }
        var worn = item.getData(DataComponentTypes.EQUIPPABLE);
        if (worn != null && worn.assetId() != null) {
            out.append(", worn ").append(worn.assetId().asString());
        }
        out.append(", repairable ").append(item.hasData(DataComponentTypes.REPAIRABLE));
        out.append(", attributes [");
        out.append(String.join(", ", item.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS).modifiers().stream()
                .map(e -> e.attribute().getKey().getKey() + " " + e.modifier().getAmount()).toList()));
        return out.append("]").toString();
    }

    private static Component plain(String text, TextColor colour) {
        return Component.text(text, colour).decoration(TextDecoration.ITALIC, false);
    }
}
