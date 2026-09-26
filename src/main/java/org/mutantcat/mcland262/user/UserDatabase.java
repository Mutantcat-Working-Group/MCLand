package org.mutantcat.mcland262.user;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 用户账号 SQLite 存储，固定写入 plugins/MCLand/MCLand/user/user.db。
 */
public class UserDatabase implements AutoCloseable {
    private final Connection connection;

    public UserDatabase(JavaPlugin plugin) throws SQLException {
        File dbFile = new File(plugin.getDataFolder(), "MCLand/user/user.db");
        File parentDir = dbFile.getParentFile();
        if (!parentDir.exists() && !parentDir.mkdirs()) {
            throw new SQLException("无法创建用户数据库目录: " + parentDir);
        }
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("未找到 SQLite JDBC 驱动", e);
        }
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout = 3000");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS users (" +
                    "uuid TEXT PRIMARY KEY," +
                    "username TEXT NOT NULL," +
                    "password_md5 TEXT NOT NULL," +
                    "created_at INTEGER NOT NULL," +
                    "updated_at INTEGER NOT NULL)");
            ensureSessionColumns(statement);
        }
    }

    /**
     * 老库补列：基岩版缓存登录的时间和 IP 各占一列。
     * SQLite 的 ALTER TABLE 不支持 IF NOT EXISTS，先查 PRAGMA table_info 再补，重复启动不会报错。
     */
    private void ensureSessionColumns(Statement statement) throws SQLException {
        Set<String> columns = new HashSet<>();
        try (ResultSet result = statement.executeQuery("PRAGMA table_info(users)")) {
            while (result.next()) {
                columns.add(result.getString("name").toLowerCase(Locale.ROOT));
            }
        }
        if (!columns.contains("bedrock_login_at")) {
            statement.executeUpdate("ALTER TABLE users ADD COLUMN bedrock_login_at INTEGER");
        }
        if (!columns.contains("bedrock_login_ip")) {
            statement.executeUpdate("ALTER TABLE users ADD COLUMN bedrock_login_ip TEXT");
        }
    }

    public boolean isRegistered(UUID uuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM users WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    public boolean isPasswordCorrect(UUID uuid, String password) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT password_md5 FROM users WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getString(1).equalsIgnoreCase(md5(password));
            }
        }
    }

    public void register(UUID uuid, String username, String password) throws SQLException {
        long now = System.currentTimeMillis();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO users (uuid, username, password_md5, created_at, updated_at) VALUES (?, ?, ?, ?, ?)")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, username);
            statement.setString(3, md5(password));
            statement.setLong(4, now);
            statement.setLong(5, now);
            statement.executeUpdate();
        }
    }

    public void updatePassword(UUID uuid, String password) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE users SET password_md5 = ?, updated_at = ? WHERE uuid = ?")) {
            statement.setString(1, md5(password));
            statement.setLong(2, System.currentTimeMillis());
            statement.setString(3, uuid.toString());
            statement.executeUpdate();
        }
    }

    /** 基岩版缓存的登录记录；没注册、没记录过或 IP 为空都返回 null */
    public BedrockSession findBedrockSession(UUID uuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT bedrock_login_at, bedrock_login_ip FROM users WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                long loginAt = result.getLong(1);
                if (result.wasNull()) {
                    return null;
                }
                String ip = result.getString(2);
                if (ip == null || ip.isEmpty()) {
                    return null;
                }
                return new BedrockSession(loginAt, ip);
            }
        }
    }

    /** 记下这次成功登录的时间和服务端看到的 IP，供下次进服比对 */
    public void updateBedrockSession(UUID uuid, long loginAt, String ip) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE users SET bedrock_login_at = ?, bedrock_login_ip = ? WHERE uuid = ?")) {
            statement.setLong(1, loginAt);
            statement.setString(2, ip);
            statement.setString(3, uuid.toString());
            statement.executeUpdate();
        }
    }

    /** 基岩版缓存登录：上次成功登录的时刻与服务端看到的连接 IP */
    public record BedrockSession(long loginAt, String ip) {}

    public static String md5(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK 不支持 MD5", e);
        }
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }
}
