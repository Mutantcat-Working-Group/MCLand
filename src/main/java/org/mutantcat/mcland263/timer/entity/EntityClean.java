package org.mutantcat.mcland263.timer.entity;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Author: tyza66
 * Date: 2024/4/21 18:25
 * Github: https://github.com/tyza66
 **/

public class EntityClean extends BukkitRunnable {
    private static final int CLEAN_DELAY_SECONDS = 60; // 通告结束后清理前的固定倒计时

    private final JavaPlugin plugin; // 插件实例，用于调度通告和清理任务

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
        new BukkitRunnable() {
            @Override
            public void run() {
                Bukkit.getWorlds().forEach(world ->
                        world.getEntitiesByClasses(Item.class).forEach(Entity::remove));
                Bukkit.broadcastMessage("掉落物已被清除。");
            }
        }.runTaskLater(plugin, CLEAN_DELAY_SECONDS * 20L);
    }

    private void announce(int secondsLeft) {
        new BukkitRunnable() {
            @Override
            public void run() {
                Bukkit.broadcastMessage("[掉落物清理]距离下次掉落物清理还差" + secondsLeft + "，请注意拾取");
            }
        }.runTaskLater(plugin, (CLEAN_DELAY_SECONDS - secondsLeft) * 20L);
    }
}
