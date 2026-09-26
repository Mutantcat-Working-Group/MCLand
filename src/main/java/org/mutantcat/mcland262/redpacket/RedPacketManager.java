package org.mutantcat.mcland262.redpacket;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.mutantcat.mcland262.user.AuthManager;
import org.mutantcat.mcland262.user.UserDatabase;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 整点红包：奇数整点（1点、3点、5点……）服务器发出一个随机 10-50 金币的红包，
 * 玩家 /red 每轮随机抢 1-10 金币；本轮抢完或满一小时没抢完都会清零，
 * 清零之后再抢一律提示“红包已被抢完”，直到下一个奇数整点发新的。
 * 所有读写都在主线程（调度任务与命令回调），不需要加锁。
 */
public class RedPacketManager {
    private static final String PREFIX = "[红包]";

    /** 每个红包的金币总数范围（含两端） */
    private static final long TOTAL_MIN = 10L;
    private static final long TOTAL_MAX = 50L;
    /** 每次抢到的金币范围（含两端） */
    private static final long GRAB_MIN = 1L;
    private static final long GRAB_MAX = 10L;
    /** 红包有效期：一小时 */
    private static final long VALID_MILLIS = 60L * 60L * 1000L;
    /** 检查周期：20 秒一次，整点过后最多迟 20 秒发出，开销可以忽略 */
    private static final long CHECK_PERIOD_TICKS = 400L;

    private final JavaPlugin plugin;
    private final AuthManager authManager;
    private final UserDatabase userDatabase;
    private final Logger logger;
    private final BukkitTask task;

    /** 当前进行中的红包；null 表示这一小时没有可抢的红包 */
    private RedPacket current;
    /** 上轮红包已被抢完或已过期：此时 /red 也提示“已被抢完”，与“没到发红包时间”区分开 */
    private boolean exhausted;
    /** 已处理过的整点小时，避免同一小时内重复发放；启动时按当前小时初始化，重启当次不补发 */
    private int lastHour = LocalDateTime.now().getHour();

    public RedPacketManager(JavaPlugin plugin, AuthManager authManager, UserDatabase userDatabase) {
        this.plugin = plugin;
        this.authManager = authManager;
        this.userDatabase = userDatabase;
        this.logger = plugin.getLogger();
        this.task = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::checkTick, CHECK_PERIOD_TICKS, CHECK_PERIOD_TICKS);
    }

    /** /red：抢当前红包，入账到账号余额；没包、抢完、抢过各有各的提示 */
    public void grab(Player player) {
        if (authManager != null && !authManager.isLoggedIn(player.getUniqueId())) {
            player.sendMessage(PREFIX + "请先登录后抢红包");
            return;
        }
        if (userDatabase == null) {
            player.sendMessage(PREFIX + "金币系统未启用");
            return;
        }

        RedPacket packet = current;
        if (packet != null && System.currentTimeMillis() - packet.issuedAt >= VALID_MILLIS) {
            // 满一小时没抢完，清零；之后再有人来领也是“已被抢完”
            current = null;
            packet = null;
            exhausted = true;
            logger.info("整点红包满一小时未抢完，已清零");
        }
        if (packet == null) {
            player.sendMessage(PREFIX + (exhausted
                    ? "红包已被抢完"
                    : "当前没有红包，奇数整点（1点、3点、5点...）发放，请输入/red 抢红包"));
            return;
        }
        if (packet.grabbers.contains(player.getUniqueId())) {
            player.sendMessage(PREFIX + "本轮红包你已经抢过了，把机会留给别人吧");
            return;
        }

        // 最后一个被抢完时按剩余封顶，不会抢出负数
        long amount = Math.min(GRAB_MIN + randomRange(GRAB_MAX - GRAB_MIN + 1), packet.remaining);
        long balance;
        try {
            balance = userDatabase.addBalance(player.getUniqueId(), amount);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "抢红包入账失败", e);
            player.sendMessage(PREFIX + "抢红包失败，请联系管理员检查服务器日志");
            return;
        }
        if (balance < 0) {
            player.sendMessage(PREFIX + "尚未查询到账号，请先注册登录");
            return;
        }

        packet.grabbers.add(player.getUniqueId());
        packet.remaining -= amount;
        boolean drained = packet.remaining <= 0;
        if (drained) {
            current = null;
            exhausted = true;
            logger.info("整点红包已被领完");
        }
        player.sendMessage(PREFIX + "抢到" + amount + "金币，当前余额为" + balance + "金币"
                + (drained ? "，本轮红包已被抢完" : "，本轮红包还剩" + packet.remaining + "金币"));
    }

    /** 每到整点检查一次：奇数点发新包；顺带兜底把过期的旧包清零 */
    private void checkTick() {
        int hour = LocalDateTime.now().getHour();
        if (hour != lastHour) {
            lastHour = hour;
            if (hour % 2 != 0) {
                issue();
            }
        }
        // 先发新包再判过期，刚发出来的包不会误判成过期
        if (current != null && System.currentTimeMillis() - current.issuedAt >= VALID_MILLIS) {
            current = null;
            exhausted = true;
            logger.info("整点红包满一小时未抢完，已清零");
        }
    }

    /** 发新包：替换掉上一轮的残留状态，并向全服播报 */
    private void issue() {
        long total = TOTAL_MIN + randomRange(TOTAL_MAX - TOTAL_MIN + 1);
        current = new RedPacket(System.currentTimeMillis(), total);
        exhausted = false;
        logger.info("已发放整点红包，共" + total + "金币");
        Bukkit.broadcastMessage(PREFIX + "服务器发出了一个红包，共" + total
                + "金币，输入/red 抢红包，每人每轮一次，一小时内有效");
    }

    private static long randomRange(long bound) {
        return ThreadLocalRandom.current().nextLong(bound);
    }

    public void close() {
        task.cancel();
    }

    /** 一轮红包：发起时间、剩余金币、本轮已抢过的人 */
    private static final class RedPacket {
        private final long issuedAt;
        private long remaining;
        private final Set<UUID> grabbers = new HashSet<>();

        private RedPacket(long issuedAt, long remaining) {
            this.issuedAt = issuedAt;
            this.remaining = remaining;
        }
    }
}
