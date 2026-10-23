package dev.wayfarer.banners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class BannerTextTest {
    private static final BannerDefinition ZOMBIE =
            new BannerDefinition("zombie", "Zombie Banner", "green", List.of(), "zombie", 100, 10, 10, 16);

    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    @Test
    void loreDescribesTheBannerWithoutPatternNames() {
        List<String> lines = BannerText.lore(ZOMBIE, Set.of("zombie", "husk", "drowned")).stream()
                .map(BannerTextTest::plain).toList();
        assertEquals(List.of(
                "Trophy banner · Zombie family",
                "",
                "Place it to guard everyone within 16 blocks:",
                " ⚔ +10% damage to Drowned, Husk and Zombie",
                " ✚ −10% damage taken from them",
                "",
                "Earned by slaying 100 of them."), lines);
    }

    @Test
    void nameIsABoldGradientInTheBannersColour() {
        Component name = BannerText.name(ZOMBIE);
        assertEquals("Zombie Banner", plain(name));
        assertEquals(TextDecoration.State.TRUE, name.decoration(TextDecoration.BOLD));
        TextColor first = name.children().getFirst().color();
        TextColor last = name.children().getLast().color();
        assertNotEquals(first, last);
        assertTrue(first.green() > first.red(), "green banner should read green");
    }

    @Test
    void darkBannersStayReadable() {
        BannerDefinition black = new BannerDefinition("b", "Shadow", "black", List.of(), "zombie", 100, 10, 10, 16);
        TextColor last = BannerText.name(black).children().getLast().color();
        assertTrue(BannerText.luminance(last.value()) > 0.45, "end of gradient too dark: " + last.asHexString());
    }

    @Test
    void actionBarLinesAreShort() {
        assertEquals("⚑ Zombie Banner  │  ⚔ +10% damage  ✚ −10% taken", plain(BannerText.entered(ZOMBIE)));
        assertEquals("Left Zombie Banner", plain(BannerText.left(ZOMBIE)));
        assertEquals("✚ Zombie Banner blocked 0.3", plain(BannerText.blocked(ZOMBIE, 0.3)));
    }

    @Test
    void popupShowsBaseThenTheBoostedExtraInRed() {
        Component normal = BannerText.popup(new DamageNumbers.Split(4.92, 0), false);
        assertEquals("4.9", plain(normal));
        Component boosted = BannerText.popup(new DamageNumbers.Split(4.92, 0.49), false);
        assertEquals("▲ 4.9 +0.5", plain(boosted));
        assertEquals(BannerText.EXTRA, boosted.children().getLast().color());
        assertEquals(BannerText.CRIT, BannerText.popup(new DamageNumbers.Split(7, 0), true).color());
    }

    @Test
    void labelShowsNameAndEffectsOrInactive() {
        assertEquals("Zombie Banner\n⚔ +10% damage  ✚ −10% taken", plain(BannerText.label("zombie", Optional.of(ZOMBIE))));
        assertEquals("gone\nInactive: not in config", plain(BannerText.label("gone", Optional.empty())));
    }

    @Test
    void mobNamesAreTitledAndListed() {
        assertEquals("Cave Spider and Spider", BannerText.mobList(Set.of("spider", "cave_spider")));
        assertEquals("Enderman", BannerText.mobList(Set.of("enderman")));
    }
}
