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
    /**
     * 新注册账号赠送金币的兜底值：配置没写 auth.default-balance 时用它，也是建表时的列默认值。
     */
    public static final long DEFAULT_BALANCE = 500L;

    private final Connection connection;
    /** 新注册账号实际赠送的金币数量，由配置 auth.default-balance 决定 */
    private final long startingBalance;

    public UserDatabase(JavaPlugin plugin, long startingBalance) throws SQLException {
        this.startingBalance = startingBalance;
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
                    "balance INTEGER NOT NULL DEFAULT 500," +
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
        if (!columns.contains("balance")) {
            statement.executeUpdate("ALTER TABLE users ADD COLUMN balance INTEGER NOT NULL DEFAULT 500");
        }
        if (!columns.contains("last_checkin_day")) {
            statement.executeUpdate("ALTER TABLE users ADD COLUMN last_checkin_day INTEGER");
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
                "INSERT INTO users (uuid, username, password_md5, balance, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, username);
            statement.setString(3, md5(password));
            statement.setLong(4, startingBalance);
            statement.setLong(5, now);
            statement.setLong(6, now);
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

    /** 查询金币余额，未注册返回 -1 */
    public long getBalance(UUID uuid) throws SQLException {
        return balance(connection, uuid);
    }

    /**
     * 给账号增加金币（交易系统卖出物品入账用），返回变动后的余额；账号不存在返回 -1。
     * 与转账一样放在单连接事务里，记账与刷新时间要么都成功要么都回滚。
     */
    public long addBalance(UUID uuid, long delta) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            if (balance(connection, uuid) < 0) {
                connection.rollback();
                return -1L;
            }
            addBalance(connection, uuid, delta);
            long updated = balance(connection, uuid);
            connection.commit();
            return updated;
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    /** 按用户名反查 UUID（不区分大小写），找不到返回 null；用于给离线玩家转账，含间歇泉前缀名 */
    public UUID findUuidByName(String username) throws SQLException {
        if (username == null || username.isEmpty()) {
            return null;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT uuid FROM users WHERE username = ? COLLATE NOCASE")) {
            statement.setString(1, username);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? UUID.fromString(result.getString(1)) : null;
            }
        }
    }

    /**
     * 账户支出（圈地买地等消费用途），返回变动后的余额。
     * 返回 -1 表示账号不存在，-2 表示余额不足，-3 表示金额非法；
     * 余额校验与扣款在同一个事务里完成，不会出现“查的时候够、扣的时候不够”。
     */
    public long spend(UUID uuid, long amount) throws SQLException {
        if (amount <= 0) {
            return -3L;
        }
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            long current = balance(connection, uuid);
            if (current < 0) {
                connection.rollback();
                return -1L;
            }
            if (current < amount) {
                connection.rollback();
                return -2L;
            }
            addBalance(connection, uuid, -amount);
            long updated = balance(connection, uuid);
            connection.commit();
            return updated;
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    /**
     * 转账：单连接事务，扣款和入账要么都成功要么都回滚。
     * 参数非法、账号缺失或余额不足都返回 false，不用抛异常区分业务失败和系统失败。
     */
    public boolean transfer(UUID from, UUID to, long amount) throws SQLException {
        if (from == null || to == null || from.equals(to) || amount <= 0) {
            return false;
        }
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            long fromBalance = balance(connection, from);
            if (fromBalance < 0 || fromBalance < amount) {
                connection.rollback();
                return false;
            }
            // 目标账号不存在时不凭空入账
            if (balance(connection, to) < 0) {
                connection.rollback();
                return false;
            }
            addBalance(connection, from, -amount);
            addBalance(connection, to, amount);
            connection.commit();
            return true;
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    /**
     * 每日签到：每个账号每天只能签到一次，奖励直接入账到账号余额。
     * todayEpochDay 用服务器本地日期的 epoch day 表示，比上次签到日大才允许签到，自然跨日清零；
     * 判定与入账在同一个事务里完成，重复调用不会重复发奖。
     * 返回变动后的余额；-1 表示账号不存在，-2 表示今天已经签到过，-3 表示奖励数额非法。
     */
    public long dailyCheckIn(UUID uuid, long todayEpochDay, long reward) throws SQLException {
        if (reward <= 0) {
            return -3L;
        }
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            if (balance(connection, uuid) < 0) {
                connection.rollback();
                return -1L;
            }
            Long lastDay = checkinDay(connection, uuid);
            // 大于等于都算已签到：时钟回拨时不能靠“日子更小”再刷一次
            if (lastDay != null && lastDay >= todayEpochDay) {
                connection.rollback();
                return -2L;
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE users SET last_checkin_day = ?, updated_at = ? WHERE uuid = ?")) {
                statement.setLong(1, todayEpochDay);
                statement.setLong(2, System.currentTimeMillis());
                statement.setString(3, uuid.toString());
                statement.executeUpdate();
            }
            addBalance(connection, uuid, reward);
            long updated = balance(connection, uuid);
            connection.commit();
            return updated;
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    /** 上次签到的 epoch day；没签到过或历史库该列为空都返回 null */
    private Long checkinDay(Connection connection, UUID uuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT last_checkin_day FROM users WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                long day = result.getLong(1);
                return result.wasNull() ? null : day;
            }
        }
    }

    private long balance(Connection connection, UUID uuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT balance FROM users WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getLong(1) : -1L;
            }
        }
    }

    private void addBalance(Connection connection, UUID uuid, long delta) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE users SET balance = balance + ?, updated_at = ? WHERE uuid = ?")) {
            statement.setLong(1, delta);
            statement.setLong(2, System.currentTimeMillis());
            statement.setString(3, uuid.toString());
            statement.executeUpdate();
        }
    }

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
