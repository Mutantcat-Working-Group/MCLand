package org.mutantcat.mcland262.spawn;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /spawn 返回主城出生点 */
public class SpawnCommand implements CommandExecutor {
    private static final String PREFIX = "[MCLand]";

    private final SpawnRegion region;

    public SpawnCommand(SpawnRegion region) {
        this.region = region;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(PREFIX + "该命令仅限玩家使用");
            return true;
        }
        Player player = (Player) sender;

        World world = region.getWorld();
        if (world == null) {
            player.sendMessage(PREFIX + "主城世界不存在，无法返回主城");
            return true;
        }

        Location spawn = world.getSpawnLocation();
        if (player.teleport(spawn)) {
            player.sendMessage(PREFIX + "已返回主城");
        } else {
            player.sendMessage(PREFIX + "返回主城失败，请稍后再试");
        }
        return true;
    }
}
