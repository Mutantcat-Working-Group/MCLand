package org.mutantcat.mcland121.event.block;

import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Author: tyza66
 * Date: 2024/4/21 19:12
 * Github: https://github.com/tyza66
 **/

public class SpawnProtectionListener implements Listener {
    private final World spawnWorld; // 出生点所在的世界
    private final Location spawnLocation; // 出生点的位置
    private final int protectionRadius; // 保护区域的半径

    public SpawnProtectionListener(World world, int radius) {
        this.spawnWorld = world;
        this.spawnLocation = world.getSpawnLocation();
        this.protectionRadius = radius;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (isInProtectedArea(event.getBlock().getLocation())) {
            // 取消事件，阻止方块被破坏
            event.getPlayer().sendMessage("这个区域受到保护，无法破坏方块。");
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isInProtectedArea(event.getBlock().getLocation())) {
            // 取消事件，阻止方块被放置
            event.getPlayer().sendMessage("这个区域受到保护，无法放置方块。");
            event.setCancelled(true);
        }
    }

    private boolean isInProtectedArea(Location location) {
        // 检查位置是否在保护区域内
        return location.getWorld().equals(spawnWorld) && location.distance(spawnLocation) <= protectionRadius;
    }
}
