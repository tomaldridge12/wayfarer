package dev.wayfarer.core.progression;

import dev.wayfarer.core.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** Player-facing progression text: the progress card and the earning moment's words. Adventure only. */
public final class ProgressionText {
    static final TextColor EARNED = TextColor.color(0xFFC94A);
    private static final TextColor CLIMBING = TextColor.color(0x55FF55);
    private static final TextColor CLAIM = TextColor.color(0x55FFFF);
    private static final int BAR_WIDTH = 25;

    private ProgressionText() {
    }

    /**
     * The /wayfarer progress card: a header, then each ladder with its count and its rewards as fixed-width bars
     * (chat is not monospaced, so bars go first to line up). Drop-only rewards show once earned. With
     * {@code claimButtons}, earned rewards get a clickable claim.
     */
    public static List<Component> progress(String who, List<Ladder> ladders, ProgressionData data,
            Function<String, Component> describe, boolean claimButtons) {
        List<Component> lines = new ArrayList<>();
        lines.add(plain("⚔ ", NamedTextColor.WHITE).append(TextStyle.gradient("Progress", 0xFFE7A0, 0xFF9F1C)
                .decorate(TextDecoration.BOLD)).append(plain(" · " + who, NamedTextColor.GRAY)));
        for (Ladder ladder : ladders) {
            long progress = ladder.progress(data);
            lines.add(plain(ladder.title(), NamedTextColor.WHITE).append(plain(" · ", NamedTextColor.DARK_GRAY))
                    .append(plain(count(progress, ladder.unit()), NamedTextColor.GRAY)));
            for (Ladder.Rung rung : ladder.rungs()) {
                if (rung.threshold() != Ladder.Rung.NEVER || data.isEarned(rung.reward())) {
                    lines.add(rungLine(rung, progress, data, describe.apply(rung.reward()), claimButtons));
                }
            }
        }
        if (ladders.isEmpty()) {
            lines.add(plain("No rewards are set up yet.", NamedTextColor.DARK_GRAY));
        }
        return lines;
    }

    private static Component rungLine(Ladder.Rung rung, long progress, ProgressionData data, Component name,
            boolean claimButtons) {
        boolean earned = data.isEarned(rung.reward());
        int filled = earned ? BAR_WIDTH
                : (int) Math.min(BAR_WIDTH, progress * BAR_WIDTH / Math.max(1, rung.threshold()));
        Component bar = plain("|".repeat(filled), earned ? EARNED : CLIMBING)
                .append(plain("|".repeat(BAR_WIDTH - filled), NamedTextColor.DARK_GRAY));
        Component status = earned ? plain(" ✔", EARNED)
                : plain(" " + Math.min(progress, rung.threshold()) + "/" + rung.threshold(), NamedTextColor.GRAY);
        Component line = plain("  ", NamedTextColor.GRAY).append(bar).append(status)
                .append(plain("  ", NamedTextColor.GRAY)).append(name);
        if (earned && claimButtons) {
            line = line.append(plain("  ", NamedTextColor.GRAY)).append(plain("[Claim copy]", CLAIM)
                    .hoverEvent(HoverEvent.showText(plain("Get another copy if you lost yours", NamedTextColor.GRAY)))
                    .clickEvent(ClickEvent.runCommand("/wayfarer claim " + rung.reward())));
        }
        return line;
    }

    /** "1 kill", "1,342 kills". */
    static String count(long n, String unit) {
        return String.format(Locale.ROOT, "%,d", n) + " " + unit + (n == 1 ? "" : "s");
    }

    public static Component announcement(String earnerName, Component rewardName) {
        return plain("✦ ", EARNED).append(plain(earnerName, NamedTextColor.WHITE))
                .append(plain(" earned the ", NamedTextColor.GRAY)).append(rewardName);
    }

    public static Component earnedSubtitle() {
        return TextStyle.gradient("✦ Reward earned ✦", 0xFFE7A0, 0xFF9F1C);
    }

    static Component plain(String text, TextColor colour) {
        return Component.text(text, colour).decoration(TextDecoration.ITALIC, false);
    }
}
