package dev.wayfarer.alloys;

import dev.wayfarer.core.BossDefeatedEvent;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

/** A structure guardian's defeat gives its alloy's template to each online player who damaged it. */
final class GuardianTemplates implements Listener {
    private final Server server;
    private final Supplier<AlloyRules> rules;
    private final AlloyItems items;
    private final Logger log;

    GuardianTemplates(Server server, Supplier<AlloyRules> rules, AlloyItems items, Logger log) {
        this.server = server;
        this.rules = rules;
        this.items = items;
        this.log = log;
    }

    @EventHandler
    void onDefeat(BossDefeatedEvent event) {
        rules.get().templateFrom(event.boss().bossId(), event.boss().guardian()).ifPresent(alloy -> {
            for (UUID id : event.damagers()) {
                Player player = server.getPlayer(id);
                boolean lucky = ThreadLocalRandom.current().nextDouble() < alloy.template().chance();
                String outcome = player == null ? "offline, not given" : lucky ? "given" : "not rolled";
                if (player != null && lucky) {
                    ItemStack template = items.template(alloy, 1);
                    player.getInventory().addItem(template).values()
                            .forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
                }
                log.info("[alloys] Guardian " + event.boss().bossId() + ": template:" + alloy.id() + " for " + id
                        + " (" + outcome + ")");
            }
        });
    }
}
