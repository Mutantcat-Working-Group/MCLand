package org.mutantcat.mcland262.home;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * 玩家 home 位置 SQLite 存储，与账号库同库，固定写入 plugins/MCLand/MCLand/user/user.db。
 * 使用独立连接并设置 busy_timeout，避免与账号库的写入相互阻塞。
 */
public class HomeDatabase implements AutoCloseable {
    private final Connection connection;

    public HomeDatabase(JavaPlugin plugin) throws SQLException {
        File dbFile = new File(plugin.getDataFolder(), "MCLand/user/user.db");
        File parentDir = dbFile.getParentFile();
        if (!parentDir.exists() && !parentDir.mkdirs()) {
            throw new SQLException("无法创建 home 数据库目录: " + parentDir);
        }
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("未找到 SQLite JDBC 驱动", e);
        }
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout = 3000");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS homes (" +
                    "uuid TEXT PRIMARY KEY," +
                    "world TEXT NOT NULL," +
                    "x REAL NOT NULL," +
                    "y REAL NOT NULL," +
                    "z REAL NOT NULL," +
                    "yaw REAL NOT NULL," +
                    "pitch REAL NOT NULL," +
                    "updated_at INTEGER NOT NULL)");
        }
    }

    /** 读取 home；没有记录或所在世界已不存在时返回 null */
    public Location load(UUID uuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT world, x, y, z, yaw, pitch FROM homes WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                World world = Bukkit.getWorld(result.getString("world"));
                if (world == null) {
                    // 世界被删或改名，home 视为失效，提示重新设置
                    return null;
                }
                return new Location(world,
                        result.getDouble("x"),
                        result.getDouble("y"),
                        result.getDouble("z"),
                        result.getFloat("yaw"),
                        result.getFloat("pitch"));
            }
        }
    }

    public void save(UUID uuid, Location location) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO homes (uuid, world, x, y, z, yaw, pitch, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                        "ON CONFLICT(uuid) DO UPDATE SET " +
                        "world = excluded.world, x = excluded.x, y = excluded.y, z = excluded.z, " +
                        "yaw = excluded.yaw, pitch = excluded.pitch, updated_at = excluded.updated_at")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, location.getWorld().getName());
            statement.setDouble(3, location.getX());
            statement.setDouble(4, location.getY());
            statement.setDouble(5, location.getZ());
            statement.setDouble(6, location.getYaw());
            statement.setDouble(7, location.getPitch());
            statement.setLong(8, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }
}
