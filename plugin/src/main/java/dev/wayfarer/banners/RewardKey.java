package dev.wayfarer.banners;

import java.util.Locale;

/** Names one reward: its type (banner for now; heads and gear later) and its id within that type. */
public record RewardKey(String type, String id) {
    public static final String BANNER = "banner";

    public static RewardKey banner(String id) {
        return new RewardKey(BANNER, id);
    }

    /** "banner:zombie", or a bare "zombie" read as {@code defaultType}. */
    public static RewardKey parse(String text, String defaultType) {
        int colon = text.indexOf(':');
        return colon < 0 ? new RewardKey(defaultType, text)
                : new RewardKey(text.substring(0, colon).toLowerCase(Locale.ROOT), text.substring(colon + 1));
    }

    @Override
    public String toString() {
        return type + ":" + id;
    }
}
