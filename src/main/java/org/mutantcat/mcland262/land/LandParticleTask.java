package org.mutantcat.mcland262.land;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 圈地选区的粒子预览：只发给正在选点的那一个玩家，其他人看不见。
 * 只有第一点时画十字标记；两点齐了之后按玩家所在高度和上一格画矩形边框。
 * 只渲染玩家附近的那段边框（视野距离截断），步长 2 格，每 10 tick 刷新一次，开销可控。
 */
public class LandParticleTask extends BukkitRunnable {
    private static final Particle PARTICLE = Particle.DUST;
    private static final int VIEW_DISTANCE = 48;
    private static final int STEP = 2;
    /** 第一个点用暖黄色标记，边框用绿色区分主城的蓝色能量墙 */
    private final Particle.DustOptions firstOptions = new Particle.DustOptions(Color.fromRGB(255, 215, 90), 1.1f);
    private final Particle.DustOptions edgeOptions = new Particle.DustOptions(Color.fromRGB(90, 235, 130), 1.0f);

    private final LandManager manager;
    private final Player player;

    public LandParticleTask(LandManager manager, Player player) {
        this.manager = manager;
        this.player = player;
    }

    @Override
    public void run() {
        if (!player.isOnline()) {
            cancel();
            return;
        }
        LandManager.Selection selection = manager.selectionOf(player.getUniqueId());
        if (selection == null || selection.first == null || selection.first.getWorld() == null) {
            cancel();
            return;
        }
        // 选点在别的世界（地狱/末地/主世界来回换）时不在当前世界乱画旧坐标，回来自动恢复
        if (!selection.first.getWorld().equals(player.getLocation().getWorld())) {
            return;
        }
        renderFirst(selection.first);
        if (selection.second != null) {
            renderRectangle(selection.first, selection.second);
        }
    }

    /** 第一个点：竖三粒加左右各一粒，站远了也认得出 */
    private void renderFirst(Location first) {
        double x = first.getBlockX() + 0.5;
        double y = first.getBlockY() + 0.5;
        double z = first.getBlockZ() + 0.5;
        player.spawnParticle(PARTICLE, x, y, z, 1, 0.0, 0.0, 0.0, 0.0, firstOptions);
        player.spawnParticle(PARTICLE, x, y + 1.0, z, 1, 0.0, 0.0, 0.0, 0.0, firstOptions);
        player.spawnParticle(PARTICLE, x, y + 2.0, z, 1, 0.0, 0.0, 0.0, 0.0, firstOptions);
        player.spawnParticle(PARTICLE, x + 0.6, y, z, 1, 0.0, 0.0, 0.0, 0.0, firstOptions);
        player.spawnParticle(PARTICLE, x - 0.6, y, z, 1, 0.0, 0.0, 0.0, 0.0, firstOptions);
    }

    /** 矩形边框：以玩家当前高度为中线画上下两圈，走的是方块中心线 */
    private void renderRectangle(Location first, Location second) {
        if (first.getWorld() == null || !first.getWorld().equals(second.getWorld())) {
            return;
        }
        int minX = Math.min(first.getBlockX(), second.getBlockX());
        int maxX = Math.max(first.getBlockX(), second.getBlockX());
        int minZ = Math.min(first.getBlockZ(), second.getBlockZ());
        int maxZ = Math.max(first.getBlockZ(), second.getBlockZ());
        double baseY = player.getLocation().getY();
        double[] heights = {Math.max(0.0, baseY), Math.max(0.0, baseY + 2.0)};
        for (double y : heights) {
            // 先走 z 固定的两条边，再走 x 固定的两条边，角点重复无妨
            for (int x = minX; x <= maxX; x += STEP) {
                emit(x, y, minZ);
                emit(x, y, maxZ);
            }
            for (int z = minZ; z <= maxZ; z += STEP) {
                emit(minX, y, z);
                emit(maxX, y, z);
            }
            // 步长取整时容易漏掉尾巴那一列/行，补齐四角
            emit(maxX, y, minZ);
            emit(maxX, y, maxZ);
            emit(minX, y, minZ);
            emit(minX, y, maxZ);
        }
    }

    /** 只渲染玩家附近的一段边框，远处的等走近了再出现 */
    private void emit(double x, double y, double z) {
        Location playerLocation = player.getLocation();
        if (Math.abs(playerLocation.getX() - x) > VIEW_DISTANCE
                || Math.abs(playerLocation.getZ() - z) > VIEW_DISTANCE) {
            return;
        }
        player.spawnParticle(PARTICLE, x + 0.5, y, z + 0.5, 1, 0.0, 0.0, 0.0, 0.0, edgeOptions);
    }
}
