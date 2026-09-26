package org.mutantcat.mcland262.home;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.Locale;

/**
 * /sethome 设置 home，/home 返回 home。
 * 两个命令共用一个执行器，按命令名分发；未登录玩家已被登录模块统一拦截，这里不重复鉴权。
 */
public class HomeCommand implements CommandExecutor {
    private static final String PREFIX = "[MCLand]";

    private final HomeManager manager;

    public HomeCommand(HomeManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(PREFIX + "该命令仅限玩家使用");
            return true;
        }
        Player player = (Player) sender;

        if (args.length > 0) {
            sender.sendMessage(PREFIX + "该命令无需额外参数，请输入“/" + command.getName() + "”");
            return true;
        }

        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "sethome":
                setHome(player);
                break;
            case "home":
                goHome(player);
                break;
            default:
                break;
        }
        return true;
    }

    private void setHome(Player player) {
        Location location = player.getLocation();
        try {
            manager.setHome(player.getUniqueId(), location);
            player.sendMessage(PREFIX + "home 已设置，输入“/home”可回到这里");
        } catch (SQLException e) {
            player.sendMessage(PREFIX + "home 设置失败，请稍后再试");
        }
    }

    private void goHome(Player player) {
        Location home = manager.getHome(player.getUniqueId());
        if (home == null) {
            player.sendMessage(PREFIX + "你还没有设置 home，请输入“/sethome”设置");
            return;
        }
        if (player.teleport(home)) {
            player.sendMessage(PREFIX + "已回到 home");
        } else {
            player.sendMessage(PREFIX + "返回 home 失败，请稍后再试");
        }
    }
}
