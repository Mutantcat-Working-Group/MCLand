package org.mutantcat.mcland110.timer.entity;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Author: tyza66
 * Date: 2024/4/21 19:01
 * Github: https://github.com/tyza66
 **/

public class AnimalClean extends BukkitRunnable {
    private final int countdownTime = 5; // 倒计时时间（秒）
    private int timeLeft = countdownTime;

    @Override
    public void run() {
        for (; timeLeft > 0; timeLeft--) {
            // 倒计时提示
            for (Player player : Bukkit.getServer().getOnlinePlayers()) {
                player.sendMessage("§c注意：全世界生物将在 " + timeLeft + " 秒后被清除！");

            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        // 遍历所有世界
        Bukkit.getServer().getWorlds().forEach(world -> {
            // 遍历世界中的所有实体
            for (Entity entity : world.getEntities()) {
                // 检查实体是否是生物（不包括玩家）
                if (entity.getType() != EntityType.PLAYER) {
                    // 移除实体
                    entity.remove();
                }
            }
        });
        Bukkit.getServer().broadcastMessage("§a生物已被清除。");
        timeLeft = countdownTime;
    }
}