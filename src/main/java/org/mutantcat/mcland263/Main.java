// MCLand — 由异猫工作群（mutantcat.org）发行
// GitHub: https://github.com/Mutantcat-Working-Group
package org.mutantcat.mcland263;

import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.mutantcat.mcland263.event.block.SpawnProtectionListener;
import org.mutantcat.mcland263.event.player.NoDropOnDeathEvent;
import org.mutantcat.mcland263.event.player.PlayerJoinedEvent;
import org.mutantcat.mcland263.timer.entity.AnimalClean;
import org.mutantcat.mcland263.timer.entity.EntityClean;
import org.mutantcat.mcland263.user.AuthCommand;
import org.mutantcat.mcland263.user.AuthListener;
import org.mutantcat.mcland263.user.AuthManager;
import org.mutantcat.mcland263.user.UserDatabase;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class Main extends JavaPlugin {
    // 记录注册的定时任务，便于卸载时统一取消
    private final List<BukkitTask> tasks = new ArrayList<>();
    private AuthManager authManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getLogger().info("Mutantcat Land 26.3 插件已启用!");
        // 发行方信息：由异猫工作群（mutantcat.org）发行。
        getLogger().info("发行方：异猫工作群（mutantcat.org） · https://github.com/Mutantcat-Working-Group");

        // 注册事件
        getServer().getPluginManager().registerEvents(
                new PlayerJoinedEvent(getConfig().getString("welcome-message", "[MCLand]欢迎来到方块猫窝！")), this);
        getServer().getPluginManager().registerEvents(new NoDropOnDeathEvent(), this);

        // 注册/登录功能（SQLite 用户库初始化失败时仅告警，不影响其余功能）
        try {
            UserDatabase userDatabase = new UserDatabase(this);
            authManager = new AuthManager(userDatabase, getLogger());
            getServer().getPluginManager().registerEvents(new AuthListener(this, authManager), this);
            getCommand("register").setExecutor(new AuthCommand(authManager));
            getCommand("login").setExecutor(new AuthCommand(authManager));
            getCommand("password").setExecutor(new AuthCommand(authManager));
            getLogger().info("用户注册/登录功能已启用（SQLite）");
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "用户数据库初始化失败，注册/登录功能未启用", e);
        }

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
        long itemInterval = Math.max(1L, getConfig().getLong("item-clean.interval-seconds", 300)) * 20L;
        EntityClean entityClean = new EntityClean(this);
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
        if (authManager != null) {
            try {
                authManager.close();
            } catch (SQLException e) {
                getLogger().log(Level.SEVERE, "关闭用户数据库失败", e);
            }
        }
        getLogger().info("Mutantcat Land 26.3 服务器正在关闭!");
    }
}
