package dev.wayfarer.marks;

import java.util.Locale;
import java.util.Optional;

/** The kind of tool a mark goes on, matched by vanilla item key (alloy gear is built on vanilla items too). */
public enum ToolType {
    SHOVEL, SHEARS, FISHING_ROD, HOE;

    public boolean fits(String itemKey) {
        return switch (this) {
            case SHOVEL -> itemKey.endsWith("_shovel");
            case SHEARS -> itemKey.equals("shears");
            case FISHING_ROD -> itemKey.equals("fishing_rod");
            case HOE -> itemKey.endsWith("_hoe");
        };
    }

    public String label() {
        return switch (this) {
            case SHOVEL -> "shovels";
            case SHEARS -> "shears";
            case FISHING_ROD -> "fishing rods";
            case HOE -> "hoes";
        };
    }

    static Optional<ToolType> parse(Object name) {
        try {
            return Optional.of(valueOf(String.valueOf(name).toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
