package org.mutantcat.mcland262.event.player;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Author: tyza66
 * Date: 2024/4/21 16:57
 * Github: https://github.com/tyza66
 **/

// 死亡不掉落
public class NoDropOnDeathEvent implements Listener {
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        // 死亡不掉落：背包和装备保留，同时清空已经生成的掉落列表，避免地上下落物和背包重复
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setDroppedExp(0); // 设置掉落的经验为0
        event.setKeepLevel(true);
    }
}
