package org.mutantcat.mcland262.home;

import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Level;

/**
 * home 业务入口：每个玩家一个 home，读写都直接落到 SQLite，不做内存缓存，
 * 避免缓存与数据库不一致，本地库读取的开销也可以忽略。
 */
public class HomeManager implements AutoCloseable {
    private final JavaPlugin plugin;
    private final HomeDatabase database;

    public HomeManager(JavaPlugin plugin, HomeDatabase database) {
        this.plugin = plugin;
        this.database = database;
    }

    /** 取 home；没有设置或所在世界已不存在时返回 null */
    public Location getHome(UUID uuid) {
        try {
            return database.load(uuid);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "读取 home 失败: " + uuid, e);
            return null;
        }
    }

    /** 设置 home；写库失败时抛出，由命令层提示玩家重试 */
    public void setHome(UUID uuid, Location location) throws SQLException {
        database.save(uuid, location);
    }

    @Override
    public void close() throws SQLException {
        database.close();
    }
}
