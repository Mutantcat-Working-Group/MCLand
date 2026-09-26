// MCLand — 由异猫工作群（mutantcat.org）发行
// GitHub: https://github.com/Mutantcat-Working-Group
package org.mutantcat.mcland262;

import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.mutantcat.mcland262.event.block.SpawnProtectionListener;
import org.mutantcat.mcland262.event.player.NoDropOnDeathEvent;
import org.mutantcat.mcland262.event.player.PlayerJoinedEvent;
import org.mutantcat.mcland262.help.HelpListener;
import org.mutantcat.mcland262.land.LandCommand;
import org.mutantcat.mcland262.land.LandListener;
import org.mutantcat.mcland262.land.LandManager;
import org.mutantcat.mcland262.land.LandProtectionListener;
import org.mutantcat.mcland262.home.HomeCommand;
import org.mutantcat.mcland262.home.HomeDatabase;
import org.mutantcat.mcland262.home.HomeManager;
import org.mutantcat.mcland262.money.MoneyCommand;
import org.mutantcat.mcland262.redpacket.RedCommand;
import org.mutantcat.mcland262.redpacket.RedPacketManager;
import org.mutantcat.mcland262.spawn.SpawnCommand;
import org.mutantcat.mcland262.spawn.SpawnGuardTask;
import org.mutantcat.mcland262.spawn.SpawnRegion;
import org.mutantcat.mcland262.spawn.SpawnWallTask;
import org.mutantcat.mcland262.sell.SellCommand;
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
    private UserDatabase userDatabase;
    private AuthManager authManager;
    private TeleportRequestManager teleportManager;
    private EntityClean entityClean;
    private AnimalClean animalClean;
    private HomeManager homeManager;
    private RedPacketManager redPacketManager;
    private LandManager landManager;

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
            userDatabase = new UserDatabase(this);
            authManager = new AuthManager(userDatabase, getLogger(),
                    getConfig().getBoolean("auth.bedrock-session.enabled", true),
                    getConfig().getLong("auth.bedrock-session.window-minutes", 60L));
            getServer().getPluginManager().registerEvents(new AuthListener(this, authManager), this);
            getCommand("register").setExecutor(new AuthCommand(authManager));
            getCommand("login").setExecutor(new AuthCommand(authManager));
            getCommand("password").setExecutor(new AuthCommand(authManager));
            getLogger().info("用户注册/登录功能已启用（SQLite；基岩版缓存登录 " +
                    getConfig().getLong("auth.bedrock-session.window-minutes", 60L) + " 分钟内同 IP 免密，Java 版照旧每次登录）");
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "用户数据库初始化失败，注册/登录功能未启用", e);
        }

        // 玩家传送请求（/tp 发起，/accept 接受）
        teleportManager = new TeleportRequestManager(this, authManager, userDatabase);
        getServer().getPluginManager().registerEvents(new TeleportListener(teleportManager), this);
        getCommand("tp").setExecutor(new TeleportCommand(teleportManager));
        getCommand("accept").setExecutor(new AcceptCommand(teleportManager));

        // 金币系统（/money 查余额与转账，/request 索要），与账号库同库，金币跟账号绑定
        getCommand("money").setExecutor(new MoneyCommand(this, authManager, teleportManager, userDatabase));
        getCommand("request").setExecutor(new MoneyCommand(this, authManager, teleportManager, userDatabase));

        // 交易系统（/sell 查看价目，/sell sum 估价，/sell sure 出售手中物品），卖出金币入账到账号余额
        getCommand("sell").setExecutor(new SellCommand(this, authManager, userDatabase));
        getLogger().info("交易系统已启用（/sell、/sell sum、/sell sure）");

        // 整点红包：奇数整点发一个 10-50 金币的红包，玩家 /red 每轮随机抢 1-10 金币，入账到账号余额
        redPacketManager = new RedPacketManager(this, authManager, userDatabase);
        getCommand("red").setExecutor(new RedCommand(redPacketManager));
        getLogger().info("整点红包已启用（奇数整点发放，/red 每轮随机抢 1-10 金币，一小时内有效）");

        // /help 帮助菜单：由 HelpListener 在命令预处理阶段拦截输出，不注册 /help 命令，避免与服务器内置命令冲突
        getServer().getPluginManager().registerEvents(new HelpListener(), this);

        // 主城保护（世界不存在时跳过并告警，避免 NPE）
        String worldName = getConfig().getString("spawn-protection.world", "world");
        World spawnWorld = getServer().getWorld(worldName);
        if (spawnWorld != null) {
            SpawnRegion region = new SpawnRegion(spawnWorld, getConfig().getInt("spawn-protection.radius", 20));

            // 普通玩家在区域内禁止放置和破坏，OP 及授权建造者放行
            getServer().getPluginManager().registerEvents(new SpawnProtectionListener(region), this);

            // 怪物秒杀：进入区域的敌对生物直接击杀，静默执行不播报；被秒杀的怪物不掉落物品和经验
            SpawnGuardTask spawnGuard = new SpawnGuardTask(region);
            getServer().getPluginManager().registerEvents(spawnGuard, this);
            tasks.add(spawnGuard.runTaskTimer(this, 0L,
                    Math.max(1L, getConfig().getLong("spawn-protection.guard.period-ticks", 10))));

            // 边界能量墙：按玩家视野渲染边界粒子，可整体关闭
            if (getConfig().getBoolean("spawn-protection.wall.enabled", true)) {
                SpawnWallTask spawnWall = new SpawnWallTask(region,
                        getConfig().getInt("spawn-protection.wall.view-distance-blocks", 24),
                        getConfig().getInt("spawn-protection.wall.horizontal-step-blocks", 2),
                        getConfig().getInt("spawn-protection.wall.vertical-range-blocks", 6),
                        getConfig().getInt("spawn-protection.wall.vertical-step-blocks", 3));
                tasks.add(spawnWall.runTaskTimer(this, 0L,
                        Math.max(1L, getConfig().getLong("spawn-protection.wall.period-ticks", 20))));
                getLogger().info("主城边界能量墙已启用");
            }

            // /spawn 返回主城出生点
            getCommand("spawn").setExecutor(new SpawnCommand(region));
        } else {
            getLogger().warning("未找到世界 \"" + worldName + "\"，主城保护未启用。");
        }

        // home（/sethome 设置、/home 返回），与账号库同库；初始化失败仅告警，不影响其余功能
        try {
            homeManager = new HomeManager(this, new HomeDatabase(this));
            getCommand("home").setExecutor(new HomeCommand(homeManager));
            getCommand("sethome").setExecutor(new HomeCommand(homeManager));
            getLogger().info("home 功能已启用（/sethome、/home）");
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "home 数据库初始化失败，home 功能未启用", e);
        }

        // 圈地系统（木铲两点选点、金币购买、白名单与领地保护），与账号库同库；用户库未启用时跳过
        if (userDatabase == null || authManager == null) {
            getLogger().warning("用户数据库或登录系统未启用，圈地系统未启用");
        } else {
            long landPricePerBlock = Math.max(1L, getConfig().getLong("land.price-per-block", 1000L));
            int landMinSide = Math.max(1, getConfig().getInt("land.min-side-blocks", 5));
            // 主城保护区换算成水平矩形；主城没加载时为 null，买地不与他判重
            LandManager.SpawnRect landSpawnRect = null;
            if (spawnWorld != null) {
                Location spawnCenter = spawnWorld.getSpawnLocation();
                int spawnRadius = Math.max(0, getConfig().getInt("spawn-protection.radius", 20));
                landSpawnRect = new LandManager.SpawnRect(spawnWorld.getName(),
                        (int) Math.floor(spawnCenter.getX() - spawnRadius),
                        (int) Math.ceil(spawnCenter.getX() + spawnRadius),
                        (int) Math.floor(spawnCenter.getZ() - spawnRadius),
                        (int) Math.ceil(spawnCenter.getZ() + spawnRadius));
            }
            try {
                landManager = new LandManager(this, authManager, userDatabase,
                        landPricePerBlock, landMinSide, landSpawnRect);
                getServer().getPluginManager().registerEvents(new LandListener(landManager, authManager), this);
                getServer().getPluginManager().registerEvents(new LandProtectionListener(landManager), this);
                getCommand("land").setExecutor(new LandCommand(landManager, authManager));
                getLogger().info("圈地系统已启用（木铲右键选点，/land 确认圈地，每格 " + landPricePerBlock
                        + " 金币，至少 " + landMinSide + "x" + landMinSide
                        + "，/land whitelist 管理白名单）");
            } catch (SQLException e) {
                getLogger().log(Level.SEVERE, "圈地数据库初始化失败，圈地系统未启用", e);
            }
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
        if (redPacketManager != null) {
            redPacketManager.close();
        }
        if (authManager != null) {
            try {
                authManager.close();
            } catch (SQLException e) {
                getLogger().log(Level.SEVERE, "关闭用户数据库失败", e);
            }
        }
        if (homeManager != null) {
            try {
                homeManager.close();
            } catch (SQLException e) {
                getLogger().log(Level.SEVERE, "关闭 home 数据库失败", e);
            }
        }
        if (landManager != null) {
            try {
                landManager.close();
            } catch (SQLException e) {
                getLogger().log(Level.SEVERE, "关闭圈地数据库失败", e);
            }
        }
        getLogger().info("Mutantcat Land 26.2 服务器正在关闭!");
    }
}
