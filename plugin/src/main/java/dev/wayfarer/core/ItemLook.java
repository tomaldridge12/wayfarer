package dev.wayfarer.core;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * The shared tooltip look of Wayfarer's reward items (curios, special gear, supplies, fish, treasures): a small kind
 * line, what it does in a soft tint of the item's colour, then a line of story in italics. Adventure only.
 */
public final class ItemLook {
    /** The kind line and footers: a warm grey. */
    public static final int MUTED = 0xB5AD9C;
    /** Flavour text: a darker warm grey, in italics. */
    public static final int STORY = 0x7A7266;
    private static final int WRAP = 34;

    private ItemLook() {
    }

    /** Plain text in a colour, never italic (lore is italic by default). */
    public static Component text(String text, int rgb) {
        return Component.text(text, TextColor.color(rgb)).decoration(TextDecoration.ITALIC, false);
    }

    /** "✦ Curio · Charm". */
    public static Component kind(String text) {
        return text("✦ " + text, MUTED);
    }

    /** The item's colour lifted towards white, for description lines. */
    public static int soft(int rgb) {
        return TextStyle.mix(rgb, 0xFFFFFF, 0.55);
    }

    /** The item's name in its colour. */
    public static Component name(String name, int rgb) {
        return text(name, rgb);
    }

    /**
     * Kind line, description, flavour and footers, with blank lines between the parts that are present.
     *
     * @param footers short lines at the end in the muted colour ("Right-click · rests 2 min after")
     */
    public static List<Component> lore(String kind, List<String> description, int rgb, String flavour, List<String> footers) {
        List<Component> out = new ArrayList<>();
        out.add(kind(kind));
        if (!description.isEmpty()) {
            out.add(Component.empty());
            description.forEach(d -> out.add(text(d, soft(rgb))));
        }
        if (flavour != null && !flavour.isBlank()) {
            out.add(Component.empty());
            TextStyle.wrap(flavour, WRAP).forEach(l -> out.add(Component.text(l, TextColor.color(STORY))
                    .decoration(TextDecoration.ITALIC, true)));
        }
        if (!footers.isEmpty()) {
            out.add(Component.empty());
            footers.forEach(f -> out.add(text(f, MUTED)));
        }
        return out;
    }

    /** "45s", "2 min", "1 h", "1 h 30 min". */
    public static String duration(int seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }
        if (seconds < 3600) {
            return (seconds / 60) + " min" + (seconds % 60 == 0 ? "" : " " + seconds % 60 + "s");
        }
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        return h + " h" + (m == 0 ? "" : " " + m + " min");
    }
}
