package org.mutantcat.mcland263.user;

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
import java.util.HexFormat;
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
