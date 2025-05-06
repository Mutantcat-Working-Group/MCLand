package org.mutantcat.mcland121.timer.entity;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Author: tyza66
 * Date: 2024/4/21 18:25
 * Github: https://github.com/tyza66
 **/

public class EntityClean extends BukkitRunnable {
    private final int countdownTime = 5; // 倒计时时间（秒）
    private int timeLeft = countdownTime;

    @Override
    public void run() {
        for (; timeLeft > 0; timeLeft--) {
            // 倒计时提示
            for (Player player : Bukkit.getServer().getOnlinePlayers()) {
                player.sendMessage("§c注意：全世界的掉落物将在 " + timeLeft + " 秒后被清除！");
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        // 倒计时结束，清除掉落物并重置倒计时
        Bukkit.getServer().getWorlds().forEach(world -> {
            world.getEntitiesByClasses(org.bukkit.entity.Item.class).forEach(org.bukkit.entity.Entity::remove);
        });
        Bukkit.getServer().broadcastMessage("§a掉落物已被清除。");
        timeLeft = countdownTime;
    }
}
