package org.mutantcat.mcland262.timer.entity;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.plugin.java.JavaPlugin;
import org.mutantcat.mcland262.timer.CountdownCleanTask;

/**
 * Author: tyza66
 * Date: 2024/4/21 18:25
 * Github: https://github.com/tyza66
 **/

public class EntityClean extends CountdownCleanTask {
    public EntityClean(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    protected void performClean() {
        // 清除所有已加载世界中的掉落物实体
        Bukkit.getWorlds().forEach(world ->
                world.getEntitiesByClasses(Item.class).forEach(Entity::remove));
    }

    @Override
    protected String countdownMessage(int secondsLeft) {
        return "[掉落物清理]距离下次掉落物清理还差" + secondsLeft + "秒，请注意拾取";
    }

    @Override
    protected String finishedMessage() {
        return "[掉落物清理]掉落物已被清除。";
    }
}
