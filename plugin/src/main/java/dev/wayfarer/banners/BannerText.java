package dev.wayfarer.banners;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Every player-facing banner text: item name and lore, the floating label, the action bar and damage popups.
 * Uses only Adventure text (no server types), so it is unit tested. Each banner is themed by its base colour.
 */
public final class BannerText {
    static final TextColor DAMAGE_UP = TextColor.color(0x55FF55);
    static final TextColor GUARD = TextColor.color(0x55FFFF);
    static final TextColor EXTRA = TextColor.color(0xFF5555);
    static final TextColor CRIT = TextColor.color(0xFFE066);

    /** Minecraft's dye colours, used to theme each banner's name. */
    private static final Map<String, Integer> DYE = Map.ofEntries(
            Map.entry("white", 0xF9FFFE), Map.entry("orange", 0xF9801D), Map.entry("magenta", 0xC74EBD),
            Map.entry("light_blue", 0x3AB3DA), Map.entry("yellow", 0xFED83D), Map.entry("lime", 0x80C71F),
            Map.entry("pink", 0xF38BAA), Map.entry("gray", 0x474F52), Map.entry("light_gray", 0x9D9D97),
            Map.entry("cyan", 0x169C9C), Map.entry("purple", 0x8932B8), Map.entry("blue", 0x3C44AA),
            Map.entry("brown", 0x835432), Map.entry("green", 0x5E7C16), Map.entry("red", 0xB02E26),
            Map.entry("black", 0x1D1D21));

    private BannerText() {
    }

    /** The banner's name as a bold gradient from a light to a rich tint of its colour. */
    public static Component name(BannerDefinition def) {
        int dye = DYE.getOrDefault(def.colour(), 0xF9FFFE);
        // Tooltips and labels sit on a dark background, so dark dyes are lifted towards white to stay readable.
        double lift = luminance(dye) < 0.3 ? 0.5 : 0.15;
        return gradient(def.name(), mix(dye, 0xFFFFFF, 0.6), mix(dye, 0xFFFFFF, lift)).decorate(TextDecoration.BOLD);
    }

    public static List<Component> lore(BannerDefinition def, Collection<String> mobs) {
        List<Component> lines = new ArrayList<>();
        lines.add(plain("Trophy banner · " + title(def.family()) + " family", NamedTextColor.DARK_GRAY));
        lines.add(Component.empty());
        lines.add(plain("Place it to guard everyone within ", NamedTextColor.GRAY)
                .append(plain(def.radius() + " blocks", NamedTextColor.WHITE))
                .append(plain(":", NamedTextColor.GRAY)));
        String who = mobs.isEmpty() ? "the " + def.family() + " family" : mobList(mobs);
        if (def.damageBonusPercent() > 0) {
            lines.add(plain(" ⚔ +" + def.damageBonusPercent() + "%", DAMAGE_UP)
                    .append(plain(" damage to " + who, NamedTextColor.GRAY)));
        }
        if (def.damageReductionPercent() > 0) {
            lines.add(plain(" ✚ −" + def.damageReductionPercent() + "%", GUARD)
                    .append(plain(" damage taken from them", NamedTextColor.GRAY)));
        }
        lines.add(Component.empty());
        lines.add(plain("Earned by slaying " + def.kills() + " of them.", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, true));
        return lines;
    }

    /** The floating label: themed name over a short effect line. */
    public static Component label(String bannerId, java.util.Optional<BannerDefinition> def) {
        if (def.isEmpty()) {
            return plain(bannerId, NamedTextColor.GRAY).append(Component.newline())
                    .append(plain("Inactive: not in config", NamedTextColor.DARK_GRAY));
        }
        return name(def.get()).append(Component.newline()).append(effects(def.get()));
    }

    /** Action bar on entering a zone: ⚑ name │ ⚔ +10% damage  ✚ −10% taken. */
    public static Component entered(BannerDefinition def) {
        return plain("⚑ ", NamedTextColor.WHITE).append(name(def))
                .append(plain("  │  ", NamedTextColor.DARK_GRAY)).append(effects(def));
    }

    /** Action bar on leaving: quiet and grey. */
    public static Component left(BannerDefinition def) {
        return plain("Left ", NamedTextColor.DARK_GRAY).append(plain(def.name(), NamedTextColor.GRAY));
    }

    /** Action bar flash when a banner softened a hit on the player. */
    public static Component blocked(BannerDefinition def, double amount) {
        return plain("✚ ", GUARD).append(plain(def.name(), NamedTextColor.GRAY))
                .append(plain(" blocked ", NamedTextColor.DARK_GRAY))
                .append(plain(DamageNumbers.format(amount), GUARD));
    }

    /** Damage popup: white (yellow on a critical), and for a boosted hit ▲ in green and the extra in red. */
    public static Component popup(DamageNumbers.Split split, boolean critical) {
        Component base = plain(DamageNumbers.format(split.base()), critical ? CRIT : NamedTextColor.WHITE);
        if (split.extra() <= 0) {
            return base;
        }
        return plain("▲ ", DAMAGE_UP).decorate(TextDecoration.BOLD).append(base)
                .append(plain(" +" + DamageNumbers.format(split.extra()), EXTRA).decorate(TextDecoration.BOLD));
    }

    private static Component effects(BannerDefinition def) {
        Component out = Component.empty();
        boolean any = false;
        if (def.damageBonusPercent() > 0) {
            out = out.append(plain("⚔ +" + def.damageBonusPercent() + "%", DAMAGE_UP))
                    .append(plain(" damage", NamedTextColor.GRAY));
            any = true;
        }
        if (def.damageReductionPercent() > 0) {
            out = out.append(plain(any ? "  " : "", NamedTextColor.GRAY))
                    .append(plain("✚ −" + def.damageReductionPercent() + "%", GUARD))
                    .append(plain(" taken", NamedTextColor.GRAY));
            any = true;
        }
        return any ? out : plain("No effect", NamedTextColor.GRAY);
    }

    /** "Drowned, Husk and Zombie" from mob type names, sorted. */
    static String mobList(Collection<String> mobs) {
        List<String> names = mobs.stream().sorted().map(BannerText::title).toList();
        if (names.size() == 1) {
            return names.getFirst();
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.getLast();
    }

    /** cave_spider → Cave Spider */
    static String title(String id) {
        return Arrays.stream(id.split("_")).filter(w -> !w.isEmpty())
                .map(w -> w.substring(0, 1).toUpperCase(Locale.ROOT) + w.substring(1))
                .collect(Collectors.joining(" "));
    }

    /** Text with each character coloured along a gradient from {@code from} to {@code to}. */
    static TextComponent gradient(String text, int from, int to) {
        TextComponent.Builder out = Component.text();
        int[] cps = text.codePoints().toArray();
        for (int i = 0; i < cps.length; i++) {
            double t = cps.length == 1 ? 0 : (double) i / (cps.length - 1);
            out.append(Component.text(new String(Character.toChars(cps[i])), TextColor.color(mix(from, to, t))));
        }
        return out.build().decoration(TextDecoration.ITALIC, false);
    }

    static double luminance(int rgb) {
        return (0.2126 * ((rgb >> 16) & 0xFF) + 0.7152 * ((rgb >> 8) & 0xFF) + 0.0722 * (rgb & 0xFF)) / 255;
    }

    /** Blends two RGB colours: t = 0 gives a, t = 1 gives b. */
    static int mix(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static Component plain(String text, TextColor colour) {
        return Component.text(text, colour).decoration(TextDecoration.ITALIC, false);
    }
}
