package dev.wayfarer.curios;

import java.util.List;

/**
 * One curio from curios.yml: its name and colour, its hand-written description and flavour line, what it does and
 * when, and (for tools) its cooldown in seconds.
 */
public record CurioDefinition(String id, String name, int colour, List<String> description, String flavour,
        Effect effect, List<Condition> conditions, int cooldownSeconds) {
    public CurioDefinition {
        description = List.copyOf(description);
        conditions = List.copyOf(conditions);
    }

    public boolean tool() {
        return effect.tool();
    }

    /** The item model, {@code wayfarer:curio/<id>}. */
    public String model() {
        return "curio/" + id;
    }
}
