package org.mutantcat.mcland110.event.player;

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
    private final String welcomeMessage; // 欢迎语（可配置，空则不发送）

    public PlayerJoinedEvent(String welcomeMessage) {
        this.welcomeMessage = welcomeMessage;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (welcomeMessage != null && !welcomeMessage.isEmpty()) {
            event.getPlayer().sendMessage(welcomeMessage);
        }
    }
}
