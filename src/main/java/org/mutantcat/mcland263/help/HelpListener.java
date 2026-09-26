package org.mutantcat.mcland263.help;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public class HelpListener implements Listener {
    @EventHandler(ignoreCancelled = true)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (event.isCancelled()) {
            return;
        }
        String[] parts = event.getMessage().split("\\s+");
        if (parts.length == 0 || !parts[0].equalsIgnoreCase("/help")) {
            return;
        }
        event.setCancelled(true);
        HelpCommand.sendHelp(event.getPlayer());
    }
}
