package org.mutantcat.mcland262.spawn;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 主城守卫：进入保护区域的敌对生物直接秒杀，静默执行不播报，避免刷屏。
 *
 * 判定选用 Enemy 而不是 Monster：恶魂、幻翼、史莱姆、猪灵兽和末影龙都实现了 Enemy 但不是 Monster，
 * 用 Enemy 覆盖面更准，且同样只做一次 instanceof 判断，资源消耗最小。
 *
 * 秒杀不掉落：击杀前先把实体登记进免掉落集合，死亡事件里清空掉落物和经验，
 * 玩家自己在区域内正常击杀的怪物不登记，掉落照常。
 */
public class SpawnGuardTask extends BukkitRunnable implements Listener {
    /** 登记后等待死亡事件消费的实体；正常情况下同一 tick 内登记即消费 */
    private final Set<UUID> noDrop = ConcurrentHashMap.newKeySet();

    private final SpawnRegion region;

    public SpawnGuardTask(SpawnRegion region) {
        this.region = region;
    }

    @Override
    public void run() {
        World world = region.getWorld();
        if (world == null) {
            return;
        }
        // 清掉已被其他插件救回（取消了死亡）的残留登记，避免集合无限增长
        noDrop.removeIf(uuid -> Bukkit.getEntity(uuid) == null);

        Location center = region.getCenter();
        // 垂直半径取整个世界的可建造高度，查询盒只需要框住水平范围，
        // 真正的区域判定交给 contains 精确执行，模糊查询 + 精确判定最省资源
        double yRadius = Math.max(1.0, world.getLogicalHeight());
        for (Entity entity :
                world.getNearbyEntities(center, region.getRadius(), yRadius, region.getRadius(),
                        candidate -> candidate instanceof Enemy)) {
            if (!region.contains(entity.getLocation())) {
                continue;
            }
            noDrop.add(entity.getUniqueId());
            ((LivingEntity) entity).setHealth(0.0);
        }
    }

    /** 被守卫秒杀的怪物：清空掉落物和经验，实现“杀但不掉东西” */
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (noDrop.remove(event.getEntity().getUniqueId())) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }
}
