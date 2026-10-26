package dev.wayfarer.comforts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.IntPredicate;

/** What a death keeps, as plain rules over inventory slot numbers (Bukkit's player inventory: 0-8 hotbar, 9-35 main, 36-39 armour, 40 offhand). */
final class DeathRules {
    static final int HOTBAR_SLOTS = 9;
    private static final int ARMOUR_FIRST = 36;
    private static final int ARMOUR_LAST = 39;
    private static final int OFFHAND = 40;

    private DeathRules() {
    }

    /**
     * The slots whose items stay with the player, ascending. The main inventory is never kept. {@code keepable} says
     * whether a slot holds something that may be kept (not empty, not cursed with vanishing).
     */
    static List<Integer> keptSlots(boolean hotbar, boolean armour, boolean offhand, IntPredicate keepable) {
        List<Integer> slots = new ArrayList<>();
        if (hotbar) {
            for (int slot = 0; slot < HOTBAR_SLOTS; slot++) {
                add(slots, slot, keepable);
            }
        }
        if (armour) {
            for (int slot = ARMOUR_FIRST; slot <= ARMOUR_LAST; slot++) {
                add(slots, slot, keepable);
            }
        }
        if (offhand) {
            add(slots, OFFHAND, keepable);
        }
        return Collections.unmodifiableList(slots);
    }

    private static void add(List<Integer> slots, int slot, IntPredicate keepable) {
        if (keepable.test(slot)) {
            slots.add(slot);
        }
    }

    /**
     * Paper hands kept items back with addItem, so a hotbar with gaps comes back packed to the left. This gives the
     * hotbar the player had: the kept stacks (in their original slot order) put back at {@code originalSlots}. It
     * answers only when the hotbar now holds exactly those stacks and nothing else, so it never rearranges a hotbar that
     * another plugin touched. {@code hotbarNow} has nine entries, null for an empty slot.
     */
    static <T> Optional<List<T>> hotbarLayout(List<Integer> originalSlots, List<T> kept, List<T> hotbarNow) {
        if (originalSlots.size() != kept.size() || hotbarNow.size() != HOTBAR_SLOTS) {
            return Optional.empty();
        }
        List<T> present = new ArrayList<>();
        for (T stack : hotbarNow) {
            if (stack != null) {
                present.add(stack);
            }
        }
        if (!present.equals(kept)) {
            return Optional.empty();
        }
        List<T> layout = new ArrayList<>(Collections.nCopies(HOTBAR_SLOTS, (T) null));
        for (int i = 0; i < kept.size(); i++) {
            int slot = originalSlots.get(i);
            if (slot < 0 || slot >= HOTBAR_SLOTS || layout.get(slot) != null) {
                return Optional.empty();
            }
            layout.set(slot, kept.get(i));
        }
        return Optional.of(layout);
    }
}
