package dev.wayfarer.objectives;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** The shop: what a player may buy, why not, and that power can never be put on sale. */
class ShopTest {
    private static final ObjectiveConfigParser.Result CONFIG = ObjectiveFixtures.shipped();
    private static final ShopConfigParser.Result SHIPPED = ShopConfigParser.parse(ObjectiveFixtures.shippedYaml().get("shop"),
            CONFIG.cosmetics(), Set.of("seed_bundle", "floral_bundle", "mushy_bundle", "animal_crate", "wayfarers_bait", "tome_of_experience"));

    private static ShopRules.Buyer buyer(long tokens, Set<String> cosmetics, boolean rerolled, Map<String, Integer> enchants, boolean exploration) {
        return new ShopRules.Buyer(tokens, cosmetics, rerolled, Set.of("alloy:template:steel"), enchants, exploration);
    }

    private static Optional<ShopRules.Offer> offer(List<ShopRules.Offer> offers, String id) {
        return offers.stream().filter(o -> o.id().equals(id)).findFirst();
    }

    @Test
    void theShippedShopLoadsAndSellsNoFeatCosmetics() {
        assertEquals(List.of(), SHIPPED.problems());
        assertFalse(SHIPPED.prices().cosmetics().containsKey("mossborn"), "feat titles are earned, not bought");
        assertEquals(6, SHIPPED.prices().supplies().size());
    }

    @Test
    void ownedCosmeticsAndTodaysRerollCannotBeBoughtTwice() {
        List<ShopRules.Offer> offers = ShopRules.offers(SHIPPED.prices(), buyer(1000, Set.of("petals"), true, Map.of(), true),
                CONFIG.cosmetics(), Map.of());
        assertEquals("You have it", offer(offers, "cosmetic:petals").orElseThrow().blocked());
        assertTrue(offer(offers, "cosmetic:embers").orElseThrow().buyable());
        assertEquals("Once a day", offer(offers, "reroll").orElseThrow().blocked());
        assertTrue(offer(offers, "template:alloy:template:steel").orElseThrow().buyable());
    }

    @Test
    void onlyUnlockedEnchantsUpToTheirLevelAreSold() {
        List<ShopRules.Offer> offers = ShopRules.offers(SHIPPED.prices(), buyer(1000, Set.of(), false,
                Map.of("excellentenchants:haste", 2), true), CONFIG.cosmetics(), Map.of());
        assertEquals(60, offer(offers, "enchant:excellentenchants:haste_1").orElseThrow().price());
        assertEquals(120, offer(offers, "enchant:excellentenchants:haste_2").orElseThrow().price());
        assertTrue(offer(offers, "enchant:excellentenchants:haste_3").isEmpty());
        assertTrue(offers.stream().noneMatch(o -> o.id().startsWith("enchant:excellentenchants:smelter")));
        assertEquals("Haste II Book", offer(offers, "enchant:excellentenchants:haste_2").orElseThrow().label());
    }

    @Test
    void explorationGoodsNeedTheModuleAndPricesNeedTokens() {
        List<ShopRules.Offer> poor = ShopRules.offers(SHIPPED.prices(), buyer(10, Set.of(), false, Map.of(), false), CONFIG.cosmetics(), Map.of());
        assertTrue(offer(poor, "wayfinder").isEmpty());
        assertTrue(offer(poor, "kindling").isEmpty());
        assertEquals("Needs 20 more tokens", offer(poor, "supply:seed_bundle").orElseThrow().blocked());
    }

    @Test
    void powerCannotBePutOnSale() {
        ShopConfigParser.Result r = ShopConfigParser.parse(new Yaml().load("""
                relics: {zombie_weapon: 10}
                curios: {moss_heart: 10}
                supplies: {"curio:moss_heart": 10, seed_bundle: 5, ingot: 3}
                cosmetics: {nope: 5}
                """), CONFIG.cosmetics(), Set.of("seed_bundle"));
        assertEquals(Map.of("seed_bundle", 5L), r.prices().supplies());
        assertTrue(r.prices().cosmetics().isEmpty());
        assertEquals(5, r.problems().size(), r.problems().toString());
    }
}
