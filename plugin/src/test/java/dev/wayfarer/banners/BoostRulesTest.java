package dev.wayfarer.banners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.wayfarer.banners.BoostRules.Boost;
import dev.wayfarer.banners.BoostRules.Party;
import dev.wayfarer.banners.BoostRules.Point;
import dev.wayfarer.banners.BoostRules.Resolved;
import dev.wayfarer.banners.PlacedBannerRegistry.BlockPos;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BoostRulesTest {
    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID OTHER_WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    private static BannerDefinition banner(String id, String family, int bonus, int reduction, int radius) {
        return new BannerDefinition(id, id, "white", List.of(), family, 100, bonus, reduction, radius);
    }

    private static BannerSet set(BannerDefinition... defs) {
        Map<String, BannerDefinition> banners = new LinkedHashMap<>();
        for (BannerDefinition d : defs) {
            banners.put(d.id(), d);
        }
        return new BannerSet(Map.of(
                "zombie", new MobFamily("zombie", Set.of("zombie", "husk")),
                "skeleton", new MobFamily("skeleton", Set.of("skeleton"))),
                banners, CountingSettings.defaults(), List.of());
    }

    private final PlacedBannerRegistry registry = new PlacedBannerRegistry();

    private void place(int x, int z, String id) {
        registry.add(WORLD, new BlockPos(x, 64, z), id);
    }

    private Optional<Resolved> hit(Party direct, Party shooter, Party victim, BannerSet set) {
        return BoostRules.resolve(direct, shooter, victim, at -> registry.within(at.world(), at.x(), at.z(), set.maxRadius()), set);
    }

    private static Party player(double x, double z) {
        return new Party(true, "player", new Point(WORLD, x, 64, z));
    }

    private static Party mob(String type, double x, double z) {
        return new Party(false, type, new Point(WORLD, x, 64, z));
    }

    @Test
    void playerInsideZoneGetsBonusAgainstTheFamily() {
        BannerSet set = set(banner("z", "zombie", 10, 10, 16));
        place(0, 0, "z");
        assertEquals(Optional.of(new Resolved(new Boost("z", 10), true)),
                hit(player(5, 5), null, mob("husk", 40, 40), set));
    }

    @Test
    void playersPositionDecidesNotTheMobs() {
        BannerSet set = set(banner("z", "zombie", 10, 10, 16));
        place(0, 0, "z");
        assertTrue(hit(player(40, 40), null, mob("zombie", 1, 1), set).isEmpty());
        // Being hit: the victim player's position decides.
        assertEquals(Optional.of(new Resolved(new Boost("z", 10), false)),
                hit(mob("zombie", 40, 40), null, player(1, 1), set));
    }

    @Test
    void zoneEdgeIsInsideAndJustPastItIsOutside() {
        BannerSet set = set(banner("z", "zombie", 10, 10, 16));
        place(0, 0, "z");
        // Banner centre is (0.5, 64.5, 0.5); the player stands at y 64.5 to keep the distance horizontal.
        Party atEdge = new Party(true, "player", new Point(WORLD, 16.5, 64.5, 0.5));
        Party past = new Party(true, "player", new Point(WORLD, 16.51, 64.5, 0.5));
        assertTrue(hit(atEdge, null, mob("zombie", 0, 0), set).isPresent());
        assertTrue(hit(past, null, mob("zombie", 0, 0), set).isEmpty());
    }

    @Test
    void duplicateBannersDoNotStack() {
        BannerSet set = set(banner("z", "zombie", 10, 10, 16));
        place(0, 0, "z");
        place(2, 2, "z");
        place(-3, 1, "z");
        assertEquals(10, hit(player(0, 0), null, mob("zombie", 0, 0), set).orElseThrow().boost().percent());
        assertEquals(110.0, BoostRules.withBonus(100, new Boost("z", 10)), 1e-9);
    }

    @Test
    void overlappingZonesExtendCoverageWithoutAdding() {
        BannerSet set = set(banner("z", "zombie", 10, 10, 16));
        place(0, 0, "z");
        place(30, 0, "z");
        assertEquals(10, hit(player(40, 0), null, mob("zombie", 0, 0), set).orElseThrow().boost().percent());
        assertEquals(10, hit(player(15, 0), null, mob("zombie", 0, 0), set).orElseThrow().boost().percent());
    }

    @Test
    void strongestBonusAndStrongestReductionAreChosenSeparately() {
        BannerSet set = set(banner("big-bonus", "zombie", 30, 5, 16), banner("big-guard", "zombie", 5, 40, 16));
        place(0, 0, "big-bonus");
        place(1, 1, "big-guard");
        assertEquals(new Boost("big-bonus", 30), hit(player(0, 0), null, mob("zombie", 0, 0), set).orElseThrow().boost());
        assertEquals(new Boost("big-guard", 40), hit(mob("zombie", 0, 0), null, player(0, 0), set).orElseThrow().boost());
    }

    @Test
    void differentFamiliesNeverInteract() {
        BannerSet set = set(banner("s", "skeleton", 50, 50, 16));
        place(0, 0, "s");
        assertTrue(hit(player(0, 0), null, mob("zombie", 0, 0), set).isEmpty());
        assertTrue(hit(mob("zombie", 0, 0), null, player(0, 0), set).isEmpty());
        assertTrue(hit(player(0, 0), null, mob("cow", 0, 0), set).isEmpty());
    }

    @Test
    void projectileCountsAsItsShooter() {
        BannerSet set = set(banner("z", "zombie", 10, 10, 16), banner("s", "skeleton", 10, 25, 16));
        place(0, 0, "z");
        place(0, 1, "s");
        Party arrow = mob("arrow", 60, 60); // where the arrow is does not matter
        assertEquals(new Boost("z", 10), hit(arrow, player(1, 1), mob("zombie", 90, 90), set).orElseThrow().boost());
        // A skeleton's arrow hitting a player: the skeleton family's reduction applies.
        assertEquals(new Boost("s", 25), hit(arrow, mob("skeleton", 90, 90), player(1, 1), set).orElseThrow().boost());
    }

    @Test
    void petsDoNotEarnTheBonus() {
        BannerSet set = set(banner("z", "zombie", 10, 10, 16));
        place(0, 0, "z");
        assertTrue(hit(mob("wolf", 0, 0), null, mob("zombie", 0, 0), set).isEmpty());
    }

    @Test
    void reductionNeverGoesBelowZeroAndInertOrOtherWorldBannersDoNothing() {
        assertEquals(0.0, BoostRules.withReduction(8, new Boost("z", 100)), 1e-9);
        BannerSet set = set(banner("z", "zombie", 10, 10, 16));
        place(0, 0, "gone");
        registry.add(OTHER_WORLD, new BlockPos(0, 64, 0), "z");
        assertTrue(hit(player(0, 0), null, mob("zombie", 0, 0), set).isEmpty());
    }
}
