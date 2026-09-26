package org.mutantcat.mcland262.user;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AuthCommand implements CommandExecutor {
    private static final String PREFIX = "[MCLand]";

    private final AuthManager authManager;

    public AuthCommand(AuthManager authManager) {
        this.authManager = authManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(PREFIX + "该命令仅限玩家使用");
            return true;
        }
        Player player = (Player) sender;
        switch (command.getName().toLowerCase()) {
            case "register":
                register(player, args);
                return true;
            case "login":
                login(player, args);
                return true;
            case "password":
                password(player, args);
                return true;
            default:
                return false;
        }
    }

    private void register(Player player, String[] args) {
        if (authManager.isRegistered(player.getUniqueId())) {
            player.sendMessage(PREFIX + "当前用户名的账号已注册，请输入“/login 密码”登录");
            return;
        }
        if (args.length != 2 || args[0].isEmpty() || !args[0].equals(args[1])) {
            player.sendMessage(PREFIX + "请输入“/register 密码 确认密码”注册当前用户名的密码，两次输入需一致");
            return;
        }
        if (authManager.register(player.getUniqueId(), player.getName(), args[0])) {
            player.sendMessage(PREFIX + "注册成功，欢迎来到方块猫窝！");
            player.sendMessage(PREFIX + "如需修改密码，请输入“/password 密码 确认密码”");
        } else {
            player.sendMessage(PREFIX + "注册失败，请联系管理员检查服务器日志");
        }
    }

    private void login(Player player, String[] args) {
        if (!authManager.isRegistered(player.getUniqueId())) {
            player.sendMessage(PREFIX + "当前用户名的账号尚未注册，请输入“/register 密码 确认密码”注册");
            return;
        }
        if (authManager.isLoggedIn(player.getUniqueId())) {
            player.sendMessage(PREFIX + "你已经登录了");
            return;
        }
        if (args.length != 1 || args[0].isEmpty()) {
            player.sendMessage(PREFIX + "请输入“/login 密码”登录当前用户名的账号");
            return;
        }
        if (authManager.login(player.getUniqueId(), args[0])) {
            player.sendMessage(PREFIX + "登录成功，欢迎回来！");
            player.sendMessage(PREFIX + "如需修改密码，请输入“/password 密码 确认密码”");
        } else {
            player.sendMessage(PREFIX + "密码错误，请重新输入“/login 密码”");
        }
    }

    private void password(Player player, String[] args) {
        if (!authManager.isRegistered(player.getUniqueId())) {
            player.sendMessage(PREFIX + "当前用户名的账号尚未注册，请输入“/register 密码 确认密码”注册");
            return;
        }
        if (!authManager.isLoggedIn(player.getUniqueId())) {
            player.sendMessage(PREFIX + "请先登录后修改密码，请输入“/login 密码”");
            return;
        }
        if (args.length != 2 || args[0].isEmpty() || !args[0].equals(args[1])) {
            player.sendMessage(PREFIX + "请输入“/password 密码 确认密码”更新密码，两次输入需一致");
            return;
        }
        if (authManager.updatePassword(player.getUniqueId(), args[0])) {
            player.sendMessage(PREFIX + "密码已更新");
        } else {
            player.sendMessage(PREFIX + "密码更新失败，请联系管理员检查服务器日志");
        }
    }
}
