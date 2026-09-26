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
        sender.sendMessage(PREFIX + "/money - 查看金币余额");
        sender.sendMessage(PREFIX + "/money send 数量 玩家名 - 转帐金币给他人，对方可不在线");
        sender.sendMessage(PREFIX + "/request 数量 玩家名 - 向在线玩家索要金币，对方在30秒内/accept且余额充足后到账");
        sender.sendMessage(PREFIX + "/spawn - 返回主城出生点");
        sender.sendMessage(PREFIX + "/sethome - 把当前位置设为 home");
        sender.sendMessage(PREFIX + "/home - 返回自己设置的 home");
        sender.sendMessage(PREFIX + "主城保护 - 以出生点为中心的正方形区域，普通玩家不能放置和破坏方块，怪物、爆炸和环境也改不动这里的地形，管理员不受限制");
        sender.sendMessage(PREFIX + "能量墙 - 主城边界有蓝色半透明能量墙标示范围");
        sender.sendMessage(PREFIX + "怪物秒杀 - 怪物进入主城范围会被直接击杀，且不掉落物品");
        sender.sendMessage(PREFIX + "死亡不掉落 - 死亡时保留背包，不产生掉落物");
        sender.sendMessage(PREFIX + "金币系统 - 金币与账号绑定，新注册默认500，仅用于玩家间转账与索要");
        sender.sendMessage(PREFIX + "掉落物清理 - 每15分钟清理一次，清理前有倒计时提示");
        sender.sendMessage(PREFIX + "生物清理 - 每小时清理一次，清理前有倒计时提示，村民不会被清理");
    }
}
