package dev.wayfarer.core.pack;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The resource pack Wayfarer offers every player: its URL, SHA-1 and prompt. Normally the URL and hash come from the
 * jar (wayfarer-pack.properties, written when the pack was built), so plugin and pack never drift apart; pack.yml can
 * switch it off or point elsewhere. No server types.
 */
public record PackOffer(String url, String sha1, String tag, String prompt) {
    static final String DEFAULT_PROMPT = "Wayfarer's alloys, relics and marks need this pack to look right. "
            + "Accept once and your game remembers.";
    private static final Pattern SHA1 = Pattern.compile("[0-9a-f]{40}");

    /** What to do after a player's client reports on the pack. */
    public enum Action {
        /** They declined a required pack. */
        KICK,
        /** The download or load failed; tell them, but let them stay (a hosting outage must not lock everyone out). */
        WARN,
        NONE
    }

    /** A stable id per pack version, so a client that already has this version is not asked again. */
    public UUID id() {
        return UUID.nameUUIDFromBytes(sha1.getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * The offer from pack.yml and the jar's properties; empty when switched off or unusable (with a problem).
     * A url override needs its sha1 too.
     */
    public static Optional<PackOffer> from(Map<String, Object> config, Properties jar, List<String> problems) {
        Object enabled = config.getOrDefault("enabled", true);
        if (!(enabled instanceof Boolean)) {
            problems.add("pack.yml: enabled must be true or false; using true");
        } else if (!(Boolean) enabled) {
            return Optional.empty();
        }
        Object rawPrompt = config.get("prompt");
        if (rawPrompt != null && !(rawPrompt instanceof String p && !p.isBlank())) {
            problems.add("pack.yml: prompt must be text; using the default");
        }
        String prompt = rawPrompt instanceof String p && !p.isBlank() ? p : DEFAULT_PROMPT;
        Object url = config.get("url");
        Object sha1 = config.get("sha1");
        if (url != null || sha1 != null) {
            String u = String.valueOf(url);
            String h = String.valueOf(sha1).toLowerCase(Locale.ROOT);
            if (url == null || !u.startsWith("https://") || !SHA1.matcher(h).matches()) {
                problems.add("pack.yml: url (https://...) and sha1 (40 hex digits) must be set together; using the built pack");
            } else {
                return Optional.of(new PackOffer(u, h, "custom-" + h.substring(0, 12), prompt));
            }
        }
        String u = jar.getProperty("url");
        String h = jar.getProperty("sha1");
        if (u == null || h == null || !SHA1.matcher(h).matches()) {
            problems.add("The jar has no wayfarer-pack.properties; no resource pack is offered");
            return Optional.empty();
        }
        return Optional.of(new PackOffer(u, h, jar.getProperty("tag", "pack-" + h.substring(0, 12)), prompt));
    }

    /** {@code status} is the client's report, named as Paper's PlayerResourcePackStatusEvent.Status. */
    public static Action onStatus(String status) {
        return switch (status) {
            case "DECLINED" -> Action.KICK;
            case "FAILED_DOWNLOAD", "INVALID_URL", "FAILED_RELOAD" -> Action.WARN;
            default -> Action.NONE;
        };
    }
}
