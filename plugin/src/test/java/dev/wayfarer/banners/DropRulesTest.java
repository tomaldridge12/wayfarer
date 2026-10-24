package dev.wayfarer.banners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.wayfarer.banners.DropRules.Hitter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import org.junit.jupiter.api.Test;

class DropRulesTest {
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID MOB = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
    private static final BannerDefinition WITHER = new BannerDefinition("wither", "Wither Banner", "black", List.of(), "skeleton", 100, 10, 10, 16);
    private static final BannerDefinition BONUS = new BannerDefinition("bonus", "Bonus", "white", List.of(), "skeleton", 100, 10, 10, 16);

    private static BannerSet set() {
        Map<String, BannerDefinition> banners = new LinkedHashMap<>();
        banners.put("wither", WITHER);
        banners.put("bonus", BONUS);
        return new BannerSet(Map.of(), banners, CountingSettings.defaults(),
                Map.of("wither", List.of(new BannerSet.Drop("wither", 0.5), new BannerSet.Drop("bonus", 0.1))), List.of());
    }

    private static DoubleSupplier fixed(double... values) {
        Deque<Double> queue = new ArrayDeque<>();
        for (double v : values) {
            queue.add(v);
        }
        return queue::removeFirst;
    }

    @Test
    void eachDropRollsOnItsOwnAgainstItsChance() {
        assertEquals(List.of(WITHER), DropRules.roll("wither", set(), fixed(0.49, 0.1)));
        assertEquals(List.of(WITHER, BONUS), DropRules.roll("wither", set(), fixed(0.0, 0.0999)));
        assertEquals(List.of(), DropRules.roll("wither", set(), fixed(0.5, 0.5)));
        assertEquals(List.of(), DropRules.roll("zombie", set(), fixed()));
    }

    @Test
    void playersAndTheirProjectilesAreCreditedButNotPetsOrMobs() {
        Hitter alice = new Hitter(ALICE, true);
        Hitter arrow = new Hitter(UUID.randomUUID(), false);
        Hitter wolf = new Hitter(UUID.randomUUID(), false);
        Hitter skeleton = new Hitter(UUID.randomUUID(), false);
        assertEquals(Optional.of(ALICE), DropRules.credited(alice, null));
        assertEquals(Optional.of(ALICE), DropRules.credited(arrow, alice));
        assertEquals(Optional.empty(), DropRules.credited(wolf, null));
        assertEquals(Optional.empty(), DropRules.credited(arrow, skeleton));
    }

    @Test
    void everyDamagerIsListedOnceAndTheMobIsForgottenAfterDeath() {
        DropRules drops = new DropRules();
        drops.recordHit(MOB, ALICE, 0);
        drops.recordHit(MOB, BOB, 5);
        drops.recordHit(MOB, ALICE, 9);
        assertEquals(List.of(ALICE, BOB), drops.takeDamagers(MOB));
        assertEquals(List.of(), drops.takeDamagers(MOB));
    }

    @Test
    void mobsNotHitForTenMinutesAreForgotten() {
        DropRules drops = new DropRules();
        drops.recordHit(MOB, ALICE, 0);
        drops.forgetStale(20L * 60 * 10);
        assertEquals(1, drops.tracked());
        drops.forgetStale(20L * 60 * 10 + 1);
        assertEquals(0, drops.tracked());
    }

    @Test
    void parserReadsDropsAndSkipsBadOnes() {
        Vocabulary vocab = new Vocabulary() {
            public boolean isMob(String n) { return List.of("wither", "skeleton").contains(n); }
            public boolean isPattern(String n) { return true; }
            public boolean isColour(String n) { return true; }
            public boolean isParticle(String n) { return true; }
            public boolean isEffect(String n) { return true; }
        };
        Map<String, Object> drops = new LinkedHashMap<>();
        drops.put("wither", 0.25);
        drops.put("ghast_of_nowhere", 0.5);
        drops.put("skeleton", 2);
        BannerSet set = BannerConfigParser.parse(Map.of(
                "families", Map.of("skeleton", List.of("skeleton")),
                "banners", Map.of("s", Map.of("family", "skeleton", "drops", drops))), vocab);
        assertEquals(List.of(new BannerSet.Drop("s", 0.25)), set.dropsFor("WITHER"));
        assertEquals(List.of(), set.dropsFor("skeleton"));
        assertEquals(2, set.problems().size());
        assertTrue(set.banners().containsKey("s"));
    }
}
