package org.mutantcat.mcland262.user;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.mutantcat.mcland262.user.UserDatabase.BedrockSession;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 注册/登录会话管理，持有用户数据库并记录在线玩家的登录状态。
 */
public class AuthManager implements AutoCloseable {
    /** Floodgate（间歇泉）API 类名，装没装都靠它软探测，不写死依赖 */
    private static final String FLOODGATE_API = "org.geysermc.floodgate.api.FloodgateApi";

    /** 反射句柄；null 表示服上没装 Floodgate，首次用到时解析一次并缓存 */
    private static volatile FloodgateHandle floodgateHandle;

    private final UserDatabase database;
    private final Logger logger;
    private final Set<UUID> loggedIn = ConcurrentHashMap.newKeySet();
    /** 基岩版缓存登录开关与有效时长（毫秒） */
    private final boolean bedrockSessionEnabled;
    private final long bedrockSessionWindowMillis;

    public AuthManager(UserDatabase database, Logger logger) {
        this(database, logger, true, 60L);
    }

    public AuthManager(UserDatabase database, Logger logger,
                       boolean bedrockSessionEnabled, long bedrockSessionWindowMinutes) {
        this.database = database;
        this.logger = logger;
        this.bedrockSessionEnabled = bedrockSessionEnabled;
        this.bedrockSessionWindowMillis = Math.max(0L, bedrockSessionWindowMinutes) * 60_000L;
    }

    public boolean isRegistered(UUID uuid) {
        try {
            return database.isRegistered(uuid);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "查询用户注册状态失败", e);
            return false;
        }
    }

    public boolean isLoggedIn(UUID uuid) {
        return loggedIn.contains(uuid);
    }

    public boolean register(UUID uuid, String username, String password) {
        try {
            database.register(uuid, username, password);
            loggedIn.add(uuid);
            return true;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "写入用户账号失败", e);
            return false;
        }
    }

    public boolean login(UUID uuid, String password) {
        try {
            if (!database.isPasswordCorrect(uuid, password)) {
                return false;
            }
            loggedIn.add(uuid);
            return true;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "校验用户密码失败", e);
            return false;
        }
    }

    public boolean updatePassword(UUID uuid, String password) {
        try {
            database.updatePassword(uuid, password);
            return true;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "更新用户密码失败", e);
            return false;
        }
    }

    public void logout(UUID uuid) {
        loggedIn.remove(uuid);
    }

    /**
     * 基岩版免密登录：已注册、是基岩版玩家、缓存登录时间在有效期内、且 IP 与上次一致，四条都满足才直接放过。
     * IP 取 Java 服务端看到的连接地址——基岩版经间歇泉进来时拿到的是间歇泉那一侧的地址，
     * 只要还是同一个入口（同一台间歇泉）就算同一个 IP；Java 版不走这条，照旧每次进服都要登录。
     */
    public boolean tryBedrockAutoLogin(Player player) {
        if (!bedrockSessionEnabled) {
            return false;
        }
        UUID uuid = player.getUniqueId();
        try {
            if (!database.isRegistered(uuid) || !isBedrockPlayer(player)) {
                return false;
            }
            BedrockSession session = database.findBedrockSession(uuid);
            if (session == null) {
                return false;
            }
            String ip = addressOf(player);
            if (ip == null || !ip.equals(session.ip())) {
                return false;
            }
            if (System.currentTimeMillis() - session.loginAt() > bedrockSessionWindowMillis) {
                return false;
            }
            loggedIn.add(uuid);
            // 自动登录也算一次登录，时间戳跟着往后推，实际效果是“1 小时没上线过才需要重新登录”
            refreshBedrockSession(player);
            return true;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "查询基岩版缓存登录失败", e);
            return false;
        }
    }

    /** 登录成功后刷新基岩版缓存；非基岩版玩家不写数据库，Java 版每次进服照旧要登录 */
    public void refreshBedrockSession(Player player) {
        if (!bedrockSessionEnabled || !isBedrockPlayer(player)) {
            return;
        }
        String ip = addressOf(player);
        if (ip == null) {
            return;
        }
        try {
            database.updateBedrockSession(player.getUniqueId(), System.currentTimeMillis(), ip);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "写入基岩版缓存登录失败", e);
        }
    }

    /** 连接地址去掉端口，取不到就返回 null——宁可要求重新登录，也不放空放过 */
    private String addressOf(Player player) {
        InetSocketAddress address = player.getAddress();
        return address == null || address.getAddress() == null
                ? null : address.getAddress().getHostAddress();
    }

    /** 服上装了 Floodgate 且该玩家是基岩版玩家时为 true，任何异常都按 Java 玩家处理 */
    private boolean isBedrockPlayer(Player player) {
        FloodgateHandle handle = floodgateHandle();
        if (handle == null) {
            return false;
        }
        try {
            Object api = handle.getInstance.invoke(null);
            return Boolean.TRUE.equals(handle.isFloodgatePlayer.invoke(api, player.getUniqueId()));
        } catch (Throwable ignored) {
            // Floodgate 尚未就绪或方法签名变动：只影响自动登录，不影响正常登录流程
            return false;
        }
    }

    private static FloodgateHandle floodgateHandle() {
        FloodgateHandle handle = floodgateHandle;
        if (handle == null) {
            handle = resolveFloodgate();
            floodgateHandle = handle;
        }
        return handle;
    }

    /**
     * 经 Floodgate 插件自身的类加载器取 API，这样即便 plugin.yml 没写 depend 也拿得到；
     * 服上没装 Floodgate 时返回 null，解析结果缓存下来不再重复试。
     */
    private static FloodgateHandle resolveFloodgate() {
        try {
            Plugin floodgate = Bukkit.getPluginManager().getPlugin("floodgate");
            ClassLoader loader = floodgate != null ? floodgate.getClass().getClassLoader() : null;
            Class<?> api = loader != null
                    ? Class.forName(FLOODGATE_API, true, loader)
                    : Class.forName(FLOODGATE_API);
            return new FloodgateHandle(api.getMethod("getInstance"),
                    api.getMethod("isFloodgatePlayer", UUID.class));
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Floodgate API 反射句柄，解析一次到处用 */
    private record FloodgateHandle(Method getInstance, Method isFloodgatePlayer) {}

    @Override
    public void close() throws SQLException {
        loggedIn.clear();
        database.close();
    }
}
