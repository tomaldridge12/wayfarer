package dev.wayfarer.objectives;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class CosmeticTest {
    @Test
    void hatsAndPetsParseAndBadOnesAreSkippedWithAProblem() {
        ObjectiveConfigParser.Result r = ObjectiveConfigParser.parse(new Yaml().load("""
                cosmetics:
                  top: {kind: hat, name: "Top", item: wayfarer:hat/top}
                  pumpkin: {kind: hat, name: "Pumpkin", item: carved_pumpkin}
                  bare: {kind: hat, name: "Bare"}
                  sheep: {kind: pet, name: "Rainbow Sheep", entity: sheep, pet-name: jeb_}
                  shulker: {kind: pet, name: "Mini Shulker", model: wayfarer:pet/shulker, scale: 0.5}
                  both: {kind: pet, entity: sheep, model: wayfarer:pet/x}
                  neither: {kind: pet}
                  huge: {kind: pet, model: wayfarer:pet/x, scale: 9}
                  odd: {kind: cape}
                """), ObjectiveFixtures.SERVER);

        assertEquals(List.of("top", "pumpkin", "sheep", "shulker", "huge"), List.copyOf(r.cosmetics().keySet()));
        assertTrue(r.cosmetics().get("top").hat().model());
        assertFalse(r.cosmetics().get("pumpkin").hat().model());
        assertTrue(r.cosmetics().get("pumpkin").hat().plain());
        assertEquals("jeb_", r.cosmetics().get("sheep").pet().customName());
        assertTrue(r.cosmetics().get("sheep").pet().baby());
        assertEquals(0.5, r.cosmetics().get("shulker").pet().scale());
        assertEquals(1.0, r.cosmetics().get("huge").pet().scale());
        assertEquals(5, r.problems().size(), r.problems().toString());
    }

    @Test
    void eachKindHasItsOwnSlotThatWearsAndTakesOffAndIsSaved() {
        ObjectivesPlayerData data = new ObjectivesPlayerData();
        Cosmetic title = new Cosmetic("t", Cosmetic.TITLE, "T", 0, null);
        Cosmetic hat = new Cosmetic("h", Cosmetic.HAT, "H", 0, null, new Cosmetic.Hat("lantern", null), null);
        Cosmetic hat2 = new Cosmetic("h2", Cosmetic.HAT, "H2", 0, null, new Cosmetic.Hat("cake", null), null);
        Cosmetic pet = new Cosmetic("p", Cosmetic.PET, "P", 0, null, null, new Cosmetic.Pet("sheep", null, true, null, 1));

        assertTrue(data.toggleWorn(title));
        assertTrue(data.toggleWorn(hat));
        assertTrue(data.toggleWorn(pet));
        assertTrue(data.toggleWorn(hat2), "a second hat replaces the first");
        assertEquals("h2", data.worn(Cosmetic.HAT));
        assertEquals("t", data.worn(Cosmetic.TITLE));
        assertNull(data.worn(Cosmetic.TRAIL));

        ObjectivesPlayerData.Codec codec = new ObjectivesPlayerData.Codec();
        ObjectivesPlayerData back = codec.decode(ObjectivesPlayerData.VERSION, codec.encode(data));
        assertEquals("h2", back.hat);
        assertEquals("p", back.pet);

        assertFalse(back.toggleWorn(pet), "wearing the same one again takes it off");
        assertNull(back.pet);
    }

    @Test
    void noHatWithAnAbilityIsInTheShopAndListingOneIsRefused() {
        ObjectiveConfigParser.Result shipped = ObjectiveFixtures.shipped();
        List<String> abilityHats = shipped.cosmetics().values().stream()
                .filter(c -> c.hat() != null && !c.hat().plain()).map(Cosmetic::id).toList();
        assertFalse(abilityHats.isEmpty());

        ShopConfigParser.Result shop = ShopConfigParser.parse(ObjectiveFixtures.shippedYaml().get("shop"), shipped.cosmetics(), java.util.Set.of());
        assertTrue(abilityHats.stream().noneMatch(shop.prices().cosmetics()::containsKey));

        ShopConfigParser.Result bad = ShopConfigParser.parse(java.util.Map.of("cosmetics", java.util.Map.of(abilityHats.get(0), 100)),
                shipped.cosmetics(), java.util.Set.of());
        assertTrue(bad.prices().cosmetics().isEmpty());
        assertEquals(1, bad.problems().size());
    }

    @Test
    void theShopSellsPlainHatsAndPetsInTheirOwnSectionsAndADuplicateRollBecomesTokens() {
        ObjectiveConfigParser.Result shipped = ObjectiveFixtures.shipped();
        ShopConfigParser.Result shop = ShopConfigParser.parse(ObjectiveFixtures.shippedYaml().get("shop"), shipped.cosmetics(), java.util.Set.of());
        List<ShopRules.Offer> offers = ShopRules.offers(shop.prices(), new ShopRules.Buyer(1000, java.util.Set.of("piglet"), false,
                java.util.Set.of(), java.util.Map.of(), false), shipped.cosmetics(), java.util.Map.of());

        assertEquals("Hats", offers.stream().filter(o -> o.id().equals("cosmetic:cake_hat")).findFirst().orElseThrow().section());
        ShopRules.Offer piglet = offers.stream().filter(o -> o.id().equals("cosmetic:piglet")).findFirst().orElseThrow();
        assertEquals("Pets", piglet.section());
        assertEquals("You have it", piglet.blocked());
        assertTrue(offers.stream().noneMatch(o -> o.id().equals("cosmetic:rainbow_sheep")), "earned by a feat, never sold");

        RewardTables tables = new RewardTables(java.util.Map.of("t", java.util.Map.of("daily",
                List.of(new RewardTables.Entry("cosmetic:pumpkin_hat", 1, 1, 1)))), 25);
        assertEquals(List.of("cosmetic:pumpkin_hat"), tables.roll("t", "daily", reward -> false, new java.util.Random(1)));
        assertEquals(List.of("tokens:25"), tables.roll("t", "daily", reward -> true, new java.util.Random(1)));
    }
}
