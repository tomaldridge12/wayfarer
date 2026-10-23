package dev.wayfarer.banners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.wayfarer.banners.PlacedBannerRegistry.BlockPos;
import dev.wayfarer.banners.ZoneTracker.Change;
import dev.wayfarer.banners.ZoneTracker.Transition;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ZoneTrackerTest {
    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final BannerDefinition ZOMBIE = new BannerDefinition("z", "Zombie Banner", "green", List.of(), "zombie", 100, 10, 10, 16);
    private static final BannerDefinition STRONG = new BannerDefinition("z2", "Big Zombie", "green", List.of(), "zombie", 100, 20, 10, 16);
    private static final BannerDefinition SKELETON = new BannerDefinition("s", "Skeleton Banner", "white", List.of(), "skeleton", 100, 10, 10, 16);

    private final PlacedBannerRegistry registry = new PlacedBannerRegistry();
    private final ZoneTracker tracker = new ZoneTracker();
    private final BannerSet set;

    ZoneTrackerTest() {
        Map<String, BannerDefinition> banners = new LinkedHashMap<>();
        for (BannerDefinition d : List.of(ZOMBIE, STRONG, SKELETON)) {
            banners.put(d.id(), d);
        }
        set = new BannerSet(Map.of("zombie", new MobFamily("zombie", Set.of("zombie")),
                "skeleton", new MobFamily("skeleton", Set.of("skeleton"))), banners, CountingSettings.defaults(), List.of());
    }

    private List<Transition> standAt(double x, double z) {
        BoostRules.Point at = new BoostRules.Point(WORLD, x, 64.5, z);
        return tracker.update(PLAYER, ZoneTracker.zonesAt(at, registry.within(WORLD, x, z, set.maxRadius()), set));
    }

    @Test
    void enteringAndLeavingAZoneEachReportOnce() {
        registry.add(WORLD, new BlockPos(0, 64, 0), "z");
        assertEquals(List.of(), standAt(50, 50));
        assertEquals(List.of(new Transition(Change.ENTER, ZOMBIE)), standAt(5, 5));
        assertEquals(List.of(), standAt(6, 6)); // still inside: no repeat
        assertTrue(tracker.inAnyZone(PLAYER));
        assertEquals(List.of(new Transition(Change.LEAVE, ZOMBIE)), standAt(50, 50));
        assertFalse(tracker.inAnyZone(PLAYER));
    }

    @Test
    void movingBetweenOverlappingSameBannersIsNotAChange() {
        registry.add(WORLD, new BlockPos(0, 64, 0), "z");
        registry.add(WORLD, new BlockPos(20, 64, 0), "z");
        standAt(1, 0);
        assertEquals(List.of(), standAt(19, 0));
        assertEquals(List.of(), standAt(30, 0));
    }

    @Test
    void steppingIntoAStrongerBannerOfTheSameFamilyAnnouncesIt() {
        registry.add(WORLD, new BlockPos(0, 64, 0), "z");
        registry.add(WORLD, new BlockPos(25, 64, 0), "z2");
        standAt(0, 0);
        assertEquals(List.of(new Transition(Change.ENTER, STRONG)), standAt(12, 0));
    }

    @Test
    void familiesAreTrackedSeparately() {
        registry.add(WORLD, new BlockPos(0, 64, 0), "z");
        registry.add(WORLD, new BlockPos(20, 64, 0), "s");
        standAt(0, 0);
        assertEquals(List.of(new Transition(Change.ENTER, SKELETON)), standAt(10, 0));
        assertEquals(List.of(new Transition(Change.LEAVE, ZOMBIE)), standAt(30, 0));
    }
}
