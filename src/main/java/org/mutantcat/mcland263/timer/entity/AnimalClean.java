package org.mutantcat.mcland263.timer.entity;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.mutantcat.mcland263.timer.CountdownCleanTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Author: tyza66
 * Date: 2024/4/21 19:01
 * Github: https://github.com/tyza66
 **/

/**
 * 定时清理实体：玩家永不清理；村民（含流浪商人）永不清理；
 * 可繁殖动物按「世界 + 网格 + 类型」模糊分桶，每种动物每格最多保留 maxPerType 只，超出部分移除，不会赶尽杀绝；
 * 其余非玩家实体全部清除。
 */
public class AnimalClean extends CountdownCleanTask {
    private final int gridSize;   // 模糊网格边长（格），按 floorDiv 把坐标归入格子
    private final int maxPerType; // 每个网格内同种动物的上限

    public AnimalClean(JavaPlugin plugin, int gridSize, int maxPerType) {
        super(plugin);
        this.gridSize = Math.max(1, gridSize);
        this.maxPerType = Math.max(1, maxPerType);
    }

    @Override
    protected void performClean() {
        // 只遍历已加载区块，避免整世界实体快照；限额用整数网格分桶，不做实体间距离计算，
        // 资源消耗最小（相邻格边界附近可能略有偏差，属模糊控制的预期行为）
        Map<AnimalCell, Integer> counts = new HashMap<>();
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                for (Entity entity : chunk.getEntities()) {
                    if (entity instanceof Player) {
                        continue; // 玩家永不清理
                    }
                    if (entity instanceof AbstractVillager) {
                        continue; // 村民（含流浪商人）永不清理
                    }
                    if (entity instanceof Animals) {
                        // 同格同种动物只保留前 maxPerType 只，超出部分移除
                        Location location = entity.getLocation();
                        AnimalCell cell = new AnimalCell(world.getUID(),
                                Math.floorDiv(location.getBlockX(), gridSize),
                                Math.floorDiv(location.getBlockZ(), gridSize),
                                entity.getType());
                        if (counts.merge(cell, 1, Integer::sum) > maxPerType) {
                            entity.remove();
                        }
                        continue;
                    }
                    entity.remove(); // 其余实体全部清除
                }
            }
        }
    }

    @Override
    protected String countdownMessage(int secondsLeft) {
        return "[生物清理]距离下次生物清理还差" + secondsLeft + "秒，请注意照看好动物";
    }

    @Override
    protected String finishedMessage() {
        return "[生物清理]生物已被清除。";
    }

    /** 动物限额分桶键：世界 + 网格坐标 + 动物类型 */
    private record AnimalCell(UUID worldUid, int cellX, int cellZ, org.bukkit.entity.EntityType type) {
    }
}
