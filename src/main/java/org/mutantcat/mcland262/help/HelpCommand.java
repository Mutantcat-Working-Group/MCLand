package org.mutantcat.mcland262.help;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class HelpCommand implements CommandExecutor {
    private static final String PREFIX = "[MCLand]";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sendHelp(sender);
        return true;
    }

    public static void sendHelp(CommandSender sender) {
        sender.sendMessage(PREFIX + "========== 方块猫窝帮助 ==========");
        sender.sendMessage(PREFIX + "/register 密码 确认密码 - 注册当前用户名的密码");
        sender.sendMessage(PREFIX + "/login 密码 - 登录当前用户名");
        sender.sendMessage(PREFIX + "/password 密码 确认密码 - 更新密码");
        sender.sendMessage(PREFIX + "/tp 玩家名 - 向对方发送传送请求，对方 /accept 接受");
        sender.sendMessage(PREFIX + "/accept - 接受收到的传送请求");
        sender.sendMessage(PREFIX + "主城保护 - 保护主城范围内的方块和互动");
        sender.sendMessage(PREFIX + "死亡不掉落 - 死亡时保留背包，不产生掉落物");
        sender.sendMessage(PREFIX + "掉落物清理 - 每15分钟清理一次，清理前有倒计时提示");
        sender.sendMessage(PREFIX + "生物清理 - 每小时清理一次，清理前有倒计时提示，村民不会被清理");
    }
}
