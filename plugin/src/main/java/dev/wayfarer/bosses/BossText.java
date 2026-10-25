package dev.wayfarer.bosses;

import dev.wayfarer.core.TextStyle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** Player-facing boss text. Adventure only, so it is unit tested. */
public final class BossText {
    static final TextColor STAR = TextColor.color(0xFFC94A);

    private BossText() {
    }

    /** "Plague Bearer ★★★": the variant's gradient name and gold stars. */
    public static Component name(BossVariant variant, int stars) {
        return TextStyle.gradient(variant.name(), variant.colourFrom(), variant.colourTo()).decorate(TextDecoration.BOLD)
                .append(Component.text(" " + "★".repeat(StarScale.clamp(stars)), STAR)
                        .decoration(TextDecoration.BOLD, false).decoration(TextDecoration.ITALIC, false));
    }

    /** "⚠ Plague Bearer is enraged!" when a boss enters phase two. */
    public static Component enraged(BossVariant variant) {
        return Component.text("⚠ ", NamedTextColor.RED)
                .append(TextStyle.gradient(variant.name(), variant.colourFrom(), variant.colourTo()).decorate(TextDecoration.BOLD))
                .append(Component.text(" is enraged!", NamedTextColor.RED).decoration(TextDecoration.BOLD, false));
    }

    /** "✦ Alice defeated Plague Bearer ★★★ with Bob and Carol". */
    public static Component defeated(BossVariant variant, int stars, String killer, java.util.List<String> helpers) {
        Component line = Component.text("✦ ", NamedTextColor.GOLD).append(Component.text(killer, NamedTextColor.WHITE))
                .append(Component.text(" defeated ", NamedTextColor.GRAY)).append(name(variant, stars));
        if (!helpers.isEmpty()) {
            line = line.append(Component.text(" with ", NamedTextColor.GRAY))
                    .append(Component.text(joined(helpers), NamedTextColor.WHITE));
        }
        return line;
    }

    /** Hover for the defeat line: the boss and everyone who fought it. */
    public static Component defeatedHover(BossVariant variant, int stars, java.util.List<String> fighters) {
        return name(variant, stars).appendNewline()
                .append(Component.text("Fought by " + joined(fighters), NamedTextColor.GRAY));
    }

    /** "Bob", "Bob and Carol", "Bob, Carol and Dan". */
    static String joined(java.util.List<String> names) {
        if (names.size() <= 1) {
            return String.join("", names);
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
    }

    public static Component list(BossRoster roster) {
        Component out = Component.text("Bosses (" + roster.bosses().size() + "):", NamedTextColor.GOLD);
        for (BossDefinition b : roster.bosses().values()) {
            out = out.append(Component.newline()).append(Component.text(" " + b.id() + ": ", NamedTextColor.WHITE))
                    .append(Component.text(String.join(", ", b.variants().stream().map(BossVariant::id).toList()),
                            NamedTextColor.GRAY));
        }
        return out;
    }
}
