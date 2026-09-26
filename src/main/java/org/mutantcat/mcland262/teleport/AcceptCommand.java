package org.mutantcat.mcland262.teleport;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AcceptCommand implements CommandExecutor {
    private static final String PREFIX = "[TP]";

    private final TeleportRequestManager manager;

    public AcceptCommand(TeleportRequestManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(PREFIX + "该命令仅限玩家使用");
            return true;
        }
        if (args.length != 0) {
            sender.sendMessage(PREFIX + "请输入“/accept”接受传送请求");
            return true;
        }
        manager.accept((Player) sender);
        return true;
    }
}
