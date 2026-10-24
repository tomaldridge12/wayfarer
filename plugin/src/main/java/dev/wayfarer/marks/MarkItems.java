package dev.wayfarer.marks;

import dev.wayfarer.core.ItemTagService;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

/**
 * Mark templates, the books the book ladders pay, and the mark a tool carries. A template is tagged
 * {@code template:<mark>:<rank>} under the module; a marked tool {@code <mark>:<rank>}, with one tooltip line.
 */
final class MarkItems {
    static final String MODULE = "marks";
    private static final String TEMPLATE = "template:";
    /** What templates are built on, as alloy templates are: no vanilla recipe wants it for them (and the guard). */
    static final Material BASE = Material.BRICK;
    private static final String LINE = "◆ ";
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V"};

    private final ItemTagService tags;
    private final Supplier<MarkRules> rules;

    MarkItems(ItemTagService tags, Supplier<MarkRules> rules) {
        this.tags = tags;
        this.rules = rules;
    }

    static String roman(int n) {
        return n >= 0 && n < ROMAN.length ? ROMAN[n] : String.valueOf(n);
    }

    /** "Dune Mark II" in the mark's colour. */
    Component title(MarkDefinition m, int rank) {
        return Component.text(m.name() + " Mark " + roman(rank), TextColor.color(m.colour())).decoration(TextDecoration.ITALIC, false);
    }

    Optional<ItemStack> template(String mark, int rank) {
        return rules.get().mark(mark).filter(m -> rank >= 1 && rank <= m.topRank()).map(m -> {
            ItemStack item = ItemStack.of(BASE);
            item.setData(DataComponentTypes.ITEM_MODEL, Key.key("wayfarer", mark + "_mark_template"));
            item.setData(DataComponentTypes.ITEM_NAME, Component.text("Smithing Template"));
            item.setData(DataComponentTypes.MAX_STACK_SIZE, 1);
            List<String> materials = m.materials().stream().sorted().limit(3).map(MarkItems::words).toList();
            item.lore(List.of(
                    title(m, rank),
                    Component.empty(),
                    plain("Applies to:", NamedTextColor.GRAY),
                    plain(" " + capital(m.tool().label()), NamedTextColor.BLUE),
                    plain("Ingredients:", NamedTextColor.GRAY),
                    plain(" " + String.join(", ", materials) + (m.materials().size() > 3 ? " ..." : ""), NamedTextColor.BLUE),
                    Component.empty(),
                    plain("Stays in the smithing table", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, true)));
            tags.write(item, MODULE, TEMPLATE + mark + ":" + rank);
            return item;
        });
    }

    /** An enchanted book of the book ladder's enchant at this level; empty when the server lacks the enchant. */
    Optional<ItemStack> book(String book, int level) {
        return rules.get().book(book).flatMap(b -> {
            Enchantment enchant = Registry.ENCHANTMENT.get(NamespacedKey.fromString(b.enchant()));
            if (enchant == null) {
                return Optional.empty();
            }
            ItemStack item = ItemStack.of(Material.ENCHANTED_BOOK);
            item.editMeta(EnchantmentStorageMeta.class, meta -> meta.addStoredEnchant(enchant, level, true));
            return Optional.of(item);
        });
    }

    /** The mark and rank on a template, or empty. */
    Optional<MarkRules.OnTool> templateOf(ItemStack item) {
        return read(item).filter(t -> t.startsWith(TEMPLATE)).flatMap(t -> MarkRules.OnTool.parse(t.substring(TEMPLATE.length())));
    }

    /** The mark on a tool, or empty (templates are not marked tools). */
    Optional<MarkRules.OnTool> markOf(ItemStack item) {
        return read(item).filter(t -> !t.startsWith(TEMPLATE)).flatMap(MarkRules.OnTool::parse);
    }

    /** Whether one more use would break the tool (unbreakable tools never do; no tool always would). */
    static boolean wouldBreak(ItemStack tool) {
        if (tool == null || tool.isEmpty()) {
            return true;
        }
        if (tool.hasData(DataComponentTypes.UNBREAKABLE)) {
            return false;
        }
        Integer max = tool.getData(DataComponentTypes.MAX_DAMAGE);
        int damage = tool.getDataOrDefault(DataComponentTypes.DAMAGE, 0);
        return max != null && damage + 1 >= max;
    }

    private Optional<String> read(ItemStack item) {
        return item == null || item.isEmpty() ? Optional.empty() : tags.read(item, MODULE);
    }

    /** Puts the mark on a copy of the tool: the tag, and one tooltip line in place of any older rank's. */
    Optional<ItemStack> marked(ItemStack tool, MarkRules.OnTool mark) {
        return rules.get().mark(mark.mark()).map(m -> {
            ItemStack out = tool.clone();
            out.setAmount(1);
            String prefix = LINE + m.name() + " Mark";
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(LINE, TextColor.color(m.colour())).decoration(TextDecoration.ITALIC, false)
                    .append(title(m, mark.rank())));
            List<Component> old = out.lore();
            if (old != null) {
                old.stream().filter(line -> !PlainTextComponentSerializer.plainText().serialize(line).startsWith(prefix))
                        .forEach(lore::add);
            }
            out.lore(lore);
            tags.write(out, MODULE, mark.key());
            return out;
        });
    }

    private static Component plain(String text, NamedTextColor colour) {
        return Component.text(text, colour).decoration(TextDecoration.ITALIC, false);
    }

    private static String words(String key) {
        return capital(key.replace('_', ' '));
    }

    private static String capital(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }
}
