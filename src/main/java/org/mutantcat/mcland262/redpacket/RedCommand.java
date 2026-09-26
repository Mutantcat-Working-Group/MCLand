package org.mutantcat.mcland262.redpacket;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /red 抢整点红包：逻辑都在 RedPacketManager，这里只负责转交和控制台报错。
 */
public class RedCommand implements CommandExecutor {
    private static final String PREFIX = "[红包]";

    private final RedPacketManager manager;

    public RedCommand(RedPacketManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(PREFIX + "该命令仅限玩家使用");
            return true;
        }
        manager.grab((Player) sender);
        return true;
    }
}
