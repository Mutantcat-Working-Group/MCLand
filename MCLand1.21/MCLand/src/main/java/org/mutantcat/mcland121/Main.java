package org.mutantcat.mcland121;

import org.bukkit.plugin.java.JavaPlugin;
import org.mutantcat.mcland121.event.block.SpawnProtectionListener;
import org.mutantcat.mcland121.event.player.NoDropOnDeathEvent;
import org.mutantcat.mcland121.event.player.PlayerJoinedEvent;
import org.mutantcat.mcland121.timer.entity.AnimalClean;
import org.mutantcat.mcland121.timer.entity.EntityClean;

public class Main extends JavaPlugin {
    @Override
    public void onEnable() {
        getLogger().info("MCLand 121 插件已启用!");
        // 注册事件
        getServer().getPluginManager().registerEvents(new PlayerJoinedEvent(), this);
        getServer().getPluginManager().registerEvents(new NoDropOnDeathEvent(), this);
        getServer().getPluginManager().registerEvents(new SpawnProtectionListener(getServer().getWorld("world"), 20), this);

        // 定时任务
        new EntityClean().runTaskTimer(this, 0, 1800 * 20);
        new AnimalClean().runTaskTimer(this, 0, 3600 * 20);
    }

    @Override
    public void onDisable() {
        getLogger().info("Mutantcat Land 110 服务器正在关闭!");
    }
}