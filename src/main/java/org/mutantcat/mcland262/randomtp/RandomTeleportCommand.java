package org.mutantcat.mcland262.randomtp;

import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.mutantcat.mcland262.user.AuthManager;

import java.util.EnumSet;
import java.util.Random;
import java.util.Set;

/**
 * /random 随机传送：以玩家当前位置为圆心，在最小/最大水平距离内随机取样，
 * 取第一个安全落点：脚下是实心地表，头顶天空通透（露天开阔地带，矿洞、封闭空间与茂密树冠下不作数），
 * 身体两格可站立，排除水面、天空、方块内、水中与岩浆。
 */
public class RandomTeleportCommand implements CommandExecutor {
    private static final String PREFIX = "[MCLand]";

    /** 站立会受伤或异常的方块，脚下与身体范围内直接排除 */
    private static final Set<Material> DANGEROUS_GROUND = EnumSet.of(
            Material.MAGMA_BLOCK, Material.CACTUS, Material.SWEET_BERRY_BUSH,
            Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.FIRE, Material.SOUL_FIRE,
            Material.POWDER_SNOW, Material.WITHER_ROSE);

    private final Random random = new Random();
    private final AuthManager authManager;
    private final int minDistance;
    private final int maxDistance;
    private final int maxAttempts;

    public RandomTeleportCommand(AuthManager authManager, int minDistance, int maxDistance, int maxAttempts) {
        this.authManager = authManager;
        this.minDistance = Math.max(0, Math.min(minDistance, maxDistance));
        this.maxDistance = Math.max(1, maxDistance);
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(PREFIX + "该命令仅限玩家使用");
            return true;
        }
        Player player = (Player) sender;
        if (args.length > 0) {
            player.sendMessage(PREFIX + "该命令无需额外参数，请输入“/random”");
            return true;
        }
        if (authManager != null && !authManager.isLoggedIn(player.getUniqueId())) {
            player.sendMessage(PREFIX + "请先登录后使用随机传送");
            return true;
        }
        Location origin = player.getLocation();
        Location destination = findSafeLocation(origin);
        if (destination == null) {
            player.sendMessage(PREFIX + "未能在附近找到安全落脚点，请稍后再试");
            return true;
        }
        double dx = destination.getBlockX() - origin.getBlockX();
        double dz = destination.getBlockZ() - origin.getBlockZ();
        int horizontalDistance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        if (player.teleport(destination)) {
            player.sendMessage(PREFIX + "已随机传送至安全地面（" + destination.getBlockX() + ", "
                    + destination.getBlockY() + ", " + destination.getBlockZ() + "），距原位置约"
                    + horizontalDistance + "格");
        } else {
            player.sendMessage(PREFIX + "随机传送失败，请稍后再试");
        }
        return true;
    }

    /** 在范围内随机取样，返回第一个通过安全检查的落点；多次尝试都找不到则返回 null */
    private Location findSafeLocation(Location origin) {
        World world = origin.getWorld();
        if (world == null) {
            return null;
        }
        int cx = origin.getBlockX();
        int cz = origin.getBlockZ();
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = minDistance + random.nextDouble() * (maxDistance - minDistance);
            int x = cx + (int) Math.round(Math.cos(angle) * distance);
            int z = cz + (int) Math.round(Math.sin(angle) * distance);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                world.getChunkAt(x, z); // 同步加载目标区块后再取地表高度
            }
            Block ground = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            if (!hasSkyAccess(world, x, ground.getY() + 1, z)) {
                continue; // 头顶不通透，说明是矿洞、封闭空间或树冠下，不算露天“地面”
            }
            Block feet = world.getBlockAt(x, ground.getY() + 1, z);
            Block head = world.getBlockAt(x, ground.getY() + 2, z);
            if (isSafeGround(ground) && isStandable(feet) && isStandable(head)) {
                return new Location(world, x + 0.5, ground.getY() + 1, z + 0.5,
                        origin.getYaw(), origin.getPitch());
            }
        }
        return null;
    }

    /**
     * 头顶一层直到世界顶部都必须通透（空气或花草、火把这类可通行方块）。
     * 这条硬约束保证落点落在露天开阔地带的地表，而不是矿洞、封闭矿穴或室内。
     */
    private boolean hasSkyAccess(World world, int x, int y, int z) {
        int maxHeight = world.getMaxHeight();
        for (int currentY = y; currentY < maxHeight; currentY++) {
            Block above = world.getBlockAt(x, currentY, z);
            if (!(above.isEmpty() || above.isPassable())) {
                return false;
            }
        }
        return true;
    }

    /** 脚下必须是实心方块，且不是水、岩浆或会伤人的方块（水面与天空由此排除） */
    private boolean isSafeGround(Block ground) {
        Material material = ground.getType();
        return material.isSolid()
                && material != Material.WATER
                && material != Material.LAVA
                && !ground.isLiquid()
                && !DANGEROUS_GROUND.contains(material);
    }

    /** 身体两格必须可站立：非实心阻挡、非水非岩浆、非危险方块（方块内由此排除） */
    private boolean isStandable(Block block) {
        Material material = block.getType();
        return !material.isSolid()
                && !block.isLiquid()
                && material != Material.WATER
                && material != Material.LAVA
                && !DANGEROUS_GROUND.contains(material);
    }
}
