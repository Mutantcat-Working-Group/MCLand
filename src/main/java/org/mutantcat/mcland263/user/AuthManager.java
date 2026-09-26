package org.mutantcat.mcland263.user;

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
    private final UserDatabase database;
    private final Logger logger;
    private final Set<UUID> loggedIn = ConcurrentHashMap.newKeySet();

    public AuthManager(UserDatabase database, Logger logger) {
        this.database = database;
        this.logger = logger;
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

    @Override
    public void close() throws SQLException {
        loggedIn.clear();
        database.close();
    }
}
