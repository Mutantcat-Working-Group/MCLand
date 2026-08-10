package org.mutantcat.mcland110.event.player;

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
        // 取消掉落物品和经验
        event.setKeepInventory(true); // 保留玩家的背包和装备
        event.setDroppedExp(0); // 设置掉落的经验为0
        event.setKeepLevel(true); // 保留玩家的等级
    }
}
