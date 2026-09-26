package org.mutantcat.mcland262.land;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.List;

/**
 * 领地保护，三层含义：
 * 1) 领主本人、白名单玩家、OP（或持 bypass 权限）可以在领地内正常建造、破坏和交互，
 *    其他人一律被拒并提示“此为他人领地，禁止xxx”；
 * 2) 箱子、门、按钮等交互同样只放行授权玩家——领地里的箱子只有自己和白名单能看，
 *    门、按钮、拉杆、压力板也只有他们能碰；
 * 3) 领地和主城一样对外力封闭：爆炸、实体改动方块、液体渗入、点燃燃烧蔓延生成、
 *    活塞推拉、展示框和画都改不动领地地形。
 */
public class LandProtectionListener implements Listener {
    private final LandManager landManager;

    public LandProtectionListener(LandManager landManager) {
        this.landManager = landManager;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        LandRegion region = landManager.regionAt(event.getBlock().getLocation());
        if (region != null && !landManager.isAuthorized(event.getPlayer(), region)) {
            event.getPlayer().sendMessage("此为他人领地，禁止破坏");
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        LandRegion region = landManager.regionAt(event.getBlock().getLocation());
        if (region != null && !landManager.isAuthorized(event.getPlayer(), region)) {
            event.getPlayer().sendMessage("此为他人领地，禁止放置");
            event.setCancelled(true);
        }
    }

    /**
     * 交互保护：右键箱子、门、按钮、拉杆、栅栏门、压力板，以及 PHYSICAL 的踩踏都走这里。
     * 木铲选点在 LOWEST 优先级已取消的事件不会走到这，登录未完成的玩家也由登录限制先拦一道。
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.PHYSICAL) {
            return;
        }
        LandRegion region = landManager.regionAt(block.getLocation());
        if (region != null && !landManager.isAuthorized(event.getPlayer(), region)) {
            event.getPlayer().sendMessage("此为他人领地，禁止操作");
            event.setCancelled(true);
        }
    }

    /** 打开领地内容器：箱子、大箱子、桶、熔炉、发射器等，按容器所在位置判授权，兜底各种打开途径 */
    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        Location location = event.getInventory().getLocation();
        if (location == null) {
            // 玩家自己的背包、创造模式物品栏等没有位置，与领地无关
            return;
        }
        LandRegion region = landManager.regionAt(location);
        if (region != null && !landManager.isAuthorized(player, region)) {
            player.sendMessage("此为他人领地，禁止操作");
            event.setCancelled(true);
        }
    }

    /** 实体爆炸（苦力怕、TNT、恶魂火球、末影水晶、末影龙）：领地一格不掉 */
    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        removeProtectedBlocks(event.blockList());
    }

    /** 方块自身爆炸（床、重生锚），同上 */
    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        removeProtectedBlocks(event.blockList());
    }

    /** 实体改动方块：末影人搬砖、僵尸破门、牛羊吃草，落在领地一律取消 */
    @EventHandler
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (landManager.regionAt(event.getBlock().getLocation()) != null) {
            event.setCancelled(true);
        }
    }

    /** 液体渗入：只拦“源在领地外、流进领地里”的越界，领地内自有水流照常 */
    @EventHandler
    public void onBlockFromTo(BlockFromToEvent event) {
        LandRegion from = landManager.regionAt(event.getBlock().getLocation());
        LandRegion to = landManager.regionAt(event.getToBlock().getLocation());
        if (from == null && to != null) {
            event.setCancelled(true);
        }
    }

    /** 点燃：领地里的火点不着，领主或白名单亲手用打火石点的放行 */
    @EventHandler
    public void onBlockIgnite(BlockIgniteEvent event) {
        LandRegion region = landManager.regionAt(event.getBlock().getLocation());
        if (region == null) {
            return;
        }
        Player igniter = event.getPlayer();
        if (igniter != null && landManager.isAuthorized(igniter, region)) {
            return;
        }
        event.setCancelled(true);
    }

    /** 燃烧蔓延到领地其它方块时取消 */
    @EventHandler
    public void onBlockBurn(BlockBurnEvent event) {
        if (landManager.regionAt(event.getBlock().getLocation()) != null) {
            event.setCancelled(true);
        }
    }

    /** 蔓延与生成：火势蔓延、草蔓延、混凝土凝固、积雪结冰在领地内取消，树叶腐烂不拦 */
    @EventHandler
    public void onBlockForm(BlockFormEvent event) {
        if (landManager.regionAt(event.getBlock().getLocation()) != null) {
            event.setCancelled(true);
        }
    }

    /** 活塞推出：落点会进领地的直接取消，界外活塞推不进来 */
    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (movesIntoRegion(event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    /** 活塞拉回：领地方块也不能被界外活塞拉出去 */
    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (movesIntoRegion(event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    /** 展示框和画：领地里的悬挂实体不让被怪物、爆炸或箭矢破坏，领主亲手拆的放行 */
    @EventHandler
    public void onHangingBreak(HangingBreakEvent event) {
        LandRegion region = landManager.regionAt(event.getEntity().getLocation());
        if (region == null) {
            return;
        }
        if (event instanceof HangingBreakByEntityEvent) {
            Entity remover = ((HangingBreakByEntityEvent) event).getRemover();
            if (remover instanceof Player && landManager.isAuthorized((Player) remover, region)) {
                return;
            }
        }
        event.setCancelled(true);
    }

    /** 爆炸列表里落在领地方块一律剔除，实现“炸得动生物、炸不掉地形” */
    private void removeProtectedBlocks(List<Block> blocks) {
        blocks.removeIf(block -> landManager.regionAt(block.getLocation()) != null);
    }

    /** 活塞移动判定：只看移动后的落点是否落进领地，领地内自己推自己不受影响 */
    private boolean movesIntoRegion(List<Block> blocks, BlockFace direction) {
        int dx = direction.getModX();
        int dy = direction.getModY();
        int dz = direction.getModZ();
        for (Block block : blocks) {
            Location origin = block.getLocation();
            if (landManager.regionAt(origin) != null
                    || landManager.regionAt(origin.add(dx, dy, dz)) != null) {
                return true;
            }
        }
        return false;
    }
}
