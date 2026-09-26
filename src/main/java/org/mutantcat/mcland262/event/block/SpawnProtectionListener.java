package org.mutantcat.mcland262.event.block;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.mutantcat.mcland262.spawn.SpawnRegion;

/**
 * Author: tyza66
 * Date: 2024/4/21 19:12
 * Github: https://github.com/tyza66
 **/

/**
 * 主城保护：普通玩家在保护区域内不能放置也不能破坏方块；
 * OP（或持有 mcland.spawnprotection.bypass 权限）直接放行，方便搭建和维护主城。
 */
public class SpawnProtectionListener implements Listener {
    /** 绕过主城保护的权限节点，方便给非 OP 的建造者单独授权 */
    public static final String BYPASS_PERMISSION = "mcland.spawnprotection.bypass";

    private final SpawnRegion region;

    public SpawnProtectionListener(SpawnRegion region) {
        this.region = region;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (canBypass(player)) {
            return;
        }
        if (region.contains(event.getBlock().getLocation())) {
            // 取消事件，阻止方块被破坏
            player.sendMessage("这个区域受到保护，无法破坏方块。");
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (canBypass(player)) {
            return;
        }
        if (region.contains(event.getBlock().getLocation())) {
            // 取消事件，阻止方块被放置
            player.sendMessage("这个区域受到保护，无法放置方块。");
            event.setCancelled(true);
        }
    }

    /** OP 或被授权的建造者不受保护限制 */
    private boolean canBypass(Player player) {
        return player.isOp() || player.hasPermission(BYPASS_PERMISSION);
    }
}
