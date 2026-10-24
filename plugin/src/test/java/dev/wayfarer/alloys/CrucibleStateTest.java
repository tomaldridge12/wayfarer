package dev.wayfarer.alloys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Loading a crucible, its melt clock (pause, unloaded time, finish) and its chunk data. */
class CrucibleStateTest {
    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private final AlloyRules rules = AlloyFixtures.rules();

    private CrucibleState steelBatch() {
        CrucibleState c = CrucibleState.empty(1, 64, 2, 0);
        c = c.add("iron_ingot", 4, 64, ALEX, 0).state();
        return c.add("coal_block", 1, 64, ALEX, 0).state();
    }

    private AlloyRules.Melt verdict(CrucibleState c, Heat heat) {
        return rules.melt(c.contents(), Optional.ofNullable(heat));
    }

    @Test
    void loadingStopsAtTheLimitAndReportsWhatWasTaken() {
        CrucibleState c = CrucibleState.empty(0, 0, 0, 0).add("iron_ingot", 60, 64, ALEX, 0).state();
        CrucibleState.Added more = c.add("coal_block", 10, 64, ALEX, 0);
        assertEquals(4, more.taken());
        assertEquals(64, more.state().total());
        assertEquals(0, more.state().add("coal_block", 1, 64, ALEX, 0).taken());
    }

    @Test
    void aMeltFinishesAfterItsTimeAndEmptiesTheCrucible() {
        CrucibleState c = steelBatch();
        CrucibleState.Step half = c.step(verdict(c, Heat.LOW), 300);
        assertTrue(half.finished().isEmpty());
        assertEquals(300, half.state().progress());
        CrucibleState.Step done = half.state().step(verdict(half.state(), Heat.LOW), 600);
        assertEquals("steel", done.finished().orElseThrow().alloy().id());
        assertEquals(Map.of(), done.state().contents());
    }

    @Test
    void losingHeatPausesTheMeltAndItCarriesOnWhenHeatReturns() {
        CrucibleState c = steelBatch().step(verdict(steelBatch(), Heat.LOW), 200).state();
        CrucibleState paused = c.step(verdict(c, null), 5000).state();
        assertEquals(200, paused.progress());
        CrucibleState.Step again = paused.step(verdict(paused, Heat.LOW), 5100);
        assertEquals(300, again.state().progress());
        assertTrue(again.finished().isEmpty());
    }

    @Test
    void timeWhileTheChunkWasUnloadedCountsIfTheHeatIsThere() {
        CrucibleState c = steelBatch();
        assertTrue(c.step(verdict(c, Heat.HIGH), 100_000).finished().isPresent());
    }

    @Test
    void changingTheContentsStartsTheMeltOver() {
        CrucibleState c = steelBatch().step(verdict(steelBatch(), Heat.LOW), 400).state();
        CrucibleState more = c.add("iron_ingot", 4, 64, ALEX, 0).state();
        assertEquals(0, more.progress());
    }

    @Test
    void timeBeforeLoadingDoesNotCount() {
        CrucibleState c = CrucibleState.empty(0, 0, 0, 0).add("iron_ingot", 4, 64, ALEX, 10_000).state()
                .add("coal_block", 1, 64, ALEX, 10_000).state();
        CrucibleState.Step s = c.step(verdict(c, Heat.LOW), 10_020);
        assertTrue(s.finished().isEmpty());
        assertEquals(20, s.state().progress());
    }

    @Test
    void aWrongMixHasNoProgress() {
        CrucibleState c = CrucibleState.empty(0, 0, 0, 0).add("dirt", 3, 64, ALEX, 0).state();
        assertEquals(0, c.step(verdict(c, Heat.SOUL), 10_000).state().progress());
    }

    @Test
    void chunkDataRoundTrips() {
        CrucibleState a = steelBatch().step(verdict(steelBatch(), Heat.LOW), 123).state();
        CrucibleState b = CrucibleState.empty(-5, -60, 7, 99);
        CrucibleState.Decoded d = CrucibleState.decode(CrucibleState.encode(List.of(a, b)));
        assertEquals(List.of(a, b), d.crucibles());
        assertEquals(List.of(), d.problems());
    }

    @Test
    void anUnreadableRecordIsSkippedAndTheRestLoad() {
        String good = CrucibleState.encode(List.of(steelBatch()));
        CrucibleState.Decoded d = CrucibleState.decode("garbage|" + good);
        assertEquals(List.of(steelBatch()), d.crucibles());
        assertEquals(1, d.problems().size());
    }
}
