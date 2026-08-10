package org.mutantcat.mcland110.event.block;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
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
    private final long radiusSquared;   // 半径平方，避免每次计算开方

    public SpawnProtectionListener(World world, int radius) {
        this.spawnWorld = world;
        this.spawnLocation = world.getSpawnLocation();
        this.protectionRadius = Math.max(0, radius);
        this.radiusSquared = (long) this.protectionRadius * this.protectionRadius;
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
        // 空位置或非保护世界直接返回 false，避免 NPE
        if (location == null || location.getWorld() == null || spawnWorld == null) {
            return false;
        }
        if (!location.getWorld().equals(spawnWorld)) {
            return false;
        }
        // 按水平距离（忽略 Y 轴）判断，高空/地下的方块不再被误判为受保护
        double dx = location.getX() - spawnLocation.getX();
        double dz = location.getZ() - spawnLocation.getZ();
        return dx * dx + dz * dz <= radiusSquared;
    }
}
