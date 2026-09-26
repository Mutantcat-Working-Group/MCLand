package org.mutantcat.mcland262.teleport;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.mutantcat.mcland262.user.AuthManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家传送请求：/tp 发起，30 秒内由目标玩家 /accept 接受。
 */
public class TeleportRequestManager {
    private static final String PREFIX = "[TP]";
    private static final long REQUEST_TIMEOUT_TICKS = 30L * 20L;

    private final JavaPlugin plugin;
    private final AuthManager authManager;
    private final Map<UUID, PendingRequest> requests = new HashMap<>();

    public TeleportRequestManager(JavaPlugin plugin, AuthManager authManager) {
        this.plugin = plugin;
        this.authManager = authManager;
    }

    public void handleTpCommand(Player player, String[] args) {
        if (isAdmin(player)) {
            // 管理员直接走官方 /tp，不需要对方确认
            Bukkit.dispatchCommand(player, "minecraft:tp " + String.join(" ", args));
            return;
        }
        if (authManager != null && !authManager.isLoggedIn(player.getUniqueId())) {
            player.sendMessage(PREFIX + "请先登录后使用传送请求");
            return;
        }
        if (args.length != 1) {
            player.sendMessage(PREFIX + "请输入“/tp 玩家名”发送传送请求");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage(PREFIX + "找不到玩家" + args[0] + "，请确认对方在线");
            return;
        }
        request(player, target);
    }

    private void request(Player requester, Player target) {
        if (requester.getUniqueId().equals(target.getUniqueId())) {
            requester.sendMessage(PREFIX + "不能给自己发送传送请求");
            return;
        }

        PendingRequest old = requests.remove(target.getUniqueId());
        if (old != null) {
            old.expiryTask.cancel();
        }

        PendingRequest pending = new PendingRequest(requester, target);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin,
                () -> expire(target.getUniqueId(), requester.getUniqueId()), REQUEST_TIMEOUT_TICKS);
        pending.expiryTask = task;
        requests.put(target.getUniqueId(), pending);

        target.sendMessage(PREFIX + "用户" + requester.getName() + "想要传送到你身边，在30秒内输入/accept接受");
        requester.sendMessage(PREFIX + "已向" + target.getName() + "发送传送请求，等待对方接受");
    }

    public void accept(Player target) {
        PendingRequest pending = requests.remove(target.getUniqueId());
        if (pending == null) {
            target.sendMessage(PREFIX + "当前没有可接受的传送请求");
            return;
        }
        pending.expiryTask.cancel();

        Player requester = Bukkit.getPlayer(pending.requesterUuid);
        if (requester == null || !requester.isOnline()) {
            target.sendMessage(PREFIX + "请求方已不在线，传送请求已取消");
            return;
        }

        requester.teleport(target.getLocation());
        requester.sendMessage(PREFIX + "已传送到" + target.getName() + "身边");
        target.sendMessage(PREFIX + "已同意" + requester.getName() + "的传送请求");
    }

    private boolean isAdmin(Player player) {
        return player.isOp() || player.hasPermission("minecraft.command.teleport");
    }

    public void handleQuit(UUID uuid) {
        requests.values().removeIf(pending -> {
            if (pending.requesterUuid.equals(uuid)) {
                pending.expiryTask.cancel();
                Player target = Bukkit.getPlayer(pending.targetUuid);
                if (target != null && target.isOnline()) {
                    target.sendMessage(PREFIX + "请求方已离线，传送请求已取消");
                }
                return true;
            }
            return false;
        });

        PendingRequest pending = requests.remove(uuid);
        if (pending != null) {
            pending.expiryTask.cancel();
            Player requester = Bukkit.getPlayer(pending.requesterUuid);
            if (requester != null && requester.isOnline()) {
                requester.sendMessage(PREFIX + "目标玩家已离线，传送请求已取消");
            }
        }
    }

    public void close() {
        requests.values().forEach(pending -> pending.expiryTask.cancel());
        requests.clear();
    }

    private void expire(UUID targetUuid, UUID requesterUuid) {
        PendingRequest pending = requests.get(targetUuid);
        if (pending == null || !pending.requesterUuid.equals(requesterUuid)) {
            return;
        }
        requests.remove(targetUuid);

        Player target = Bukkit.getPlayer(targetUuid);
        if (target != null && target.isOnline()) {
            target.sendMessage(PREFIX + "传送请求已过期");
        }
        Player requester = Bukkit.getPlayer(requesterUuid);
        if (requester != null && requester.isOnline()) {
            requester.sendMessage(PREFIX + "传送请求已过期");
        }
    }

    private static final class PendingRequest {
        private final UUID requesterUuid;
        private final UUID targetUuid;
        private BukkitTask expiryTask;

        private PendingRequest(Player requester, Player target) {
            this.requesterUuid = requester.getUniqueId();
            this.targetUuid = target.getUniqueId();
        }
    }
}
