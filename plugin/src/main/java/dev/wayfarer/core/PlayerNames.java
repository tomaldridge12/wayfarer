package dev.wayfarer.core;

import java.util.Optional;
import java.util.UUID;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;

/** Finds players for commands by UUID or name, and names them for messages. */
public final class PlayerNames {
    private PlayerNames() {
    }

    /** A UUID as written, an online player, or an offline player the server remembers by name; else null. */
    public static UUID resolve(Server server, String name) {
        try {
            return UUID.fromString(name);
        } catch (IllegalArgumentException ignored) {
            // not a UUID; try names
        }
        Player online = server.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        OfflinePlayer offline = server.getOfflinePlayerIfCached(name);
        return offline == null ? null : offline.getUniqueId();
    }

    /** The player's last known name, or the UUID if the server never saw them. */
    public static String nameOf(Server server, UUID id) {
        return Optional.ofNullable(server.getOfflinePlayer(id).getName()).orElse(id.toString());
    }
}
