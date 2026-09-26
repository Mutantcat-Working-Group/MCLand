package org.mutantcat.mcland263.timer.entity;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.ArrayList;
import java.util.List;

/**
 * Author: tyza66
 * Date: 2024/4/21 18:25
 * Github: https://github.com/tyza66
 **/

public class EntityClean extends BukkitRunnable {
    private static final int CLEAN_DELAY_SECONDS = 60; // 通告结束后清理前的固定倒计时

    private final JavaPlugin plugin; // 插件实例，用于调度通告和清理任务
    private final List<BukkitRunnable> pendingTasks = new ArrayList<>(); // 延迟中的任务，插件卸载时统一取消

    public EntityClean(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        // 清理前 60 秒、30 秒各通告一次，最后 5 秒每秒通告
        announce(60);
        announce(30);
        for (int i = 5; i > 0; i--) {
            announce(i);
        }

        // 倒计时结束后清除掉落物
        BukkitRunnable cleanup = new BukkitRunnable() {
            @Override
            public void run() {
                pendingTasks.remove(this);
                Bukkit.getWorlds().forEach(world ->
                        world.getEntitiesByClasses(Item.class).forEach(Entity::remove));
                Bukkit.broadcastMessage("掉落物已被清除。");
            }
        };
        pendingTasks.add(cleanup);
        cleanup.runTaskLater(plugin, CLEAN_DELAY_SECONDS * 20L);
    }

    private void announce(int secondsLeft) {
        final int left = secondsLeft;
        BukkitRunnable announcer = new BukkitRunnable() {
            @Override
            public void run() {
                pendingTasks.remove(this);
                Bukkit.broadcastMessage("[掉落物清理]距离下次掉落物清理还差" + left + "秒，请注意拾取");
            }
        };
        pendingTasks.add(announcer);
        announcer.runTaskLater(plugin, (CLEAN_DELAY_SECONDS - left) * 20L);
    }

    // 插件卸载/重载时取消延迟中的通告和清理，避免关闭后残留任务仍会广播或清物
    public void close() {
        for (BukkitRunnable task : pendingTasks) {
            task.cancel();
        }
        pendingTasks.clear();
    }
}
