package dev.wayfarer.core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * Per-player scoreboards (for the sidebar) without losing the shared glow colours: a glow colour is a scoreboard
 * team, and a client only sees the teams of the scoreboard it is shown, so every board carries a copy of each glow
 * team and glowing entities are added to all of them. Main thread.
 */
public final class PlayerBoards {
    private final Server server;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    public PlayerBoards(Server server) {
        this.server = server;
    }

    private Scoreboard main() {
        return server.getScoreboardManager().getMainScoreboard();
    }

    /** The player's own scoreboard, created (with copies of the glow teams) the first time and shown to them. */
    public Scoreboard of(Player player) {
        Scoreboard board = boards.get(player.getUniqueId());
        if (board == null) {
            board = server.getScoreboardManager().getNewScoreboard();
            for (Team glow : main().getTeams()) {
                if (glow.getName().startsWith(GLOW_PREFIX)) {
                    Team copy = board.registerNewTeam(glow.getName());
                    copy.color(NamedTextColor.NAMES.value(glow.getName().substring(GLOW_PREFIX.length())));
                    glow.getEntries().forEach(copy::addEntry);
                }
            }
            boards.put(player.getUniqueId(), board);
        }
        if (player.getScoreboard() != board) {
            player.setScoreboard(board);
        }
        return board;
    }

    /** Forgets a player's board when they leave. */
    public void release(UUID player) {
        boards.remove(player);
    }

    public static final String GLOW_PREFIX = "wayfarer_glow_";

    /** Makes the entity glow in this colour for everyone, whichever scoreboard they look at. */
    public void glow(Entity entity, String colour) {
        String name = GLOW_PREFIX + colour.toLowerCase(java.util.Locale.ROOT);
        NamedTextColor colourValue = NamedTextColor.NAMES.value(colour);
        for (Scoreboard board : everyBoard()) {
            Team team = board.getTeam(name);
            if (team == null) {
                team = board.registerNewTeam(name);
                team.color(colourValue);
            }
            team.addEntity(entity);
        }
    }

    /** Takes the entity out of every glow team. */
    public void unglow(Entity entity) {
        for (Scoreboard board : everyBoard()) {
            Team team = board.getEntityTeam(entity);
            if (team != null && team.getName().startsWith(GLOW_PREFIX)) {
                team.removeEntity(entity);
            }
        }
    }

    private java.util.List<Scoreboard> everyBoard() {
        java.util.List<Scoreboard> all = new java.util.ArrayList<>();
        all.add(main());
        all.addAll(boards.values());
        return all;
    }
}
