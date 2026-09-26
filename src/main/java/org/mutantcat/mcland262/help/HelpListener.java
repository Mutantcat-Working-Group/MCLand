package org.mutantcat.mcland262.help;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public class HelpListener implements Listener {
    private final HelpCommand helpCommand;

    public HelpListener(HelpCommand helpCommand) {
        this.helpCommand = helpCommand;
    }

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
        helpCommand.sendHelp(event.getPlayer());
    }
}
