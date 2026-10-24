package dev.wayfarer.core.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class ProgressionTextTest {
    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    private static final List<Ladder> LADDERS = List.of(
            new Ladder("all", "All mobs", "kill", Set.of("kill:zombie", "kill:skeleton"), List.of(), true),
            new Ladder("zombie", "Zombie", "kill", Set.of("kill:zombie"),
                    List.of(new Ladder.Rung(100, "banner:zombie"), new Ladder.Rung(Ladder.Rung.NEVER, "relic:drop")), true),
            new Ladder("skeleton", "Skeleton", "kill", Set.of("kill:skeleton"),
                    List.of(new Ladder.Rung(100, "banner:skeleton")), true));

    private static Component name(String reward) {
        return Component.text(reward.equals("banner:zombie") ? "Zombie Banner" : "Skeleton Banner");
    }

    @Test
    void progressCardShowsBarsCountsAndEarnedRewards() {
        ProgressionData data = new ProgressionData();
        data.set("kill:zombie", 52);
        data.set("kill:skeleton", 1290);
        data.markEarned("banner:skeleton", 1L);

        List<Component> card = ProgressionText.progress("Tom", LADDERS, data, ProgressionTextTest::name, true);
        assertEquals(List.of(
                "⚔ Progress · Tom",
                "All mobs · 1,342 kills",
                "Zombie · 52 kills",
                "  " + "|".repeat(25) + " 52/100  Zombie Banner",
                "Skeleton · 1,290 kills",
                "  " + "|".repeat(25) + " ✔  Skeleton Banner  [Claim copy]"), card.stream().map(ProgressionTextTest::plain).toList());
        assertEquals(ProgressionText.EARNED, colourOf(card.getLast(), "|".repeat(25)));
        // Viewing someone else's card shows no claim button.
        assertTrue(plain(ProgressionText.progress("Tom", LADDERS, data, ProgressionTextTest::name, false).getLast())
                .endsWith("Skeleton Banner"));
        assertEquals("1 kill", ProgressionText.count(1, "kill"));
        assertEquals("2 catches", ProgressionText.count(2, "catch|catches"));
        assertEquals("1 sheep", ProgressionText.count(1, "sheep|sheep"));
    }

    @Test
    void theMomentsWordsNameTheEarnerAndTheReward() {
        assertEquals("✦ Tom earned the Zombie Cleaver II",
                plain(ProgressionText.announcement("Tom", Component.text("Zombie Cleaver II"))));
        assertEquals("✦ Reward earned ✦", plain(ProgressionText.earnedSubtitle()));
    }

    /** The colour of the first text piece exactly equal to {@code content}, searching the whole component tree. */
    private static TextColor colourOf(Component c, String content) {
        if (c instanceof net.kyori.adventure.text.TextComponent t && t.content().equals(content)) {
            return t.color();
        }
        for (Component child : c.children()) {
            TextColor found = colourOf(child, content);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
