package org.mutantcat.mcland262.land;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 领地与白名单 SQLite 存储，与账号库同库（plugins/MCLand/MCLand/user/user.db），
 * 独立连接 + busy_timeout，避免和账号库的写入相互阻塞。
 * lands 一行一块地；land_whitelist 一行一个被授权玩家（名字小写存便于匹配，展示用原名单列）。
 */
public class LandDatabase implements AutoCloseable {
    private final Connection connection;

    public LandDatabase(JavaPlugin plugin) throws SQLException {
        File dbFile = new File(plugin.getDataFolder(), "MCLand/user/user.db");
        File parentDir = dbFile.getParentFile();
        if (!parentDir.exists() && !parentDir.mkdirs()) {
            throw new SQLException("无法创建圈地数据库目录: " + parentDir);
        }
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("未找到 SQLite JDBC 驱动", e);
        }
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout = 3000");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS lands (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "owner_uuid TEXT NOT NULL," +
                    "owner_name TEXT NOT NULL," +
                    "world TEXT NOT NULL," +
                    "min_x INTEGER NOT NULL," +
                    "max_x INTEGER NOT NULL," +
                    "min_z INTEGER NOT NULL," +
                    "max_z INTEGER NOT NULL," +
                    "created_at INTEGER NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS land_whitelist (" +
                    "land_id INTEGER NOT NULL," +
                    "player_name TEXT NOT NULL," +
                    "display_name TEXT NOT NULL," +
                    "added_at INTEGER NOT NULL," +
                    "PRIMARY KEY (land_id, player_name))");
        }
    }

    /** 启动时一次性把全部领地读进内存，之后判定都走内存，读库只发生在写操作时 */
    public List<LandRegion> listAll() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT id, owner_uuid, owner_name, world, min_x, max_x, min_z, max_z FROM lands")) {
            List<LandRegion> lands = new ArrayList<>();
            while (result.next()) {
                lands.add(new LandRegion(
                        result.getLong("id"),
                        result.getString("owner_uuid"),
                        result.getString("owner_name"),
                        result.getString("world"),
                        result.getInt("min_x"),
                        result.getInt("max_x"),
                        result.getInt("min_z"),
                        result.getInt("max_z")));
            }
            return lands;
        }
    }

    /** 插入一块地，返回自增 id；写库失败抛给上层，由上层回滚 */
    public long insertLand(String ownerUuid, String ownerName, String world,
                           int minX, int maxX, int minZ, int maxZ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO lands (owner_uuid, owner_name, world, min_x, max_x, min_z, max_z, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, ownerUuid);
            statement.setString(2, ownerName);
            statement.setString(3, world);
            statement.setInt(4, minX);
            statement.setInt(5, maxX);
            statement.setInt(6, minZ);
            statement.setInt(7, maxZ);
            statement.setLong(8, System.currentTimeMillis());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1L;
            }
        }
    }

    /** 扣款失败时按 id 删掉刚插入的地，保证不会白扣金币 */
    public void deleteLand(long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM land_whitelist WHERE land_id = ?")) {
            statement.setLong(1, id);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM lands WHERE id = ?")) {
            statement.setLong(1, id);
            statement.executeUpdate();
        }
    }

    /** 某块地的白名单展示名列表 */
    public List<String> listWhitelist(long landId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT display_name FROM land_whitelist WHERE land_id = ? ORDER BY display_name")) {
            statement.setLong(1, landId);
            try (ResultSet result = statement.executeQuery()) {
                List<String> names = new ArrayList<>();
                while (result.next()) {
                    names.add(result.getString(1));
                }
                return names;
            }
        }
    }

    /** 加白名单；重复添加用 INSERT OR IGNORE 幂等处理 */
    public void addWhitelist(long landId, String displayName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT OR IGNORE INTO land_whitelist (land_id, player_name, display_name, added_at) " +
                        "VALUES (?, ?, ?, ?)")) {
            statement.setLong(1, landId);
            statement.setString(2, displayName.toLowerCase(Locale.ROOT));
            statement.setString(3, displayName);
            statement.setLong(4, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    /** 移出白名单，返回是否确实删掉了东西 */
    public boolean removeWhitelist(long landId, String lowerName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM land_whitelist WHERE land_id = ? AND player_name = ?")) {
            statement.setLong(1, landId);
            statement.setString(2, lowerName);
            return statement.executeUpdate() > 0;
        }
    }

    /** 启动时把白名单一次性读进内存：键为 land_id，值为小写玩家名集合 */
    public Map<Long, Set<String>> loadWhitelists() throws SQLException {
        Map<Long, Set<String>> map = new HashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT land_id, player_name FROM land_whitelist")) {
            while (result.next()) {
                map.computeIfAbsent(result.getLong("land_id"), key -> new java.util.HashSet<>())
                        .add(result.getString("player_name"));
            }
        }
        return map;
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }
}
