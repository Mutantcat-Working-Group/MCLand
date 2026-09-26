package org.mutantcat.mcland262.spawn;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * 主城保护区域：以出生点为中心的正方形，按水平距离计算（忽略 Y 轴）。
 * 区域同时服务于放置/破坏拦截、边界能量墙渲染和怪物秒杀判定，三处共用同一套边界定义。
 */
public class SpawnRegion {
    private final World world;       // 区域所在世界
    private final Location center;   // 区域中心（出生点）
    private final int radius;        // 正方形半宽（格）

    public SpawnRegion(World world, int radius) {
        this.world = world;
        this.center = world.getSpawnLocation();
        this.radius = Math.max(0, radius);
    }

    public World getWorld() {
        return world;
    }

    /** 区域中心副本，避免外部直接持有内部 Location 被改写 */
    public Location getCenter() {
        return center.clone();
    }

    public int getRadius() {
        return radius;
    }

    /** 位置是否落在保护区域内；空位置或跨世界直接 false，避免 NPE */
    public boolean contains(Location location) {
        if (location == null || location.getWorld() == null || world == null) {
            return false;
        }
        if (!location.getWorld().equals(world)) {
            return false;
        }
        // 正方形判定：水平两个方向都不超过半宽即为区域内
        return Math.abs(location.getX() - center.getX()) <= radius
                && Math.abs(location.getZ() - center.getZ()) <= radius;
    }
}
