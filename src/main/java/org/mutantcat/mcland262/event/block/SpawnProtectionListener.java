package org.mutantcat.mcland262.event.block;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.mutantcat.mcland262.spawn.SpawnRegion;

import java.util.List;

/**
 * Author: tyza66
 * Date: 2024/4/21 19:12
 * Github: https://github.com/tyza66
 **/

/**
 * 主城保护，两层含义：
 * 1) 普通玩家在保护区域内不能放置也不能破坏方块，OP（或持有 mcland.spawnprotection.bypass 权限）直接放行，
 *    方便搭建和维护主城；
 * 2) 区域内的一切非玩家改动同样拦截：爆炸、实体改动方块、液体渗入、火焰、活塞推入、展示框与画被破坏，
 *    保证主城地形不会因为怪物和环境因素被改掉。
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

    /**
     * 实体爆炸：苦力怕、TNT、恶魂火球、末影水晶、末影龙都走这条。
     * 只把破坏列表里落在区域内的方块剔除，保留爆炸对生物的伤害——区域里照样会挨炸，但地形一格不动。
     */
    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        removeProtectedBlocks(event.blockList());
    }

    /** 方块自身爆炸（床、重生锚），处理方式同上 */
    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        removeProtectedBlocks(event.blockList());
    }

    /**
     * 实体改动方块：末影人拿起或放下方块、蠹虫潜进石头、劫掠兽啃树叶、僵尸破门、牛羊吃草。
     * 事件一取消，方块就保持原样。
     */
    @EventHandler
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (region.contains(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    /**
     * 液体渗入：只拦“源在区域外、流向区域里”这一种越界情况，
     * 区域内自有的水池和岩浆照常流动，OP 在主城里做水景不受影响。
     */
    @EventHandler
    public void onBlockFromTo(BlockFromToEvent event) {
        if (!region.contains(event.getBlock().getLocation())
                && region.contains(event.getToBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    /** 点燃：区域里点不着火（火球、闪电、火蔓延），OP 亲自用打火石点的放行 */
    @EventHandler
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (!region.contains(event.getBlock().getLocation())) {
            return;
        }
        if (canBypassIgniter(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
    }

    /** 燃烧：火苗蔓延到区域里其它方块时取消，火只能在原地烧完 */
    @EventHandler
    public void onBlockBurn(BlockBurnEvent event) {
        if (region.contains(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    /**
     * 蔓延与生成：火势蔓延、草蔓延、混凝土凝固、积雪结冰都在区域内取消，地形保持稳定。
     * 树叶腐烂故意不拦，OP 在主城种树不会留下一堆悬浮树叶。
     */
    @EventHandler
    public void onBlockForm(BlockFormEvent event) {
        if (region.contains(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    /** 活塞推出：移动后会落进区域里的方块直接取消，防止从界外把方块推进主城 */
    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (movesIntoRegion(event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    /** 活塞拉回：同上，界外活塞也不能把区域里的方块拉出去 */
    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (movesIntoRegion(event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    /**
     * 展示框和画：区域内的悬挂实体不让被怪物、爆炸或箭矢破坏。
     * OP 亲手拆的放行，和方块保护保持一致。
     */
    @EventHandler
    public void onHangingBreak(HangingBreakEvent event) {
        if (!region.contains(event.getEntity().getLocation())) {
            return;
        }
        if (event instanceof HangingBreakByEntityEvent) {
            Entity remover = ((HangingBreakByEntityEvent) event).getRemover();
            if (remover instanceof Player && canBypass((Player) remover)) {
                return;
            }
        }
        event.setCancelled(true);
    }

    /** 爆炸列表里落在区域内的方块一律剔除，实现“炸得动生物、炸不掉地形” */
    private void removeProtectedBlocks(List<Block> blocks) {
        blocks.removeIf(block -> region.contains(block.getLocation()));
    }

    /**
     * 活塞移动判定：只看移动后的落点是否落进区域，区域内自己推自己不受影响。
     * 落点用偏移量直接算，不 clone 每个方块；活塞事件触发频率低，这点开销无所谓。
     */
    private boolean movesIntoRegion(List<Block> blocks, BlockFace direction) {
        int dx = direction.getModX();
        int dy = direction.getModY();
        int dz = direction.getModZ();
        for (Block block : blocks) {
            Location origin = block.getLocation();
            if (region.contains(origin) || region.contains(origin.add(dx, dy, dz))) {
                return true;
            }
        }
        return false;
    }

    /** OP 或被授权的建造者不受保护限制 */
    private boolean canBypass(Player player) {
        return player.isOp() || player.hasPermission(BYPASS_PERMISSION);
    }

    /** 点燃者不是玩家（火球、闪电）时一律拦截；是玩家才按 OP/权限放行 */
    private boolean canBypassIgniter(Player player) {
        return player != null && canBypass(player);
    }
}
