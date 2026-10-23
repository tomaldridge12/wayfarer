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

    @Test
    void progressCardShowsBarsCountsAndEarnedRewards() {
        BannerDefinition skeleton = new BannerDefinition("skeleton", "Skeleton Banner", "white", List.of(), "skeleton", 100, 10, 10, 16);
        java.util.Map<String, BannerDefinition> banners = new java.util.LinkedHashMap<>();
        banners.put("zombie", ZOMBIE);
        banners.put("skeleton", skeleton);
        BannerSet set = new BannerSet(java.util.Map.of(
                "zombie", new MobFamily("zombie", Set.of("zombie")),
                "skeleton", new MobFamily("skeleton", Set.of("skeleton"))),
                banners, CountingSettings.defaults(), List.of());
        PlayerData data = new PlayerData();
        data.setKills(MobFamily.ALL, 1342);
        data.setKills("zombie", 52);
        data.setKills("skeleton", 130);
        data.markEarned(RewardKey.banner("skeleton"));

        List<String> lines = BannerText.progress("Tom", data, set, true).stream().map(BannerTextTest::plain).toList();
        assertEquals(List.of(
                "⚔ Kill progress · Tom",
                "All mobs · 1,342 kills",
                "Zombie · 52 kills",
                "  " + "|".repeat(25) + " 52/100  Zombie Banner",
                "Skeleton · 130 kills",
                "  " + "|".repeat(25) + " ✔  Skeleton Banner  [Claim copy]"), lines);
        assertEquals(BannerText.EARNED, colourOf(BannerText.progress("Tom", data, set, true).getLast(), "|".repeat(25)));
        // Viewing someone else's card shows no claim button.
        assertTrue(plain(BannerText.progress("Tom", data, set, false).getLast()).endsWith("Skeleton Banner"));
        assertEquals("1 kill", BannerText.count(1));
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

    @Test
    void plaqueShowsEarnerAndLiveCount() {
        HeadDefinition head = new HeadDefinition("zombie", "Zombie Trophy", "zombie", 250);
        assertEquals("☠ Tom's Zombie Trophy\n1,342 zombies slain", plain(BannerText.plaque(head, "green", "Tom", 1342)));
        assertEquals("☠ Tom's Zombie Trophy\n… zombies slain", plain(BannerText.plaque(head, "green", "Tom", null)));
        assertEquals("endermen", BannerText.plural("enderman"));
        assertEquals("cave spiders", BannerText.plural("cave_spider"));
    }

    @Test
    void headLoreSaysWhoAndWhen() {
        HeadDefinition head = new HeadDefinition("zombie", "Zombie Trophy", "zombie", 250);
        assertEquals(List.of("☠ Trophy head · Zombie family", "", "Place it to display Tom's live kill count.", "",
                        "Earned 23 Oct 2026 at 250 zombie kills"),
                BannerText.headLore(head, "Tom", "23 Oct 2026").stream().map(BannerTextTest::plain).toList());
        assertEquals("Earned at 250 zombie kills", BannerText.earned(null, 250, "zombie"));
    }

    @Test
    void relicLoreShowsRarityEdgeAndEarned() {
        RelicDefinition cleaver = new RelicDefinition("zombie_weapon", "Zombie Cleaver", "zombie", 400, 1,
                RelicDefinition.Slot.WEAPON);
        assertEquals(List.of("✦ Rare Relic · Zombie family", "", " ⚔ +30% damage to Husk and Zombie", "",
                        "Earned 23 Oct 2026 at 400 zombie kills"),
                BannerText.relicLore(cleaver, Set.of("zombie", "husk"), "23 Oct 2026").stream().map(BannerTextTest::plain).toList());
        RelicDefinition chest = new RelicDefinition("zombie_armour_2", "Rotguard II", "zombie", 1800, 2,
                RelicDefinition.Slot.CHEST);
        List<String> lore = BannerText.relicLore(chest, Set.of("zombie"), null).stream().map(BannerTextTest::plain).toList();
        assertEquals("✦ Epic Relic · Zombie family", lore.getFirst());
        assertEquals(" ✚ −30% damage taken from Zombie", lore.get(2));
        RelicDefinition blade = new RelicDefinition("wayfarers_blade", "Wayfarer's Blade", MobFamily.ALL, 5000, 2,
                RelicDefinition.Slot.WEAPON);
        assertEquals(List.of("✦ Epic Relic · All mobs", "", " ⚔ +10% damage to every hostile mob", "",
                "Earned at 5,000 mob kills"), BannerText.relicLore(blade, Set.of(), null).stream().map(BannerTextTest::plain).toList());
    }

    @Test
    void relicLoreListsPerks() {
        RelicDefinition boots = new RelicDefinition("spider_armour", "Silkstep Boots", "spider", 600, 1,
                RelicDefinition.Slot.FEET, "iron_boots", java.util.Map.of(), null, null, 25,
                List.of(new Perk(Perk.Type.COBWEB_WALKER, null, 1), new Perk(Perk.Type.FALL_CUSHION, null, 5)));
        List<String> lore = BannerText.relicLore(boots, Set.of("spider"), null).stream().map(BannerTextTest::plain).toList();
        assertEquals(" ✦ Cobwebs dissolve as you walk into them", lore.get(3));
        assertEquals(" ✦ No fall damage from falls of 5 blocks or less", lore.get(4));
    }
}
