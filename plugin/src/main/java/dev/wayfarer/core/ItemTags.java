package dev.wayfarer.core;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Generic item tagging rules: a module marks an item with one string value under its own key.
 * Free of server types; {@link ItemTagService} backs {@link Holder} with an item's persistent data.
 */
public final class ItemTags {
    private static final Pattern MODULE_ID = Pattern.compile("[a-z0-9_]+");

    /** Somewhere a string can be stored by key. */
    public interface Holder {
        String get(String key);

        void set(String key, String value);
    }

    private ItemTags() {
    }

    public static void write(Holder holder, String module, String value) {
        holder.set(storageKey(module), value);
    }

    public static Optional<String> read(Holder holder, String module) {
        return Optional.ofNullable(holder.get(storageKey(module)));
    }

    static String storageKey(String module) {
        if (!MODULE_ID.matcher(module).matches()) {
            throw new IllegalArgumentException("Module id must match [a-z0-9_]+: " + module);
        }
        return module + "_tag";
    }
}
