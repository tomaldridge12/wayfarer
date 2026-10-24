package dev.wayfarer.core.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Mining, harvesting, fishing, shearing and breeding as counted activities. */
class EverydayActivityTest {
    private static final ProgressionEngine ENGINE = new ProgressionEngine(ProgressionSettings.defaults());

    @Test
    void everyBlockBrokenCountsByType() {
        ProgressionData data = new ProgressionData();
        assertEquals(List.of("mine:sand"), ENGINE.mine(data, "sand", false).raised());
        ENGINE.mine(data, "sand", false);
        ENGINE.mine(data, "terralith:moss", false);
        assertEquals(Map.of("mine:sand", 2L, "mine:terralith:moss", 1L), data.countersView());
    }

    @Test
    void aFullyGrownCropIsAlsoAHarvest() {
        ProgressionData data = new ProgressionData();
        assertEquals(List.of("mine:wheat", "harvest:wheat"), ENGINE.mine(data, "wheat", true).raised());
        assertEquals(1, data.count("harvest:wheat"));
        assertEquals(1, data.count("mine:wheat"));
    }

    @Test
    void anUnripeCropIsOnlyMined() {
        ProgressionData data = new ProgressionData();
        assertEquals(List.of("mine:carrots"), ENGINE.mine(data, "carrots", false).raised());
        assertEquals(0, data.count("harvest:carrots"));
    }

    @Test
    void aGrownBlockThatIsNotACropIsNotAHarvest() {
        ProgressionData data = new ProgressionData();
        assertEquals(List.of("mine:kelp"), ENGINE.mine(data, "kelp", true).raised());
        assertEquals(List.of("mine:melon_stem"), ENGINE.mine(data, "melon_stem", true).raised());
    }

    @Test
    void catchesShearingAndBreedingCountByTarget() {
        ProgressionData data = new ProgressionData();
        ENGINE.record(data, Activity.CATCH + ":" + Activity.target("minecraft", "salmon"));
        ENGINE.record(data, Activity.CATCH + ":salmon");
        ENGINE.record(data, Activity.SHEAR + ":sheep");
        ENGINE.record(data, Activity.BREED + ":cow");
        assertEquals(Map.of("catch:salmon", 2L, "shear:sheep", 1L, "breed:cow", 1L), data.countersView());
    }
}
