package org.mutantcat.mcland262.land;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.mutantcat.mcland262.user.AuthManager;

import java.util.Locale;

/**
 * /land 圈地命令入口：裸命令开始或重绘选区，sum 看区域和价格，sure 购买，off 取消，whitelist 管白名单。
 */
public class LandCommand implements CommandExecutor {
    private static final String PREFIX = "[圈地]";

    private final LandManager landManager;
    private final AuthManager authManager;

    public LandCommand(LandManager landManager, AuthManager authManager) {
        this.landManager = landManager;
        this.authManager = authManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(PREFIX + "该命令仅限玩家使用");
            return true;
        }
        Player player = (Player) sender;
        if (authManager != null && !authManager.isLoggedIn(player.getUniqueId())) {
            player.sendMessage(PREFIX + "请先登录后使用圈地功能");
            return true;
        }
        if (args.length == 0) {
            landManager.startOrRedraw(player);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "sum" -> landManager.showSum(player);
            case "sure" -> landManager.purchase(player);
            case "off" -> landManager.off(player);
            case "whitelist" -> landManager.handleWhitelist(player, args);
            default -> player.sendMessage(PREFIX + "未知用法。/land 开始圈地；“/land sum”查看区域与价格；"
                    + "“/land sure”购买；“/land off”取消；“/land whitelist add 玩家名”添加白名单");
        }
        return true;
    }
}
