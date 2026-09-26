package org.mutantcat.mcland262.teleport;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Arrays;

public class TeleportListener implements Listener {
    private final TeleportRequestManager manager;

    public TeleportListener(TeleportRequestManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        manager.handleQuit(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (event.isCancelled()) {
            return;
        }
        Player player = event.getPlayer();
        String[] parts = event.getMessage().split("\\s+");
        if (parts.length == 0 || !parts[0].equalsIgnoreCase("/tp")) {
            return;
        }
        if (player.isOp() || player.hasPermission("minecraft.command.teleport")) {
            // 管理员保留官方 /tp，直接传送
            return;
        }
        event.setCancelled(true);
        String[] args = parts.length > 1 ? Arrays.copyOfRange(parts, 1, parts.length) : new String[0];
        manager.handleTpCommand(player, args);
    }
}
