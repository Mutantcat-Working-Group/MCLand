package org.mutantcat.mcland110;

import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.mutantcat.mcland110.event.block.SpawnProtectionListener;
import org.mutantcat.mcland110.event.player.NoDropOnDeathEvent;
import org.mutantcat.mcland110.event.player.PlayerJoinedEvent;
import org.mutantcat.mcland110.timer.entity.AnimalClean;
import org.mutantcat.mcland110.timer.entity.EntityClean;

import java.util.ArrayList;
import java.util.List;

public class Main extends JavaPlugin {
    // 记录注册的定时任务，便于卸载时统一取消
    private final List<BukkitTask> tasks = new ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getLogger().info("Mutantcat Land 110 插件已启用!");

        // 注册事件
        getServer().getPluginManager().registerEvents(
                new PlayerJoinedEvent(getConfig().getString("welcome-message", "欢迎来到Mutantcat Land！")), this);
        getServer().getPluginManager().registerEvents(new NoDropOnDeathEvent(), this);

        // 主城保护（世界不存在时跳过并告警，避免 NPE）
        String worldName = getConfig().getString("spawn-protection.world", "world");
        World spawnWorld = getServer().getWorld(worldName);
        if (spawnWorld != null) {
            int radius = getConfig().getInt("spawn-protection.radius", 20);
            getServer().getPluginManager().registerEvents(new SpawnProtectionListener(spawnWorld, radius), this);
        } else {
            getLogger().warning("未找到世界 \"" + worldName + "\"，主城保护未启用。");
        }

        // 定时清理掉落物（interval-seconds 换算为 ticks，20 ticks = 1 秒；保底 1 秒避免 period 为 0 导致每 tick 触发）
        long itemInterval = Math.max(1L, getConfig().getLong("item-clean.interval-seconds", 1800)) * 20L;
        int itemCountdown = getConfig().getInt("item-clean.countdown-seconds", 5);
        EntityClean entityClean = new EntityClean(this, itemCountdown);
        tasks.add(entityClean.runTaskTimer(this, 0L, itemInterval));

        // 定时清理生物
        long animalInterval = Math.max(1L, getConfig().getLong("animal-clean.interval-seconds", 3600)) * 20L;
        int animalCountdown = getConfig().getInt("animal-clean.countdown-seconds", 5);
        AnimalClean animalClean = new AnimalClean(this, animalCountdown);
        tasks.add(animalClean.runTaskTimer(this, 0L, animalInterval));
    }

    @Override
    public void onDisable() {
        // 取消所有定时任务，避免卸载时残留
        for (BukkitTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
        getLogger().info("Mutantcat Land 110 服务器正在关闭!");
    }
}
