package org.mutantcat.mcland262.sell;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.mutantcat.mcland262.user.AuthManager;
import org.mutantcat.mcland262.user.UserDatabase;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * 交易系统：/sell 查看价目，/sell sum 计算手中物品总价，/sell sure 把手中物品换成金币。
 * 价目只在 SellItem 一处维护，价目展示、估价、成交都从同一张表取价，不会出现报价和成交价不一致。
 * 卖出后物品直接消失且无法赎回，所以每一步提示都带无法赎回的提醒。
 */
public class SellCommand implements CommandExecutor {
    private static final String PREFIX = "[交易系统]";

    /** 可出售物品与单价（金币），按展示顺序排列 */
    private static final List<SellItem> PRICE_LIST = List.of(
            new SellItem(Material.COBBLESTONE, "原石", 1L),
            new SellItem(Material.IRON_INGOT, "铁锭", 10L),
            new SellItem(Material.EMERALD, "绿宝石", 25L),
            new SellItem(Material.REDSTONE, "红石", 15L),
            new SellItem(Material.LAPIS_LAZULI, "青金石", 20L),
            new SellItem(Material.COPPER_INGOT, "铜锭", 8L),
            new SellItem(Material.GOLD_INGOT, "金锭", 50L),
            new SellItem(Material.DIAMOND, "钻石", 100L));

    private static final Map<Material, SellItem> PRICE_BY_MATERIAL = buildPriceIndex();

    private final JavaPlugin plugin;
    private final AuthManager authManager;
    private final UserDatabase userDatabase;

    public SellCommand(JavaPlugin plugin, AuthManager authManager, UserDatabase userDatabase) {
        this.plugin = plugin;
        this.authManager = authManager;
        this.userDatabase = userDatabase;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(PREFIX + "该命令仅限玩家使用");
            return true;
        }
        Player player = (Player) sender;
        if (authManager != null && !authManager.isLoggedIn(player.getUniqueId())) {
            player.sendMessage(PREFIX + "请先登录后使用交易功能");
            return true;
        }
        if (userDatabase == null) {
            player.sendMessage(PREFIX + "交易系统未启用");
            return true;
        }
        if (args.length == 0) {
            sendPriceList(player);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "sum":
                showSum(player);
                return true;
            case "sure":
                sell(player);
                return true;
            default:
                sendPriceList(player);
                return true;
        }
    }

    /** /sell：价目、两条子命令、无法赎回的提醒一次说清 */
    private void sendPriceList(Player player) {
        player.sendMessage(PREFIX + "========== 交易所 ==========");
        player.sendMessage(PREFIX + "/sell sum - 计算当前手中物品总价");
        player.sendMessage(PREFIX + "/sell sure - 卖掉当前手中的物品");
        player.sendMessage(PREFIX + "可出售物品与单价：" + priceListText());
        player.sendMessage(PREFIX + "注意：物品卖出后无法赎回，确认无误再输入/sell sure");
    }

    private static String priceListText() {
        StringBuilder builder = new StringBuilder();
        for (SellItem item : PRICE_LIST) {
            if (builder.length() > 0) {
                builder.append("，");
            }
            builder.append(item.displayName()).append(item.price()).append("金币");
        }
        return builder.toString();
    }

    /** /sell sum：只估价不动物品，估价完提示确认命令与无法赎回 */
    private void showSum(Player player) {
        ItemStack stack = itemInHand(player);
        if (stack == null) {
            player.sendMessage(PREFIX + emptyHandHint());
            return;
        }
        SellItem item = PRICE_BY_MATERIAL.get(stack.getType());
        if (item == null) {
            player.sendMessage(PREFIX + unsupportedHint());
            return;
        }
        long total = item.price() * stack.getAmount();
        player.sendMessage(PREFIX + "当前手中物品：" + item.displayName() + " x" + stack.getAmount()
                + "，单价" + item.price() + "金币，总价" + total + "金币");
        player.sendMessage(PREFIX + "确认卖出请输入/sell sure，卖出后无法赎回");
    }

    /** /sell sure：按当前手中物品成交，先入账再收物品，避免货没了钱没到 */
    private void sell(Player player) {
        ItemStack stack = itemInHand(player);
        if (stack == null) {
            player.sendMessage(PREFIX + emptyHandHint());
            return;
        }
        SellItem item = PRICE_BY_MATERIAL.get(stack.getType());
        if (item == null) {
            player.sendMessage(PREFIX + unsupportedHint());
            return;
        }
        int amount = stack.getAmount();
        long total = item.price() * amount;

        long balance;
        try {
            balance = userDatabase.addBalance(player.getUniqueId(), total);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "出售物品入账失败", e);
            player.sendMessage(PREFIX + "出售失败，请联系管理员检查服务器日志");
            return;
        }
        if (balance < 0) {
            player.sendMessage(PREFIX + "尚未查询到账号，请先注册登录");
            return;
        }
        // 入账成功才收物品，中途失败玩家至少还拿着东西
        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        player.sendMessage(PREFIX + "已卖出" + item.displayName() + " x" + amount + "，获得" + total
                + "金币，当前余额为" + balance);
    }

    /** 手中没有可卖的东西：与“不支持的物品”区分开，提示拿在手上而不是报价 */
    private static String emptyHandHint() {
        return "请先将要出售的物品拿在手中，输入/sell 查看可出售物品与价钱";
    }

    private static String unsupportedHint() {
        return "此商品不支持出售，输入/sell 查看可出售物品与价钱";
    }

    private static ItemStack itemInHand(Player player) {
        ItemStack stack = player.getInventory().getItemInMainHand();
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return null;
        }
        return stack;
    }

    private static Map<Material, SellItem> buildPriceIndex() {
        Map<Material, SellItem> index = new LinkedHashMap<>();
        for (SellItem item : PRICE_LIST) {
            index.put(item.material(), item);
        }
        return Map.copyOf(index);
    }

    /** 价目条目：物品、展示名、单价（金币） */
    private record SellItem(Material material, String displayName, long price) {}
}
