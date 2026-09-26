package org.mutantcat.mcland262.money;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.mutantcat.mcland262.teleport.TeleportRequestManager;
import org.mutantcat.mcland262.user.AuthManager;
import org.mutantcat.mcland262.user.UserDatabase;

import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

/**
 * 金币命令：/money 查余额，/money give 数量 玩家名 转账，/money request 数量 玩家名 向在线玩家索要。
 * 金币与账号绑定，读写都落在登录库 users 表的 balance 列。
 */
public class MoneyCommand implements CommandExecutor {
    private static final String PREFIX = "[交易系统]";

    private final JavaPlugin plugin;
    private final AuthManager authManager;
    private final TeleportRequestManager teleportManager;
    private final UserDatabase userDatabase;

    public MoneyCommand(JavaPlugin plugin, AuthManager authManager,
                        TeleportRequestManager teleportManager, UserDatabase userDatabase) {
        this.plugin = plugin;
        this.authManager = authManager;
        this.teleportManager = teleportManager;
        this.userDatabase = userDatabase;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(PREFIX + "该命令仅限玩家使用");
            return true;
        }
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("money")) {
            handleMoney((Player) sender, args);
            return true;
        }
        return false;
    }

    private void handleMoney(Player player, String[] args) {
        if (authManager != null && !authManager.isLoggedIn(player.getUniqueId())) {
            player.sendMessage(PREFIX + "请先登录后使用金币功能");
            return;
        }
        if (userDatabase == null) {
            player.sendMessage(PREFIX + "金币系统未启用");
            return;
        }
        if (args.length == 0) {
            showBalance(player);
            return;
        }
        if (args[0].equalsIgnoreCase("give")) {
            if (args.length != 3) {
                player.sendMessage(PREFIX + "请输入“/money give 数量 玩家名”向他人转帐金币，对方可不在线，玩家名前可带@");
                return;
            }
            giveMoney(player, args[1], args[2]);
            return;
        }
        if (args[0].equalsIgnoreCase("request")) {
            if (args.length != 3) {
                player.sendMessage(PREFIX + "请输入“/money request 数量 玩家名”向在线玩家索要金币，需对方/accept且余额充足");
                return;
            }
            requestMoney(player, args[1], args[2]);
            return;
        }
        player.sendMessage(PREFIX + "未知用法。/money 查看余额；“/money give 数量 玩家名”转帐；“/money request 数量 玩家名”向在线玩家索要");
    }

    private void requestMoney(Player player, String amountArg, String targetArg) {
        if (authManager != null && !authManager.isLoggedIn(player.getUniqueId())) {
            player.sendMessage(PREFIX + "请先登录后使用金币功能");
            return;
        }
        if (userDatabase == null || teleportManager == null) {
            player.sendMessage(PREFIX + "金币系统未启用");
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(amountArg.trim());
        } catch (NumberFormatException e) {
            player.sendMessage(PREFIX + "金币数量必须为正整数");
            return;
        }
        if (amount <= 0) {
            player.sendMessage(PREFIX + "金币数量必须为正整数");
            return;
        }
        String targetName = stripAt(targetArg);
        if (targetName.isEmpty()) {
            player.sendMessage(PREFIX + "请输入对方玩家名，例如“/money request 100 @对方名字”");
            return;
        }
        if (targetName.equalsIgnoreCase(player.getName())) {
            player.sendMessage(PREFIX + "不能向自己索要金币");
            return;
        }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !target.isOnline()) {
            player.sendMessage(PREFIX + "对方不在线，索要金币仅支持在线玩家");
            return;
        }
        teleportManager.requestMoney(player, target, amount);
    }

    private void showBalance(Player player) {
        long balance;
        try {
            balance = userDatabase.getBalance(player.getUniqueId());
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "查询金币余额失败", e);
            player.sendMessage(PREFIX + "查询金币余额失败，请联系管理员检查服务器日志");
            return;
        }
        if (balance < 0) {
            player.sendMessage(PREFIX + "尚未查询到账号，请先注册登录");
            return;
        }
        player.sendMessage(PREFIX + "当前金币余额为" + balance);
    }

    /** 转账：校验数量、账号、余额后走数据库事务，对方在线则顺带通知一声 */
    private void giveMoney(Player player, String amountArg, String targetArg) {
        long amount;
        try {
            amount = Long.parseLong(amountArg.trim());
        } catch (NumberFormatException e) {
            player.sendMessage(PREFIX + "金币数量必须为正整数");
            return;
        }
        if (amount <= 0) {
            player.sendMessage(PREFIX + "金币数量必须为正整数");
            return;
        }
        String targetName = stripAt(targetArg);
        if (targetName.isEmpty()) {
            player.sendMessage(PREFIX + "请输入“/money give 数量 玩家名”向他人转帐金币，玩家名前可带@");
            return;
        }

        UUID from = player.getUniqueId();
        UUID to;
        try {
            to = userDatabase.findUuidByName(targetName);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "查询目标账号失败", e);
            player.sendMessage(PREFIX + "查询目标账号失败，请联系管理员检查服务器日志");
            return;
        }
        if (to == null) {
            player.sendMessage(PREFIX + "找不到玩家账号" + targetName + "，请确认对方已注册");
            return;
        }
        if (to.equals(from)) {
            player.sendMessage(PREFIX + "不能给自己转帐金币");
            return;
        }

        long balance = -1L;
        boolean ok = false;
        try {
            balance = userDatabase.getBalance(from);
            if (balance < 0) {
                player.sendMessage(PREFIX + "尚未查询到账号，请先注册登录");
                return;
            }
            if (balance < amount) {
                player.sendMessage(PREFIX + "金币不足，当前余额为" + balance + "，无法转帐" + amount + "金币");
                return;
            }
            ok = userDatabase.transfer(from, to, amount);
            if (ok) {
                balance = userDatabase.getBalance(from);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "金币转账失败", e);
            player.sendMessage(PREFIX + "转账失败，请联系管理员检查服务器日志");
            return;
        }
        if (!ok) {
            player.sendMessage(PREFIX + "转账失败，请联系管理员检查服务器日志");
            return;
        }
        player.sendMessage(PREFIX + "已向" + targetName + "转帐" + amount + "金币，当前余额为" + balance);
        Player targetPlayer = Bukkit.getPlayer(to);
        if (targetPlayer != null && targetPlayer.isOnline()) {
            targetPlayer.sendMessage(PREFIX + "收到来自" + player.getName() + "的" + amount + "金币");
        }
    }

    /** 去掉玩家名前面可能带的 @ */
    private static String stripAt(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        return trimmed.startsWith("@") ? trimmed.substring(1).trim() : trimmed;
    }
}
