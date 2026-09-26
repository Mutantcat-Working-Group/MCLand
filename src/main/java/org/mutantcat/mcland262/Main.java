// MCLand — 由异猫工作群（mutantcat.org）发行
// GitHub: https://github.com/Mutantcat-Working-Group
package org.mutantcat.mcland262;

import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.mutantcat.mcland262.event.block.SpawnProtectionListener;
import org.mutantcat.mcland262.event.player.NoDropOnDeathEvent;
import org.mutantcat.mcland262.event.player.PlayerJoinedEvent;
import org.mutantcat.mcland262.help.HelpListener;
import org.mutantcat.mcland262.timer.entity.AnimalClean;
import org.mutantcat.mcland262.timer.entity.EntityClean;
import org.mutantcat.mcland262.teleport.AcceptCommand;
import org.mutantcat.mcland262.teleport.TeleportCommand;
import org.mutantcat.mcland262.teleport.TeleportListener;
import org.mutantcat.mcland262.teleport.TeleportRequestManager;
import org.mutantcat.mcland262.user.AuthCommand;
import org.mutantcat.mcland262.user.AuthListener;
import org.mutantcat.mcland262.user.AuthManager;
import org.mutantcat.mcland262.user.UserDatabase;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class Main extends JavaPlugin {
    // 记录注册的定时任务，便于卸载时统一取消
    private final List<BukkitTask> tasks = new ArrayList<>();
    private AuthManager authManager;
    private TeleportRequestManager teleportManager;
    private EntityClean entityClean;
    private AnimalClean animalClean;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getLogger().info("Mutantcat Land 26.2 插件已启用!");
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

        // 玩家传送请求（/tp 发起，/accept 接受）
        teleportManager = new TeleportRequestManager(this, authManager);
        getServer().getPluginManager().registerEvents(new TeleportListener(teleportManager), this);
        getCommand("tp").setExecutor(new TeleportCommand(teleportManager));
        getCommand("accept").setExecutor(new AcceptCommand(teleportManager));

        // /help 帮助菜单：由 HelpListener 在命令预处理阶段拦截输出，不注册 /help 命令，避免与服务器内置命令冲突
        getServer().getPluginManager().registerEvents(new HelpListener(), this);

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
        long itemInterval = Math.max(1L, getConfig().getLong("item-clean.interval-seconds", 900)) * 20L;
        entityClean = new EntityClean(this);
        tasks.add(entityClean.runTaskTimer(this, 0L, itemInterval));

        // 定时清理生物
        long animalInterval = Math.max(1L, getConfig().getLong("animal-clean.interval-seconds", 3600)) * 20L;
        int animalGridSize = getConfig().getInt("animal-clean.grid-size-blocks", 100);
        int animalMaxPerType = getConfig().getInt("animal-clean.max-per-type-per-grid", 2);
        animalClean = new AnimalClean(this, animalGridSize, animalMaxPerType);
        tasks.add(animalClean.runTaskTimer(this, 0L, animalInterval));
    }

    @Override
    public void onDisable() {
        // 取消所有定时任务，避免卸载时残留
        for (BukkitTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
        if (entityClean != null) {
            entityClean.close();
        }
        if (animalClean != null) {
            animalClean.close();
        }
        if (teleportManager != null) {
            teleportManager.close();
        }
        if (authManager != null) {
            try {
                authManager.close();
            } catch (SQLException e) {
                getLogger().log(Level.SEVERE, "关闭用户数据库失败", e);
            }
        }
        getLogger().info("Mutantcat Land 26.2 服务器正在关闭!");
    }
}
