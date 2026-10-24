package dev.wayfarer.marks;

import java.util.List;
import java.util.Set;

/**
 * A ladder paying an ExcellentEnchants book, one level per threshold: Veinminer along ores, Treefeller along logs.
 * {@code enchant} is the namespaced key. No server types.
 */
public record BookDefinition(String id, String name, String enchant, String unit, Set<String> selector,
        List<Long> thresholds) {
}
