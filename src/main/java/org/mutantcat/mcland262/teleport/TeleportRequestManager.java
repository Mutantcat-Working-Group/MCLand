package org.mutantcat.mcland262.teleport;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.mutantcat.mcland262.user.AuthManager;
import org.mutantcat.mcland262.user.UserDatabase;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * 玩家传送请求：/tp 发起，30 秒内由目标玩家 /accept 接受。
 */
public class TeleportRequestManager {
    private static final String PREFIX = "[TP]";
    private static final String PREFIX_MONEY = "[交易系统]";
    private static final long REQUEST_TIMEOUT_TICKS = 30L * 20L;

    /** 请求类型：传送在 /accept 时直接飞过去，金币索要在 /accept 时按类型执行转账 */
    private enum Kind { TP, MONEY }

    private final JavaPlugin plugin;
    private final AuthManager authManager;
    private final UserDatabase userDatabase;
    private final Map<UUID, PendingRequest> requests = new HashMap<>();

    public TeleportRequestManager(JavaPlugin plugin, AuthManager authManager, UserDatabase userDatabase) {
        this.plugin = plugin;
        this.authManager = authManager;
        this.userDatabase = userDatabase;
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
        submit(requester, target, Kind.TP, 0);
        target.sendMessage(PREFIX + "用户" + requester.getName() + "想要传送到你身边，在30秒内输入/accept接受");
        requester.sendMessage(PREFIX + "已向" + target.getName() + "发送传送请求，等待对方接受");
    }

    /** 金币索要：目标在线才可发起，/accept 时校验对方余额并转账；与传送共用同一个待处理槽位 */
    public void requestMoney(Player requester, Player target, long amount) {
        if (target == null || !target.isOnline()) {
            requester.sendMessage(PREFIX_MONEY + "对方不在线，索要金币仅支持在线玩家");
            return;
        }
        if (authManager != null && !authManager.isLoggedIn(requester.getUniqueId())) {
            requester.sendMessage(PREFIX_MONEY + "请先登录后使用金币功能");
            return;
        }
        if (requester.getUniqueId().equals(target.getUniqueId())) {
            requester.sendMessage(PREFIX_MONEY + "不能向自己索要金币");
            return;
        }
        submit(requester, target, Kind.MONEY, amount);
        target.sendMessage(PREFIX_MONEY + "用户" + requester.getName() + "想要向你索要" + amount + "金币，在30秒内输入/accept接受");
        requester.sendMessage(PREFIX_MONEY + "已向" + target.getName() + "发送索要" + amount + "金币的请求，等待对方接受");
    }

    /** 覆盖目标玩家的旧请求，登记新的待处理请求并挂 30 秒过期任务 */
    private void submit(Player requester, Player target, Kind kind, long amount) {
        PendingRequest old = requests.remove(target.getUniqueId());
        if (old != null) {
            old.expiryTask.cancel();
        }
        PendingRequest pending = new PendingRequest(requester, target, kind, amount);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin,
                () -> expire(target.getUniqueId(), requester.getUniqueId()), REQUEST_TIMEOUT_TICKS);
        pending.expiryTask = task;
        requests.put(target.getUniqueId(), pending);
    }

    public void accept(Player target) {
        PendingRequest pending = requests.remove(target.getUniqueId());
        if (pending == null) {
            target.sendMessage(PREFIX + "当前没有可接受的请求");
            return;
        }
        pending.expiryTask.cancel();

        Player requester = Bukkit.getPlayer(pending.requesterUuid);
        if (requester == null || !requester.isOnline()) {
            target.sendMessage(prefixOf(pending.kind) + "请求方已不在线，请求已取消");
            return;
        }

        if (pending.kind == Kind.TP) {
            requester.teleport(target.getLocation());
            requester.sendMessage(PREFIX + "已传送到" + target.getName() + "身边");
            target.sendMessage(PREFIX + "已同意" + requester.getName() + "的传送请求");
            return;
        }
        acceptMoney(target, requester, pending.amount);
    }

    /** 接受金币索要：由接受方（target）转给请求方（requester），余额不足则双方都收到提示 */
    private void acceptMoney(Player target, Player requester, long amount) {
        if (userDatabase == null) {
            target.sendMessage(PREFIX_MONEY + "金币系统未启用");
            return;
        }
        boolean paid;
        try {
            paid = userDatabase.transfer(target.getUniqueId(), requester.getUniqueId(), amount);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "金币转账失败", e);
            target.sendMessage(PREFIX_MONEY + "转账失败，请联系管理员检查服务器日志");
            return;
        }
        if (!paid) {
            target.sendMessage(PREFIX_MONEY + "你的金币不足，无法支付" + amount + "金币");
            requester.sendMessage(PREFIX_MONEY + target.getName() + "的金币不足，无法支付" + amount + "金币");
            return;
        }
        long requesterBalance = 0L;
        try {
            requesterBalance = userDatabase.getBalance(requester.getUniqueId());
        } catch (SQLException ignored) {
            // 余额展示仅作提示，查询失败不影响本次转账结果
        }
        target.sendMessage(PREFIX_MONEY + "已向" + requester.getName() + "支付" + amount + "金币");
        requester.sendMessage(PREFIX_MONEY + target.getName() + "已向你支付" + amount + "金币，当前余额为" + requesterBalance);
    }

    private static String prefixOf(Kind kind) {
        return kind == Kind.MONEY ? PREFIX_MONEY : PREFIX;
    }

    private static String verbOf(Kind kind) {
        return kind == Kind.MONEY ? "索要金币" : "传送";
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
                    target.sendMessage(prefixOf(pending.kind) + verbOf(pending.kind) + "请求已取消");
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
                requester.sendMessage(prefixOf(pending.kind) + verbOf(pending.kind) + "请求已取消");
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
            target.sendMessage(prefixOf(pending.kind) + verbOf(pending.kind) + "请求已过期");
        }
        Player requester = Bukkit.getPlayer(requesterUuid);
        if (requester != null && requester.isOnline()) {
            requester.sendMessage(prefixOf(pending.kind) + verbOf(pending.kind) + "请求已过期");
        }
    }

    private static final class PendingRequest {
        private final UUID requesterUuid;
        private final UUID targetUuid;
        private final Kind kind;
        private final long amount;
        private BukkitTask expiryTask;

        private PendingRequest(Player requester, Player target, Kind kind, long amount) {
            this.requesterUuid = requester.getUniqueId();
            this.targetUuid = target.getUniqueId();
            this.kind = kind;
            this.amount = amount;
        }
    }
}
