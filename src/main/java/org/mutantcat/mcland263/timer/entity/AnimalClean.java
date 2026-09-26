package org.mutantcat.mcland263.timer.entity;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.ArrayList;
import java.util.List;

/**
 * Author: tyza66
 * Date: 2024/4/21 19:01
 * Github: https://github.com/tyza66
 **/

public class AnimalClean extends BukkitRunnable {
    private final JavaPlugin plugin; // 插件实例，用于调度倒计时任务
    private final int countdownTime; // 倒计时时间（秒）
    private final List<BukkitRunnable> pendingTasks = new ArrayList<>(); // 延迟中的任务，插件卸载时统一取消

    public AnimalClean(JavaPlugin plugin, int countdownSeconds) {
        this.plugin = plugin;
        // 至少 1 秒，避免配置为 0 或负数时出现异常倒计时
        this.countdownTime = Math.max(1, countdownSeconds);
    }

    @Override
    public void run() {
        // 用主线程定时任务分次发出倒计时提示，避免在主线程 sleep 阻塞服务器
        for (int i = countdownTime; i > 0; i--) {
            final int secondsLeft = i;
            BukkitRunnable announcer = new BukkitRunnable() {
                @Override
                public void run() {
                    pendingTasks.remove(this);
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.sendMessage("[生物清理]注意：全世界生物将在 " + secondsLeft + " 秒后被清除！");
                    }
                }
            };
            pendingTasks.add(announcer);
            announcer.runTaskLater(plugin, (countdownTime - secondsLeft) * 20L);
        }
        // 倒计时结束后移除所有非玩家实体
        BukkitRunnable cleanup = new BukkitRunnable() {
            @Override
            public void run() {
                pendingTasks.remove(this);
                Bukkit.getWorlds().forEach(world -> {
                    for (Entity entity : world.getEntities()) {
                        if (entity.getType() != EntityType.PLAYER) {
                            entity.remove();
                        }
                    }
                });
                Bukkit.broadcastMessage("[生物清理]生物已被清除。");
            }
        };
        pendingTasks.add(cleanup);
        cleanup.runTaskLater(plugin, countdownTime * 20L);
    }

    // 插件卸载/重载时取消延迟中的通告和清理，避免关闭后残留任务仍会广播或清物
    public void close() {
        for (BukkitRunnable task : pendingTasks) {
            task.cancel();
        }
        pendingTasks.clear();
    }
}
