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
