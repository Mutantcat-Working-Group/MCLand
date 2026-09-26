package org.mutantcat.mcland263.user;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

/**
 * 未注册/未登录玩家进服时提示，并限制其移动、操作和聊天。
 */
public class AuthListener implements Listener {
    private static final String PREFIX = "[MCLand]";

    private final JavaPlugin plugin;
    private final AuthManager authManager;

    public AuthListener(JavaPlugin plugin, AuthManager authManager) {
        this.plugin = plugin;
        this.authManager = authManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (authManager.isRegistered(player.getUniqueId())) {
            player.sendMessage(PREFIX + "请输入“/login 密码”登录当前用户名的账号");
        } else {
            player.sendMessage(PREFIX + "请输入“/register 密码 确认密码”注册当前用户名的密码");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        authManager.logout(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!authManager.isLoggedIn(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!authManager.isLoggedIn(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            deny(event.getPlayer());
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!authManager.isLoggedIn(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            deny(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!authManager.isLoggedIn(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            deny(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (!authManager.isLoggedIn(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            deny(event.getPlayer());
        }
    }

    @EventHandler
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        if (!authManager.isLoggedIn(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            deny(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (authManager.isLoggedIn(event.getPlayer().getUniqueId())) {
            return;
        }
        String message = event.getMessage().toLowerCase(Locale.ROOT).trim();
        if (message.equals("/register") || message.startsWith("/register ")
                || message.equals("/login") || message.startsWith("/login ")
                || message.equals("/password") || message.startsWith("/password ")
                || message.equals("/help") || message.startsWith("/help ")) {
            return;
        }
        event.setCancelled(true);
        deny(event.getPlayer());
    }

    private void deny(Player player) {
        Bukkit.getScheduler().runTask(plugin, () ->
                player.sendMessage(PREFIX + "请先登录后操作，未注册请输入“/register 密码 确认密码”，已注册请输入“/login 密码”"));
    }
}
