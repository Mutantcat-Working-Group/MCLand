package org.mutantcat.mcland262.land;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.mutantcat.mcland262.user.AuthManager;
import org.mutantcat.mcland262.user.UserDatabase;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * 圈地系统核心：木铲两点选点 → /land 确认 → /land sum 算价 → /land sure 扣款买地，
 * 外加白名单管理和领地查询。全部领地启动时载入内存，运行时只做内存判定。
 *
 * 流程约定：木铲右键先缓存第一个点（未输入 /land 前每次点击都换第一个点）；
 * 输入 /land 后开始选第二个点，可随时更换；/land off 随时终止并收起粒子。
 */
public class LandManager implements AutoCloseable {
    /** 绕过领地保护的权限节点（OP 天生拥有） */
    public static final String BYPASS_PERMISSION = "mcland.land.bypass";
    private static final String PREFIX = "[圈地]";
    private static final long PARTICLE_PERIOD_TICKS = 10L;

    private final JavaPlugin plugin;
    private final AuthManager authManager;
    private final UserDatabase userDatabase;
    private final LandDatabase database;
    private final long pricePerBlock;
    private final int minSide;
    private final SpawnRect spawnRect;

    private final List<LandRegion> lands = new ArrayList<>();
    private final Map<Long, Set<String>> whitelists = new HashMap<>();
    private final Map<UUID, Selection> selections = new HashMap<>();

    /** 主城保护区换算成的水平矩形；主城没加载时为 null，表示买地不与主城判重 */
    public record SpawnRect(String world, int minX, int maxX, int minZ, int maxZ) {}

    /** 每个玩家一份的选点状态；粒子任务在 /land 确认后才启动 */
    static final class Selection {
        Location first;
        boolean confirmed;
        Location second;
        BukkitTask particleTask;
    }

    /** 圈地区域的中间校验结果 */
    private record Rect(String world, int minX, int maxX, int minZ, int maxZ, int width, int depth) {
        long area() {
            return (long) width * depth;
        }
    }

    public LandManager(JavaPlugin plugin, AuthManager authManager, UserDatabase userDatabase,
                       long pricePerBlock, int minSide, SpawnRect spawnRect) throws SQLException {
        this.plugin = plugin;
        this.authManager = authManager;
        this.userDatabase = userDatabase;
        this.pricePerBlock = Math.max(1L, pricePerBlock);
        this.minSide = Math.max(1, minSide);
        this.spawnRect = spawnRect;
        this.database = new LandDatabase(plugin);
        this.lands.addAll(database.listAll());
        this.whitelists.putAll(database.loadWhitelists());
        for (LandRegion region : lands) {
            if (Bukkit.getWorld(region.world()) == null) {
                plugin.getLogger().warning("领地 #" + region.id() + " 所在世界 \"" + region.world()
                        + "\" 不存在，该领地的保护暂不生效");
            }
        }
        plugin.getLogger().info("圈地系统已加载 " + lands.size() + " 块领地");
    }

    // ===== 选点流程 =====

    /** 木铲右键选点：未确认时每次点击都换第一个点，确认后每次点击都换第二个点 */
    public void handleShovelClick(Player player, Location clicked) {
        Selection selection = selections.computeIfAbsent(player.getUniqueId(), key -> new Selection());
        if (!selection.confirmed) {
            selection.first = clicked.clone();
            selection.second = null;
            player.sendMessage(PREFIX + "已选择第一个点：" + worldName(clicked) + " " + pos(clicked));
            player.sendMessage(PREFIX + "确认要圈地请输入 /land，再次点击木铲可更换第一个点");
            return;
        }
        selection.second = clicked.clone();
        player.sendMessage(PREFIX + "已选择第二个点：" + worldName(clicked) + " " + pos(clicked));
        player.sendMessage(PREFIX + "输入 /land sum 查看区域与价格，输入 /land off 取消本次圈地");
    }

    /** /land 裸命令：没有第一点时引导选点；未确认时确认开圈并起粒子；已确认时重绘选区 */
    public void startOrRedraw(Player player) {
        Selection selection = selections.get(player.getUniqueId());
        if (selection == null || selection.first == null) {
            player.sendMessage(PREFIX + "请先用木铲右键点击一个方块选择第一个点");
            return;
        }
        if (!selection.confirmed) {
            selection.confirmed = true;
            selection.second = null;
            startParticles(player);
            player.sendMessage(PREFIX + "已开始圈地，请用木铲右键选择第二个点，可随时点击更换");
            player.sendMessage(PREFIX + "输入 /land sum 查看所选区域与价格，输入 /land off 取消并隐藏粒子效果");
            return;
        }
        startParticles(player);
        if (selection.second == null) {
            player.sendMessage(PREFIX + "正在圈地，请用木铲右键选择第二个点，输入 /land off 取消");
        } else {
            player.sendMessage(PREFIX + "正在圈地，可继续点击木铲更换第二个点，输入 /land sum 查看区域与价格");
        }
    }

    /** /land off：终止本次圈地，收起粒子 */
    public void off(Player player) {
        Selection selection = selections.remove(player.getUniqueId());
        if (selection != null && selection.particleTask != null) {
            selection.particleTask.cancel();
        }
        player.sendMessage(PREFIX + "已终止本次圈地，粒子效果已隐藏");
    }

    /** 玩家退服时清掉选点和粒子任务，避免任务残留 */
    public void clearSelection(UUID uuid) {
        Selection selection = selections.remove(uuid);
        if (selection != null && selection.particleTask != null) {
            selection.particleTask.cancel();
        }
    }

    // ===== 计价与购买 =====

    /** /land sum：列出两点坐标、区域大小、价格和余额，并引导 /land sure */
    public void showSum(Player player) {
        Selection selection = selections.get(player.getUniqueId());
        if (selection == null || selection.first == null) {
            player.sendMessage(PREFIX + "请先用木铲右键点击一个方块选择第一个点，再输入 /land 开始圈地");
            return;
        }
        if (!selection.confirmed) {
            player.sendMessage(PREFIX + "请先输入 /land 确认圈地，然后用木铲选择第二个点");
            return;
        }
        if (selection.second == null) {
            player.sendMessage(PREFIX + "还没有选择第二个点，请用木铲右键点击目标位置");
            return;
        }
        Rect rect = validate(player, selection);
        if (rect == null) {
            return;
        }
        long price = rect.area() * pricePerBlock;
        player.sendMessage(PREFIX + "第一个点：" + worldName(selection.first) + " " + pos(selection.first)
                + "，第二个点：" + worldName(selection.second) + " " + pos(selection.second));
        player.sendMessage(PREFIX + "区域大小：" + rect.width() + " × " + rect.depth()
                + "（长 " + rect.width() + " 格、宽 " + rect.depth() + " 格），共 " + rect.area() + " 格");
        player.sendMessage(PREFIX + "购买价格：" + price + " 金币（每格 " + pricePerBlock + " 金币）");
        long balance = balanceOf(player);
        if (balance < 0) {
            return;
        }
        if (balance < price) {
            player.sendMessage(PREFIX + "当前余额：" + balance + " 金币，还差 " + (price - balance) + " 金币");
            return;
        }
        player.sendMessage(PREFIX + "当前余额：" + balance + " 金币，确认购买请输入 /land sure，取消请输入 /land off");
    }

    /** /land sure：复检区域与余额后买地，成功扣款、插库、收粒子，并全服广播 */
    public void purchase(Player player) {
        Selection selection = selections.get(player.getUniqueId());
        if (selection == null || selection.first == null) {
            player.sendMessage(PREFIX + "请先用木铲右键点击一个方块选择第一个点，再输入 /land 开始圈地");
            return;
        }
        Rect rect = validate(player, selection);
        if (rect == null) {
            return;
        }
        long price = rect.area() * pricePerBlock;
        long balance = balanceOf(player);
        if (balance < 0 || balance < price) {
            if (balance >= 0) {
                player.sendMessage(PREFIX + "金币不足，需要 " + price + " 金币，当前余额 " + balance
                        + "，还差 " + (price - balance) + " 金币");
            }
            return;
        }
        long landId = -1L;
        long result = 0L;
        try {
            // 先落地再扣款：万一扣款失败，按 id 删掉这块地，不会出现“扣了钱没拿到地”
            landId = database.insertLand(player.getUniqueId().toString(), player.getName(),
                    rect.world(), rect.minX(), rect.maxX(), rect.minZ(), rect.maxZ());
            if (landId <= 0) {
                player.sendMessage(PREFIX + "圈地失败，请稍后重试或联系管理员");
                return;
            }
            result = userDatabase.spend(player.getUniqueId(), price);
            if (result < 0) {
                database.deleteLand(landId);
                sendSpendFailure(player, result, price);
                return;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "圈地写入数据库失败", e);
            player.sendMessage(PREFIX + "圈地失败，请联系管理员检查服务器日志");
            try {
                if (landId > 0) {
                    // 扣款环节抛异常也要把刚插的地删掉，别留下没扣过钱的领地
                    database.deleteLand(landId);
                }
            } catch (SQLException rollbackError) {
                plugin.getLogger().log(Level.SEVERE,
                        "圈地扣款失败后回滚插入数据也失败，land_id=" + landId, rollbackError);
            }
            return;
        }
        lands.add(new LandRegion(landId, player.getUniqueId().toString(), player.getName(),
                rect.world(), rect.minX(), rect.maxX(), rect.minZ(), rect.maxZ()));
        // 购买成功：静默收掉选点和粒子，不再补一条“已终止圈地”的提示
        clearSelection(player.getUniqueId());
        player.sendMessage(PREFIX + "购买成功，已花费 " + price + " 金币圈下 " + rect.width() + " × "
                + rect.depth() + " 的地，剩余余额 " + result + " 金币");
        Bukkit.broadcastMessage(PREFIX + player.getName() + " 圈下了一块 " + rect.width()
                + " × " + rect.depth() + " 的领地");
    }

    // ===== 白名单 =====

    /** /land whitelist add/remove/list：只允许管理自己脚下的领地 */
    public void handleWhitelist(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(PREFIX + "请输入“/land whitelist add 玩家名”添加白名单，"
                    + "或“/land whitelist remove 玩家名”移出白名单，“/land whitelist list”查看白名单");
            return;
        }
        LandRegion region = regionAt(player.getLocation());
        if (region == null) {
            player.sendMessage(PREFIX + "当前位置不在任何领地内");
            return;
        }
        if (!region.ownerUuid().equals(player.getUniqueId().toString())) {
            player.sendMessage(PREFIX + "只能管理自己领地的白名单");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("list")) {
            showWhitelist(player, region);
            return;
        }
        if (args.length != 3) {
            player.sendMessage(PREFIX + "请输入“/land whitelist add 玩家名”或“/land whitelist remove 玩家名”");
            return;
        }
        String targetName = stripAt(args[2]);
        if (targetName.isEmpty()) {
            player.sendMessage(PREFIX + "请输入要操作的玩家名，例如“/land whitelist add @对方名字”");
            return;
        }
        String lowerName = targetName.toLowerCase(Locale.ROOT);
        Set<String> whitelist = whitelists.computeIfAbsent(region.id(), key -> new java.util.HashSet<>());
        if (action.equals("add")) {
            if (lowerName.equals(player.getName().toLowerCase(Locale.ROOT))) {
                player.sendMessage(PREFIX + "不需要把自己加入白名单，领地本来就是你的");
                return;
            }
            if (whitelist.contains(lowerName)) {
                player.sendMessage(PREFIX + targetName + " 已在白名单中");
                return;
            }
            try {
                database.addWhitelist(region.id(), targetName);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "写入领地白名单失败", e);
                player.sendMessage(PREFIX + "写入白名单失败，请联系管理员检查服务器日志");
                return;
            }
            whitelist.add(lowerName);
            player.sendMessage(PREFIX + "已将 " + targetName + " 加入本领地白名单");
            return;
        }
        if (action.equals("remove")) {
            if (!whitelist.contains(lowerName)) {
                player.sendMessage(PREFIX + targetName + " 不在白名单中");
                return;
            }
            try {
                database.removeWhitelist(region.id(), lowerName);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "移出领地白名单失败", e);
                player.sendMessage(PREFIX + "移出白名单失败，请联系管理员检查服务器日志");
                return;
            }
            whitelist.remove(lowerName);
            player.sendMessage(PREFIX + "已将 " + targetName + " 移出本领地白名单");
            return;
        }
        player.sendMessage(PREFIX + "未知操作，请输入“/land whitelist add 玩家名”或“/land whitelist remove 玩家名”");
    }

    private void showWhitelist(Player player, LandRegion region) {
        List<String> names;
        try {
            names = database.listWhitelist(region.id());
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "读取领地白名单失败", e);
            player.sendMessage(PREFIX + "读取白名单失败，请联系管理员检查服务器日志");
            return;
        }
        if (names.isEmpty()) {
            player.sendMessage(PREFIX + "本领地白名单为空");
            return;
        }
        player.sendMessage(PREFIX + "本领地白名单：" + String.join("、", names));
    }

    // ===== 查询 =====

    /** 位置所在的领地；不在任何领地内返回 null */
    public LandRegion regionAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return regionAt(location.getWorld().getName(), location.getBlockX(), location.getBlockZ());
    }

    /** 水平坐标所在的领地；不在任何领地内返回 null */
    public LandRegion regionAt(String worldName, int x, int z) {
        if (worldName == null) {
            return null;
        }
        for (LandRegion region : lands) {
            if (region.contains(worldName, x, z)) {
                return region;
            }
        }
        return null;
    }

    /** 领地主本人、白名单玩家、OP 或持 bypass 权限者视为已授权 */
    public boolean isAuthorized(Player player, LandRegion region) {
        if (region == null || player == null) {
            return false;
        }
        return player.isOp()
                || player.hasPermission(BYPASS_PERMISSION)
                || region.ownerUuid().equals(player.getUniqueId().toString())
                || whitelists.getOrDefault(region.id(), Set.of())
                        .contains(player.getName().toLowerCase(Locale.ROOT));
    }

    // ===== 内部 =====

    /** 给粒子任务读实时选点状态用 */
    Selection selectionOf(UUID uuid) {
        return selections.get(uuid);
    }

    private void startParticles(Player player) {
        Selection selection = selections.get(player.getUniqueId());
        if (selection == null || selection.particleTask != null) {
            return;
        }
        selection.particleTask = new LandParticleTask(this, player)
                .runTaskTimer(plugin, 0L, PARTICLE_PERIOD_TICKS);
    }

    /** 两点归一化成矩形并做全套校验：同世界、最小边长、与主城和已有领地不重叠；不合法时发送提示并返回 null */
    private Rect validate(Player player, Selection selection) {
        Location first = selection.first;
        Location second = selection.second;
        if (first.getWorld() == null || second.getWorld() == null
                || !first.getWorld().equals(second.getWorld())) {
            player.sendMessage(PREFIX + "两个点不在同一个世界，请重新选择");
            return null;
        }
        int minX = Math.min(first.getBlockX(), second.getBlockX());
        int maxX = Math.max(first.getBlockX(), second.getBlockX());
        int minZ = Math.min(first.getBlockZ(), second.getBlockZ());
        int maxZ = Math.max(first.getBlockZ(), second.getBlockZ());
        int width = maxX - minX + 1;
        int depth = maxZ - minZ + 1;
        if (width < minSide || depth < minSide) {
            player.sendMessage(PREFIX + "圈地至少 " + minSide + " × " + minSide
                    + "，当前为 " + width + " × " + depth + "，不符合要求");
            return null;
        }
        String worldName = first.getWorld().getName();
        if (spawnRect != null && spawnRect.world().equals(worldName)
                && minX <= spawnRect.maxX() && spawnRect.minX() <= maxX
                && minZ <= spawnRect.maxZ() && spawnRect.minZ() <= maxZ) {
            player.sendMessage(PREFIX + "所选区域与主城保护区重叠，请重新选择");
            return null;
        }
        LandRegion conflict = landOverlap(worldName, minX, maxX, minZ, maxZ);
        if (conflict != null) {
            player.sendMessage(PREFIX + "所选区域与 " + conflict.ownerName() + " 的领地重叠，请重新选择");
            return null;
        }
        return new Rect(worldName, minX, maxX, minZ, maxZ, width, depth);
    }

    private LandRegion landOverlap(String worldName, int minX, int maxX, int minZ, int maxZ) {
        for (LandRegion region : lands) {
            if (region.overlaps(worldName, minX, maxX, minZ, maxZ)) {
                return region;
            }
        }
        return null;
    }

    /** 查余额；未注册或查询失败时提示玩家并返回 -1 */
    private long balanceOf(Player player) {
        try {
            long balance = userDatabase.getBalance(player.getUniqueId());
            if (balance < 0) {
                player.sendMessage(PREFIX + "尚未查询到账号，请先注册登录");
            }
            return balance;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "查询金币余额失败", e);
            player.sendMessage(PREFIX + "查询金币余额失败，请联系管理员检查服务器日志");
            return -1L;
        }
    }

    private void sendSpendFailure(Player player, long result, long price) {
        if (result == -2L) {
            long balance = balanceOf(player);
            player.sendMessage(PREFIX + "金币不足，需要 " + price + " 金币，当前余额 "
                    + Math.max(0L, balance));
            return;
        }
        if (result == -1L) {
            player.sendMessage(PREFIX + "尚未查询到账号，请先注册登录");
            return;
        }
        player.sendMessage(PREFIX + "扣款失败，本次圈地已取消");
    }

    private static String worldName(Location location) {
        return location.getWorld() == null ? "未知世界" : location.getWorld().getName();
    }

    private static String pos(Location location) {
        return "(" + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ() + ")";
    }

    private static String stripAt(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        return trimmed.startsWith("@") ? trimmed.substring(1).trim() : trimmed;
    }

    @Override
    public void close() throws SQLException {
        for (Selection selection : selections.values()) {
            if (selection.particleTask != null) {
                selection.particleTask.cancel();
            }
        }
        selections.clear();
        database.close();
    }
}
