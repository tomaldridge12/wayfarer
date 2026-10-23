package dev.wayfarer.banners;

import java.util.ArrayDeque;
import java.util.Deque;

/** Caps how many ▲ indicators exist at once. Each start reserves a slot until its lifetime ends. */
public final class IndicatorBudget {
    private final int max;
    private final long lifetimeTicks;
    private final Deque<Long> endTicks = new ArrayDeque<>();

    public IndicatorBudget(int max, long lifetimeTicks) {
        this.max = max;
        this.lifetimeTicks = lifetimeTicks;
    }

    /** @return true if a new indicator may start now; false means skip it */
    public boolean tryStart(long nowTick) {
        while (!endTicks.isEmpty() && endTicks.peekFirst() <= nowTick) {
            endTicks.pollFirst();
        }
        if (endTicks.size() >= max) {
            return false;
        }
        endTicks.addLast(nowTick + lifetimeTicks);
        return true;
    }

    public int active(long nowTick) {
        return (int) endTicks.stream().filter(end -> end > nowTick).count();
    }
}
