package org.mutantcat.mcland262.user;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
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
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 未注册/未登录玩家进服时提示，并限制其移动、操作、聊天和其他命令，仅允许注册和登录命令。
 * 进服后超时未登录会被踢出服务器，超时时长由配置决定。
 */
public class AuthListener implements Listener {
    private static final String PREFIX = "[MCLand]";

    private final JavaPlugin plugin;
    private final AuthManager authManager;
    /** 进服后允许的登录等待时长（秒），<=0 表示不启用超时踢出 */
    private final long loginTimeoutSeconds;
    /** 每个在线玩家的登录超时任务，退服或点名前取消 */
    private final Map<UUID, BukkitTask> loginTimeoutTasks = new HashMap<>();

    public AuthListener(JavaPlugin plugin, AuthManager authManager) {
        this(plugin, authManager, 60L);
    }

    public AuthListener(JavaPlugin plugin, AuthManager authManager, long loginTimeoutSeconds) {
        this.plugin = plugin;
        this.authManager = authManager;
        this.loginTimeoutSeconds = Math.max(0L, loginTimeoutSeconds);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        scheduleLoginTimeout(player);
        if (authManager.isRegistered(player.getUniqueId())) {
            // 基岩版走缓存：缓存有效期内同一 IP 进服直接放过，不用再输密码
            if (authManager.tryBedrockAutoLogin(player)) {
                player.sendMessage(PREFIX + "检测到本 IP 近期已登录，已自动登录，欢迎回来！");
                return;
            }
            player.sendMessage(PREFIX + "请输入“/login 密码”登录当前用户名的账号");
        } else {
            player.sendMessage(PREFIX + "请输入“/register 密码 确认密码”注册当前用户名的密码");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        cancelLoginTimeout(event.getPlayer().getUniqueId());
        authManager.logout(event.getPlayer().getUniqueId());
    }

    /** 进服即开始计时；到点仍未登录就踢出，基岩版自动登录的情况会在点名前被判定为已登录而放过 */
    private void scheduleLoginTimeout(Player player) {
        if (loginTimeoutSeconds <= 0) {
            return;
        }
        UUID uuid = player.getUniqueId();
        cancelLoginTimeout(uuid);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin,
                () -> expireLoginTimeout(uuid), loginTimeoutSeconds * 20L);
        loginTimeoutTasks.put(uuid, task);
    }

    /** 超时检查：已登录（含基岩版缓存自动登录）直接放过，否则踢出服务器 */
    private void expireLoginTimeout(UUID uuid) {
        loginTimeoutTasks.remove(uuid);
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline() || authManager.isLoggedIn(uuid)) {
            return;
        }
        player.kickPlayer(PREFIX + "超时未登录，已被请出服务器，请重新进服并登录");
    }

    private void cancelLoginTimeout(UUID uuid) {
        BukkitTask task = loginTimeoutTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
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

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (event.isCancelled()) {
            return;
        }
        if (authManager.isLoggedIn(event.getPlayer().getUniqueId())) {
            return;
        }
        String message = event.getMessage().toLowerCase(Locale.ROOT).trim();
        if (message.equals("/register") || message.startsWith("/register ")
                || message.equals("/login") || message.startsWith("/login ")) {
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
