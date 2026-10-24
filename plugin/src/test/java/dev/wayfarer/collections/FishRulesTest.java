package dev.wayfarer.collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** The Almanac: the shipped species, eligibility by place and time, rarity weights and bait, weights and records. */
class FishRulesTest {
    private static final FishConfigParser.Result SHIPPED = FishConfigParser.parse(CollectionConfigParser.parse(CollectionsTest.shipped())
            .collections().get("almanac").settings());
    private static final Map<String, Double> WEIGHTS = SHIPPED.settings().rarityWeights();

    private static FishSpecies species(String id) {
        return SHIPPED.species().stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
    }

    private static FishRules.Facts facts(String water, String temp, boolean day, boolean rain, boolean thunder, String... places) {
        return new FishRules.Facts(water, temp, day, rain, thunder, Set.of(places));
    }

    @Test
    void theShippedAlmanacHasAllFortyFourSpeciesWithAStoryEach() {
        assertEquals(List.of(), SHIPPED.problems());
        assertEquals(44, SHIPPED.species().size());
        assertEquals(0.5, SHIPPED.settings().chance());
        SHIPPED.species().forEach(s -> assertFalse(s.flavour().isBlank(), s.id()));
        assertEquals(3, SHIPPED.species().stream().filter(s -> s.rarity().equals("legendary")).count());
    }

    @Test
    void eligibilityFollowsWaterClimateTimeWeatherAndPlace() {
        assertTrue(FishRules.eligible(species("northern_pike"), facts("fresh", "cold", true, false, false)));
        assertFalse(FishRules.eligible(species("northern_pike"), facts("salt", "cold", true, false, false)));
        assertFalse(FishRules.eligible(species("northern_pike"), facts("fresh", "hot", true, false, false)));
        assertFalse(FishRules.eligible(species("lamprey"), facts("fresh", "cold", true, false, false)), "night only");
        assertTrue(FishRules.eligible(species("lamprey"), facts("fresh", "cold", false, false, false)));
        assertFalse(FishRules.eligible(species("flying_fish"), facts("salt", "hot", false, false, false)), "day only");
        assertTrue(FishRules.eligible(species("pride_fish"), facts("salt", "cold", true, true, false)));
        assertTrue(FishRules.eligible(species("pride_fish"), facts("fresh", "warm", false, false, true)), "thunder is rain too");
        assertFalse(FishRules.eligible(species("pride_fish"), facts("fresh", "warm", false, false, false)));
        assertFalse(FishRules.eligible(species("arapaima"), facts("fresh", "warm", true, false, false)), "jungle only");
        assertTrue(FishRules.eligible(species("arapaima"), facts("fresh", "warm", true, false, false, "jungle")));
        assertTrue(FishRules.eligible(species("echo_fish"), facts("fresh", "cool", true, false, false, "deep_dark")));
        FishSpecies oarfish = species("oarfish");
        assertFalse(FishRules.eligible(oarfish, facts("salt", "temperate", false, true, false, "deep_ocean")));
        assertTrue(FishRules.eligible(oarfish, facts("salt", "temperate", false, true, true, "deep_ocean")));
    }

    @Test
    void picksAreWeightedByRarityAndBaitDoublesRareOdds() {
        FishSpecies common = new FishSpecies("a", "A", "common", "any", Set.of(), "any", "any", "", 1, 2, "", null);
        FishSpecies rare = new FishSpecies("b", "B", "rare", "any", Set.of(), "any", "any", "", 1, 2, "", null);
        Map<String, Double> weights = Map.of("common", 10.0, "rare", 10.0);
        assertEquals(20.0, FishRules.weight(rare, weights, 2));
        assertEquals(10.0, FishRules.weight(common, weights, 2));
        Random random = new Random(3);
        Map<String, Integer> plain = new HashMap<>();
        Map<String, Integer> baited = new HashMap<>();
        FishRules.Facts any = facts("fresh", "cool", true, false, false);
        for (int i = 0; i < 4000; i++) {
            plain.merge(FishRules.pick(List.of(common, rare), any, weights, 1, random).id(), 1, Integer::sum);
            baited.merge(FishRules.pick(List.of(common, rare), any, weights, 2, random).id(), 1, Integer::sum);
        }
        assertTrue(Math.abs(plain.get("b") - 2000) < 200, plain.toString());
        assertTrue(Math.abs(baited.get("b") - 2667) < 200, baited.toString());
        assertNull(FishRules.pick(List.of(species("arapaima")), any, WEIGHTS, 1, random), "nothing can bite: the catch stays vanilla");
    }

    @Test
    void weightsStayInRangeAndRecordsOnlyRise() {
        FishSpecies carp = species("carp");
        assertEquals(carp.minGrams(), FishRules.grams(carp, 0));
        assertEquals(carp.maxGrams(), FishRules.grams(carp, 1));
        assertTrue(FishRules.grams(carp, 0.5) < (carp.minGrams() + carp.maxGrams()) / 2, "heavy fish are rarer");
        assertTrue(FishRules.isRecord(500, 0));
        assertFalse(FishRules.isRecord(500, 500));
        assertEquals("850 g", FishRules.weight(850));
        assertEquals("2.41 kg", FishRules.weight(2410));
        assertEquals("112 kg", FishRules.weight(112_400));
    }

    @Test
    void hintsSayWhereAndWhen() {
        assertEquals("Cold or cool fresh water, at night", FishRules.where(species("lamprey")));
        assertEquals("Far out over the deep ocean, at night, in a thunderstorm", FishRules.where(species("oarfish")));
        assertEquals("Any water, in the rain", FishRules.where(species("pride_fish")));
        assertEquals("Jungle water", FishRules.where(species("piranha")));
    }

    @Test
    void badSpeciesAreSkipped() {
        FishConfigParser.Result r = FishConfigParser.parse(new Yaml().load("""
                chance: 2
                species:
                  good: {water: fresh, weight: 10-20}
                  bad_rarity: {rarity: mythic, weight: 10-20}
                  bad_water: {water: lava, weight: 10-20}
                  bad_temp: {temp: [toasty], weight: 10-20}
                  bad_place: {place: moon, weight: 10-20}
                  bad_weight: {weight: heavy}
                  falling_weight: {weight: 20-10}
                """));
        assertEquals(List.of("good"), r.species().stream().map(FishSpecies::id).toList());
        assertEquals(7, r.problems().size(), r.problems().toString());
        assertEquals(0.5, r.settings().chance());
    }

    @Test
    void climateBands() {
        assertEquals("cold", Almanac.climate(0.0));
        assertEquals("cool", Almanac.climate(0.25));
        assertEquals("temperate", Almanac.climate(0.8));
        assertEquals("warm", Almanac.climate(0.95));
        assertEquals("hot", Almanac.climate(2.0));
    }
}
