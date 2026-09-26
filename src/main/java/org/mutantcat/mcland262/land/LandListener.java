package org.mutantcat.mcland262.land;

import org.bukkit.EquipmentSlot;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 木铲选点入口：主手持木铲右键方块即圈地选点。
 * 用 LOWEST 优先级抢在领地保护和登录限制之前接住事件并取消，
 * 保证在别人领地范围内、火把箱子旁边也能正常选点；取消事件同时避免木铲把草地质成土径。
 */
public class LandListener implements Listener {
    private final LandManager landManager;

    public LandListener(LandManager landManager) {
        this.landManager = landManager;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        // 主手才算，副手同一动作会再触发一次
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getInventory().getItemInMainHand().getType() != Material.WOODEN_SHOVEL) {
            return;
        }
        event.setCancelled(true);
        landManager.handleShovelClick(player, event.getClickedBlock().getLocation());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        landManager.clearSelection(event.getPlayer().getUniqueId());
    }
}
