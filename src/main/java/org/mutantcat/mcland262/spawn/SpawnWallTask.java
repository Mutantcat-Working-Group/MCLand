package org.mutantcat.mcland262.spawn;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 主城边界能量墙：把保护区域的正方形边界渲染成蓝色半透明粒子墙。
 *
 * 性能取舍：按玩家逐一渲染，只发该玩家附近的那段边界（天然不重复、不打扰无关玩家）；
 * 边界按水平步长、纵向步长采样，避免逐格堆叠导致客户端和发包压力过大。
 */
public class SpawnWallTask extends BukkitRunnable {
    private static final Particle WALL_PARTICLE = Particle.DUST;
    /** 粒子大小越小越通透，颜色取蓝色以对应“能量墙”观感 */
    private final Particle.DustOptions dustOptions = new Particle.DustOptions(Color.fromRGB(70, 150, 255), 0.9f);

    private final SpawnRegion region; // 保护区域，边界由它定义
    private final int viewDistance;   // 水平可视距离（格），只渲染玩家附近这段边界
    private final int horizontalStep; // 边界水平采样步长（格）
    private final int verticalRange;  // 以玩家所在高度为中线向上下延伸的格数
    private final int verticalStep;   // 纵向采样步长（格）

    public SpawnWallTask(SpawnRegion region, int viewDistance, int horizontalStep,
                         int verticalRange, int verticalStep) {
        this.region = region;
        this.viewDistance = Math.max(4, viewDistance);
        this.horizontalStep = Math.max(1, horizontalStep);
        this.verticalRange = Math.max(1, verticalRange);
        this.verticalStep = Math.max(1, verticalStep);
    }

    @Override
    public void run() {
        World world = region.getWorld();
        if (world == null) {
            return;
        }
        // 四条边界对所有玩家是同一套，提到循环外只算一次
        Location center = region.getCenter();
        double radius = region.getRadius();
        Bounds bounds = new Bounds(
                center.getX() - radius, center.getX() + radius,
                center.getZ() - radius, center.getZ() + radius,
                world.getLogicalHeight());
        // 只遍历保护世界内的玩家，其他世界一个粒子都不发
        for (Player player : world.getPlayers()) {
            renderFor(player, bounds);
        }
    }

    private void renderFor(Player player, Bounds bounds) {
        Location playerLocation = player.getLocation();
        double fromY = Math.max(0.0, playerLocation.getY() - verticalRange);
        double toY = Math.min((double) bounds.heightCeiling(), playerLocation.getY() + verticalRange);

        // 先走 z 固定的两条边，再走 x 固定的两条边，跳过已被走过的下边界起点，避免角点重复
        for (double x = bounds.minX(); x <= bounds.maxX(); x += horizontalStep) {
            emitColumn(player, playerLocation, x, bounds.minZ(), fromY, toY);
            emitColumn(player, playerLocation, x, bounds.maxZ(), fromY, toY);
        }
        for (double z = bounds.minZ() + horizontalStep; z < bounds.maxZ(); z += horizontalStep) {
            emitColumn(player, playerLocation, bounds.minX(), z, fromY, toY);
            emitColumn(player, playerLocation, bounds.maxX(), z, fromY, toY);
        }
    }

    /** 在世界高度上限内按纵向步长渲染一列粒子 */
    private void emitColumn(Player player, Location playerLocation, double x, double z, double fromY, double toY) {
        // 只渲染玩家附近的一段边界，远处直接跳过
        if (Math.abs(playerLocation.getX() - x) > viewDistance
                || Math.abs(playerLocation.getZ() - z) > viewDistance) {
            return;
        }
        for (double y = fromY; y <= toY; y += verticalStep) {
            player.spawnParticle(WALL_PARTICLE, x, y, z, 1, 0.0, 0.0, 0.0, 0.0, dustOptions);
        }
    }

    /** 正方形边界的四条坐标与世界高度上限 */
    private record Bounds(double minX, double maxX, double minZ, double maxZ, int heightCeiling) {
    }
}
