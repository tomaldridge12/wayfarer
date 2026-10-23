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
    static final TextColor PERK = TextColor.color(0xE0A3FF);

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
        return themed(def.name(), def.colour());
    }

    /** Any name as a bold gradient in a dye colour's theme. */
    public static Component themed(String text, String dyeColour) {
        int dye = DYE.getOrDefault(dyeColour, 0xF9FFFE);
        // Tooltips and labels sit on a dark background, so dark dyes are lifted towards white to stay readable.
        double lift = luminance(dye) < 0.3 ? 0.5 : 0.15;
        return gradient(text, mix(dye, 0xFFFFFF, 0.6), mix(dye, 0xFFFFFF, lift)).decorate(TextDecoration.BOLD);
    }

    /** A family's theme colour: the dye of its banner, or white when it has none. */
    public static String familyColour(BannerSet set, String family) {
        return set.banners().values().stream().filter(b -> b.family().equals(family)).map(BannerDefinition::colour)
                .findFirst().orElse("white");
    }

    public static List<Component> headLore(HeadDefinition def, String earnerName, String earnedOn) {
        List<Component> lines = new ArrayList<>();
        lines.add(plain("☠ Trophy head · " + title(def.family()) + " family", NamedTextColor.DARK_GRAY));
        lines.add(Component.empty());
        lines.add(plain("Place it to display ", NamedTextColor.GRAY).append(plain(earnerName, NamedTextColor.WHITE))
                .append(plain("'s live kill count.", NamedTextColor.GRAY)));
        lines.add(Component.empty());
        lines.add(plain(earned(earnedOn, def.kills(), def.family()), NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, true));
        return lines;
    }

    /** ✦ Rare Relic (cool blue) for tier I, ✦ Epic Relic (purple to gold) for tier II. */
    public static Component rarity(int tier) {
        return tier >= 2 ? gradient("✦ Epic Relic", 0xC77DFF, 0xFFB000).decorate(TextDecoration.BOLD)
                : gradient("✦ Rare Relic", 0x8FD3FF, 0x2E86C1).decorate(TextDecoration.BOLD);
    }

    public static List<Component> relicLore(RelicDefinition def, Collection<String> mobs, String earnedOn) {
        List<Component> lines = new ArrayList<>();
        Component family = MobFamily.ALL.equals(def.family()) ? plain(" · All mobs", NamedTextColor.DARK_GRAY)
                : plain(" · " + title(def.family()) + " family", NamedTextColor.DARK_GRAY);
        lines.add(rarity(def.tier()).append(family));
        lines.add(Component.empty());
        String who = MobFamily.ALL.equals(def.family()) ? "every hostile mob"
                : mobs.isEmpty() ? "the " + def.family() + " family" : mobList(mobs);
        if (def.slot().armour()) {
            lines.add(plain(" ✚ −" + def.edgePercent() + "%", GUARD).append(plain(" damage taken from " + who, NamedTextColor.GRAY)));
        } else {
            lines.add(plain(" ⚔ +" + def.edgePercent() + "%", DAMAGE_UP).append(plain(" damage to " + who, NamedTextColor.GRAY)));
        }
        for (Perk perk : def.perks()) {
            lines.add(plain(" ✦ ", PERK).append(plain(perk.describe(), NamedTextColor.WHITE)));
        }
        lines.add(Component.empty());
        lines.add(plain(earned(earnedOn, def.kills(), def.family()), NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, true));
        return lines;
    }

    /** Action bar when the Wayfarer set completes or breaks. */
    public static Component setActive(boolean on, WayfarerSet.Settings s) {
        return on ? gradient("✦ Wayfarer set ✦", 0xFFE7A0, 0xFF9F1C).decorate(TextDecoration.BOLD)
                .append(plain("  −" + s.reductionPercent() + "% damage from hostile mobs, +" + s.hearts() + " ❤",
                        NamedTextColor.GRAY))
                : plain("Wayfarer set broken", NamedTextColor.DARK_GRAY);
    }

    /** The lore of a relic whose id config no longer defines. */
    public static Component inertRelic() {
        return plain("Inactive: this relic is not in config", NamedTextColor.DARK_GRAY);
    }

    /** "Earned 23 Oct 2026 at 250 zombie kills", or without the date when it is unknown. */
    static String earned(String earnedOn, int kills, String family) {
        String at = "at " + String.format(Locale.ROOT, "%,d", kills) + " "
                + (MobFamily.ALL.equals(family) ? "mob" : family) + " kills";
        return earnedOn == null ? "Earned " + at : "Earned " + earnedOn + " " + at;
    }

    /** A plaque: ☠ Tom's Zombie Trophy over a live "1,342 zombies slain". */
    public static Component plaque(HeadDefinition def, String colour, String earnerName, Integer kills) {
        String count = kills == null ? "…" : String.format(Locale.ROOT, "%,d", kills);
        return plain("☠ ", NamedTextColor.WHITE).append(themed(earnerName + "'s " + def.name(), colour))
                .append(Component.newline())
                .append(plain(count, NamedTextColor.WHITE))
                .append(plain(" " + plural(def.family()) + " slain", NamedTextColor.GRAY));
    }

    /** zombie → zombies, enderman → endermen, all → mobs. */
    static String plural(String family) {
        if (MobFamily.ALL.equals(family)) {
            return "mobs";
        }
        String name = family.replace('_', ' ');
        return name.endsWith("man") ? name.substring(0, name.length() - 3) + "men" : name + "s";
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
        return blocked(def.name(), amount);
    }

    /** "✚ <source> blocked 1.5": a banner or relic armour softened a hit. */
    public static Component blocked(String sourceName, double amount) {
        return plain("✚ ", GUARD).append(plain(sourceName, NamedTextColor.GRAY))
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

    private static final int BAR_WIDTH = 25;
    static final TextColor EARNED = TextColor.color(0xFFC94A);

    /**
     * The /mobbanners progress card: a header, the all-mobs count, then each family with its rewards as
     * fixed-width bars (chat is not monospaced, so bars go first to line up). Hovering a reward shows its item
     * tooltip; with {@code claimButtons}, earned rewards get a clickable claim.
     */
    public static List<Component> progress(String who, PlayerData data, BannerSet set, boolean claimButtons) {
        List<Component> lines = new ArrayList<>();
        lines.add(plain("⚔ ", NamedTextColor.WHITE).append(gradient("Kill progress", 0xFFE7A0, 0xFF9F1C)
                .decorate(TextDecoration.BOLD)).append(plain(" · " + who, NamedTextColor.GRAY)));
        lines.add(plain("All mobs", NamedTextColor.WHITE).append(plain(" · ", NamedTextColor.DARK_GRAY))
                .append(plain(count(data.kills(MobFamily.ALL)), NamedTextColor.GRAY)));
        java.util.Map<String, List<Reward>> byFamily = new java.util.LinkedHashMap<>();
        set.rewards().forEach(r -> byFamily.computeIfAbsent(r.family(), f -> new ArrayList<>()).add(r));
        byFamily.forEach((family, rewards) -> {
            lines.add(plain(title(family), NamedTextColor.WHITE).append(plain(" · ", NamedTextColor.DARK_GRAY))
                    .append(plain(count(data.kills(family)), NamedTextColor.GRAY)));
            for (Reward reward : rewards) {
                lines.add(rewardLine(reward, data, set, claimButtons));
            }
        });
        if (byFamily.isEmpty()) {
            lines.add(plain("No rewards are set up yet.", NamedTextColor.DARK_GRAY));
        }
        return lines;
    }

    private static Component rewardLine(Reward reward, PlayerData data, BannerSet set, boolean claimButtons) {
        int kills = data.kills(reward.family());
        boolean earned = data.isEarned(reward.rewardKey());
        int filled = earned ? BAR_WIDTH : (int) Math.min(BAR_WIDTH, (long) kills * BAR_WIDTH / Math.max(1, reward.kills()));
        Component bar = plain("|".repeat(filled), earned ? EARNED : DAMAGE_UP)
                .append(plain("|".repeat(BAR_WIDTH - filled), NamedTextColor.DARK_GRAY));
        Component status = earned ? plain(" ✔", EARNED)
                : plain(" " + Math.min(kills, reward.kills()) + "/" + reward.kills(), NamedTextColor.GRAY);
        Component name = rewardName(reward, set);
        Component line = plain("  ", NamedTextColor.GRAY).append(bar).append(status)
                .append(plain("  ", NamedTextColor.GRAY)).append(name);
        if (earned && claimButtons) {
            line = line.append(plain("  ", NamedTextColor.GRAY)).append(plain("[Claim copy]", GUARD)
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                            plain("Get another " + reward.name() + " if you lost yours", NamedTextColor.GRAY)))
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(
                            "/mobbanners claim " + reward.rewardKey())));
        }
        return line;
    }

    /** The reward's themed name; hovering it shows the same tooltip as the item. */
    private static Component rewardName(Reward reward, BannerSet set) {
        if (RewardKey.BANNER.equals(reward.rewardKey().type())) {
            var def = set.find(reward.rewardKey().id());
            if (def.isPresent()) {
                MobFamily family = set.families().get(def.get().family());
                Component tooltip = name(def.get());
                for (Component line : lore(def.get(), family == null ? List.of() : family.mobs())) {
                    tooltip = tooltip.append(Component.newline()).append(line);
                }
                return name(def.get()).hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(tooltip));
            }
        }
        if (RewardKey.RELIC.equals(reward.rewardKey().type())) {
            var def = set.findRelic(reward.rewardKey().id());
            if (def.isPresent()) {
                MobFamily family = set.families().get(def.get().family());
                Component tooltip = themed(def.get().name(), familyColour(set, def.get().family()));
                for (Component line : relicLore(def.get(), family == null ? List.of() : family.mobs(), null)) {
                    tooltip = tooltip.append(Component.newline()).append(line);
                }
                return themed(def.get().name(), familyColour(set, def.get().family()))
                        .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(tooltip));
            }
        }
        if (RewardKey.HEAD.equals(reward.rewardKey().type())) {
            var def = set.findHead(reward.rewardKey().id());
            if (def.isPresent()) {
                Component name = themed(def.get().name(), familyColour(set, def.get().family()));
                return name.hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(name));
            }
        }
        return plain(reward.name(), NamedTextColor.WHITE);
    }

    /** "1 kill", "1,342 kills". */
    static String count(int kills) {
        return String.format(Locale.ROOT, "%,d", kills) + (kills == 1 ? " kill" : " kills");
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
