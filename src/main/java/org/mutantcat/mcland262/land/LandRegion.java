package org.mutantcat.mcland262.land;

/**
 * 一块领地：水平矩形 + 垂直全范围（不卡 Y 轴，地下到天上整根都属于领主）。
 * 坐标一律用方块坐标（int），世界名存字符串，避免世界被卸载后持有失效引用。
 */
public record LandRegion(long id, String ownerUuid, String ownerName, String world,
                         int minX, int maxX, int minZ, int maxZ) {

    /** 水平坐标是否落在矩形内；世界不同直接 false */
    public boolean contains(String worldName, int x, int z) {
        if (worldName == null || !worldName.equals(world)) {
            return false;
        }
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public int width() {
        return maxX - minX + 1;
    }

    public int depth() {
        return maxZ - minZ + 1;
    }

    /** 是否与另一块领地水平交叠；共边不算交叠，共格才算 */
    public boolean overlaps(LandRegion other) {
        return other != null
                && world.equals(other.world())
                && minX <= other.maxX() && other.minX() <= maxX
                && minZ <= other.maxZ() && other.minZ() <= maxZ;
    }

    /** 是否与给定水平矩形交叠，规则同上 */
    public boolean overlaps(String worldName, int otherMinX, int otherMaxX, int otherMinZ, int otherMaxZ) {
        return worldName != null && worldName.equals(world)
                && minX <= otherMaxX && otherMinX <= maxX
                && minZ <= otherMaxZ && otherMinZ <= maxZ;
    }
}
