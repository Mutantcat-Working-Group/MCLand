package org.mutantcat.mcland263.timer;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

/**
 * 定时清理任务的公共骨架：清理前 60 秒、30 秒各通告一次，最后 5 秒每秒通告一次，
 * 倒计时结束执行清理并播报完成文案。
 */
public abstract class CountdownCleanTask extends BukkitRunnable {
    /** 倒计时播报点（距清理的秒数）：60、30 秒各一次，最后 5 秒每秒一次 */
    private static final int[] ANNOUNCE_POINTS = {60, 30, 5, 4, 3, 2, 1};
    /** 从开始播报到执行清理的总提前量（秒） */
    private static final int LEAD_SECONDS = 60;

    protected final JavaPlugin plugin; // 插件实例，用于调度通告和清理任务

    private final List<BukkitRunnable> pendingTasks = new ArrayList<>(); // 延迟中的任务，插件卸载时统一取消

    protected CountdownCleanTask(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        for (int secondsLeft : ANNOUNCE_POINTS) {
            schedule(secondsLeft, () -> Bukkit.broadcastMessage(countdownMessage(secondsLeft)));
        }
        schedule(0, () -> {
            performClean();
            Bukkit.broadcastMessage(finishedMessage());
        });
    }

    private void schedule(int secondsLeft, Runnable action) {
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                pendingTasks.remove(this);
                action.run();
            }
        };
        pendingTasks.add(task);
        task.runTaskLater(plugin, Math.max(0L, (LEAD_SECONDS - secondsLeft) * 20L));
    }

    /** 执行具体清理逻辑（主线程调用） */
    protected abstract void performClean();

    /** 倒计时播报文案，secondsLeft 为距清理的秒数 */
    protected abstract String countdownMessage(int secondsLeft);

    /** 清理完成播报文案 */
    protected abstract String finishedMessage();

    // 插件卸载/重载时取消延迟中的通告和清理，避免关闭后残留任务仍会广播或清物
    public void close() {
        for (BukkitRunnable task : pendingTasks) {
            task.cancel();
        }
        pendingTasks.clear();
    }
}
