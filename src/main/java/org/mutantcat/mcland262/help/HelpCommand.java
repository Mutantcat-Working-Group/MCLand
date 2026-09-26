package org.mutantcat.mcland262.help;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class HelpCommand implements CommandExecutor {
    private static final String PREFIX = "[MCLand]";

    /**
     * 帮助文案里引用的各项数值，全部由配置注入。
     * 文案不再写死数字，配置改了帮助菜单跟着变，不会出现“写着每15分钟、其实配成了半小时”。
     */
    public record Info(long checkinRewardCoins, long landPricePerBlock, int landMinSide,
                       long itemCleanIntervalSeconds, long animalCleanIntervalSeconds,
                       int tpRequestTimeoutSeconds, long startingBalance) {
    }

    private final Info info;

    public HelpCommand(Info info) {
        this.info = info;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sendHelp(sender);
        return true;
    }

    public void sendHelp(CommandSender sender) {
        sender.sendMessage(PREFIX + "========== 方块猫窝帮助 ==========");
        sender.sendMessage(PREFIX + "/register 密码 确认密码 - 注册当前用户名的密码");
        sender.sendMessage(PREFIX + "/login 密码 - 登录当前用户名");
        sender.sendMessage(PREFIX + "/password 密码 确认密码 - 更新密码");
        sender.sendMessage(PREFIX + "/tp 玩家名 - 向对方发送传送请求，对方 /accept 接受");
        sender.sendMessage(PREFIX + "/accept - 接受收到的传送请求");
        sender.sendMessage(PREFIX + "/money - 查看金币余额");
        sender.sendMessage(PREFIX + "/money give 数量 玩家名 - 转帐金币给他人，对方可不在线");
        sender.sendMessage(PREFIX + "/money request 数量 玩家名 - 向在线玩家索要金币，对方在" + info.tpRequestTimeoutSeconds() + "秒内/accept且余额充足后到账");
        sender.sendMessage(PREFIX + "/sell - 查看可出售物品与单价");
        sender.sendMessage(PREFIX + "/sell sum - 计算当前手中物品总价");
        sender.sendMessage(PREFIX + "/sell sure - 卖掉当前手中的物品换取金币，卖出后无法赎回");
        sender.sendMessage(PREFIX + "/red - 抢整点红包，奇数整点发放，每人每轮一次");
        sender.sendMessage(PREFIX + "/check - 每日签到，每个账号每天一次，奖励" + info.checkinRewardCoins() + "金币");
        sender.sendMessage(PREFIX + "/spawn - 返回主城出生点");
        sender.sendMessage(PREFIX + "/sethome - 把当前位置设为 home");
        sender.sendMessage(PREFIX + "/home - 返回自己设置的 home");
        sender.sendMessage(PREFIX + "/land - 木铲右键选点圈地：先选第一个点，输 /land 确认后选第二个点（可更换）");
        sender.sendMessage(PREFIX + "/land sum - 查看所选区域的两点坐标、大小、价格与当前余额");
        sender.sendMessage(PREFIX + "/land sure - 确认购买所选区域，扣除对应金币");
        sender.sendMessage(PREFIX + "/land off - 取消本次圈地并隐藏粒子效果");
        sender.sendMessage(PREFIX + "/land whitelist add 玩家名 - 把玩家加入自己领地的白名单（remove 移出，list 查看）");
        sender.sendMessage(PREFIX + "主城保护 - 以出生点为中心的正方形区域，普通玩家不能放置和破坏方块，怪物、爆炸和环境也改不动这里的地形，管理员不受限制");
        sender.sendMessage(PREFIX + "能量墙 - 主城边界有蓝色半透明能量墙标示范围");
        sender.sendMessage(PREFIX + "怪物秒杀 - 怪物进入主城范围会被直接击杀，且不掉落物品");
        sender.sendMessage(PREFIX + "死亡不掉落 - 死亡时保留背包，不产生掉落物");
        sender.sendMessage(PREFIX + "金币系统 - 金币与账号绑定，新注册默认" + info.startingBalance() + "，仅用于玩家间转账与索要");
        sender.sendMessage(PREFIX + "每日签到 - 每个账号每天可/check签到一次，奖励" + info.checkinRewardCoins() + "金币");
        sender.sendMessage(PREFIX + "掉落物清理 - 每" + duration(info.itemCleanIntervalSeconds()) + "清理一次，清理前有倒计时提示");
        sender.sendMessage(PREFIX + "生物清理 - 每" + duration(info.animalCleanIntervalSeconds()) + "清理一次，清理前有倒计时提示，村民不会被清理");
        sender.sendMessage(PREFIX + "圈地系统 - 木铲两点圈地（每格" + info.landPricePerBlock() + "金币，至少"
                + info.landMinSide() + "x" + info.landMinSide() + "），领地垂直全范围；领地内只有主人和白名单玩家可以建造、破坏、开箱、操作门和按钮，地形对外力完全免疫；白名单用 /land whitelist 管理");
    }

    /** 秒数转中文时长：整小时显示小时、整分显示分钟，其余回落秒 */
    private static String duration(long seconds) {
        if (seconds <= 0) {
            return "0秒";
        }
        if (seconds % 3600 == 0) {
            return (seconds / 3600) + "小时";
        }
        if (seconds % 60 == 0) {
            return (seconds / 60) + "分钟";
        }
        return seconds + "秒";
    }
}
