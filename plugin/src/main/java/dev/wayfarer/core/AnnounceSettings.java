package dev.wayfarer.core;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Which announcement categories are on (the {@code announce:} section of progression.yml). All are on by default. */
public record AnnounceSettings(Set<AnnounceCategory> enabled) {
    public AnnounceSettings {
        enabled = Set.copyOf(enabled);
    }

    public static AnnounceSettings defaults() {
        return new AnnounceSettings(EnumSet.allOf(AnnounceCategory.class));
    }

    public boolean on(AnnounceCategory category) {
        return enabled.contains(category);
    }

    /** A missing section or switch leaves a category on; an unknown name or a non-boolean value is a problem and is ignored. */
    public static AnnounceSettings parse(Object raw, List<String> problems) {
        if (!(raw instanceof Map<?, ?> map)) {
            return defaults();
        }
        Set<AnnounceCategory> on = EnumSet.allOf(AnnounceCategory.class);
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String name = String.valueOf(entry.getKey());
            var category = AnnounceCategory.ofSwitch(name);
            if (category.isEmpty()) {
                problems.add("announce." + name + ": unknown category; ignored");
            } else if (entry.getValue() instanceof Boolean b) {
                if (!b) {
                    on.remove(category.get());
                }
            } else {
                problems.add("announce." + name + " must be true or false; leaving it on");
            }
        }
        return new AnnounceSettings(on);
    }
}
