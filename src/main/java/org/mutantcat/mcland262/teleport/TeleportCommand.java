package org.mutantcat.mcland262.teleport;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TeleportCommand implements CommandExecutor {
    private static final String PREFIX = "[TP]";

    private final TeleportRequestManager manager;

    public TeleportCommand(TeleportRequestManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(PREFIX + "该命令仅限玩家使用");
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(PREFIX + "请输入“/tp 玩家名”发送传送请求");
            return true;
        }

        manager.handleTpCommand((Player) sender, args);
        return true;
    }
}
