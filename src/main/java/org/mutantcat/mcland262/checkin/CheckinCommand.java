package org.mutantcat.mcland262.checkin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.mutantcat.mcland262.user.AuthManager;
import org.mutantcat.mcland262.user.UserDatabase;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.logging.Level;

/**
 * /check 每日签到：每个账号每天只能签到一次，奖励入账到账号余额（金币跟账号绑定）。
 * 未登录玩家本来就被 AuthListener 拦住，这里仍显式判一次，
 * 免得命令被权限或其他插件放行时少了提示。
 */
public class CheckinCommand implements CommandExecutor {
    private static final String PREFIX = "[签到]";

    private final JavaPlugin plugin;
    private final AuthManager authManager;
    private final UserDatabase userDatabase;
    private final long reward;

    public CheckinCommand(JavaPlugin plugin, AuthManager authManager, UserDatabase userDatabase, long reward) {
        this.plugin = plugin;
        this.authManager = authManager;
        this.userDatabase = userDatabase;
        this.reward = reward;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(PREFIX + "该命令仅限玩家使用");
            return true;
        }
        Player player = (Player) sender;
        if (authManager != null && !authManager.isLoggedIn(player.getUniqueId())) {
            player.sendMessage(PREFIX + "请先登录后签到");
            return true;
        }
        if (userDatabase == null) {
            player.sendMessage(PREFIX + "账号系统未启用，签到不可用");
            return true;
        }

        long today = LocalDate.now().toEpochDay();
        long balance;
        try {
            balance = userDatabase.dailyCheckIn(player.getUniqueId(), today, reward);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "每日签到写入失败", e);
            player.sendMessage(PREFIX + "签到失败，请联系管理员检查服务器日志");
            return true;
        }
        if (balance == -1L) {
            player.sendMessage(PREFIX + "尚未查询到账号，请先注册登录");
        } else if (balance == -2L) {
            player.sendMessage(PREFIX + "今天已经签到过了，明天再来吧");
        } else if (balance < 0L) {
            plugin.getLogger().warning("签到奖励数额非法（" + reward + "），已跳过发放");
            player.sendMessage(PREFIX + "签到失败，请联系管理员检查服务器日志");
        } else {
            player.sendMessage(PREFIX + "签到成功，获得" + reward + "金币，当前余额为" + balance + "金币");
        }
        return true;
    }
}
