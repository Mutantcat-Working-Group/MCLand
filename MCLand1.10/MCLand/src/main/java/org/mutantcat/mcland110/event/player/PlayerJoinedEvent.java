package org.mutantcat.mcland110.event.player;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Author: tyza66
 * Date: 2024/4/21 16:50
 * Github: https://github.com/tyza66
 **/

// 玩家加入时有提示
public class PlayerJoinedEvent implements Listener {
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // 获取玩家对象
        Player player = event.getPlayer();
        // 发送欢迎信息
        player.sendMessage("欢迎来到Mutantcat Land！");
    }
}
