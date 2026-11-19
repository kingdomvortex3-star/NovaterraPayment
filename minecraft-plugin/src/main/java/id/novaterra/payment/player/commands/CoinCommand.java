
package id.novaterra.payment.player.commands;

import id.novaterra.payment.NovaTerraPayment;
import id.novaterra.payment.economy.CoinManager;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CoinCommand implements CommandExecutor, Listener {

    private final NovaTerraPayment plugin;
    private final CoinManager coinManager;

    public CoinCommand(
            NovaTerraPayment plugin,
            CoinManager coinManager
    ) {
        this.plugin = plugin;
        this.coinManager = coinManager;
    }

    // ==========================================
    // COMMAND
    // ==========================================

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Perintah ini hanya untuk pemain.");
            return true;
        }

        openMainMenu(player);
        return true;
    }

    // ==========================================
    // MAIN COIN MARKET
    // ==========================================

    private void openMainMenu(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            long balance;

            try {
                coinManager.createAccount(
                        player.getUniqueId(),
                        player.getName()
                );

                balance = coinManager.getBalance(
                        player.getUniqueId()
                );

            } catch (Exception exception) {
                plugin.getLogger().severe(
                        "Failed to load Coin balance: "
                                + exception.getMessage()
                );

                Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage(color(
                                "&cGagal mengambil saldo Coin."
                        ))
                );
                return;
            }

            long finalBalance = balance;

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;

                CoinHolder holder = new CoinHolder(MenuType.MAIN);

                Inventory inventory = Bukkit.createInventory(
                        holder,
                        45,
                        color("&8ᴄᴏɪɴ ᴍᴀʀᴋᴇᴛ &8| &bɴᴏᴠᴀᴛᴇʀʀᴀ")
                );

                holder.setInventory(inventory);

                inventory.setItem(4, item(
                        Material.GOLD_INGOT,
                        "&6&lᴍʏ ᴄᴏɪɴ ʙᴀʟᴀɴᴄᴇ",
                        List.of(
                                "&7Your current balance:",
                                "",
                                "&e✦ &f" + format(finalBalance) + " ᴄᴏɪɴ",
                                "",
                                "&8ᴘᴀʏᴍᴇɴᴛ ᴡᴀʟʟᴇᴛ"
                        )
                ));

                inventory.setItem(20, item(
                        Material.DIAMOND_BLOCK,
                        "&b&lʀᴀɴᴋ ѕʜᴏᴘ",
                        List.of(
                                "&7Unlock exclusive server ranks.",
                                "",
                                "&b✦ &f9 Exclusive Ranks",
                                "&b✦ &fPermanent Rank Benefits",
                                "",
                                "&e➜ Click to browse ranks"
                        )
                ));

                inventory.setItem(22, item(
                        Material.EMERALD_BLOCK,
                        "&a&lᴄʟᴀɪᴍ ʙʟᴏᴄᴋ",
                        List.of(
                                "&7Protect your land with Claim Blocks.",
                                "",
                                "&a✦ &fFuture Marketplace Category",
                                "",
                                "&c✘ &lCOMING SOON",
                                "",
                                "&e➜ Click for information"
                        )
                ));

                inventory.setItem(24, item(
                        Material.CREEPER_SPAWN_EGG,
                        "&d&lᴘᴇᴛѕ ѕʜᴏᴘ",
                        List.of(
                                "&7Find your own loyal companions.",
                                "",
                                "&d✦ &fFuture Marketplace Category",
                                "",
                                "&c✘ &lCOMING SOON",
                                "",
                                "&e➜ Click for information"
                        )
                ));

                inventory.setItem(31, item(
                        Material.AMETHYST_SHARD,
                        "&5&lᴄᴏѕᴍᴇᴛɪᴄ ѕʜᴏᴘ",
                        List.of(
                                "&7Customize your character.",
                                "",
                                "&5✦ &fFuture Marketplace Category",
                                "",
                                "&c✘ &lCOMING SOON",
                                "",
                                "&e➜ Click for information"
                        )
                ));

                inventory.setItem(40, item(
                        Material.SUNFLOWER,
                        "&e&lᴛᴏᴘ ᴜᴘ ᴄᴏɪɴ",
                        List.of(
                                "&7Need more Coins?",
                                "",
                                "&ePurchase Coins through",
                                "&eour official Discord store.",
                                "",
                                "&bᴅɪѕᴄᴏʀᴅ: &fNOVATERRA"
                        )
                ));

                fillEmpty(inventory);
                player.openInventory(inventory);
            });
        });
    }

    // ==========================================
    // RANK SHOP
    // ==========================================

    private void openRankMenu(Player player) {
        if (!plugin.getConfig().getBoolean(
                "rank-shop.enabled",
                true
        )) {
            player.sendMessage(color(
                    "&cRank Shop sedang dinonaktifkan."
            ));
            return;
        }

        var section = plugin.getConfig()
                .getConfigurationSection("rank-shop.ranks");

        if (section == null || section.getKeys(false).isEmpty()) {
            player.sendMessage(color(
                    "&cDaftar rank belum dikonfigurasi."
            ));
            return;
        }

        CoinHolder holder = new CoinHolder(MenuType.RANKS);

        Inventory inventory = Bukkit.createInventory(
                holder,
                54,
                color("&8ʀᴀɴᴋ ѕʜᴏᴘ &8| &bɴᴏᴠᴀᴛᴇʀʀᴀ")
        );

        holder.setInventory(inventory);

        int[] slots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25
        };

        int index = 0;

        for (String rankId : section.getKeys(false)) {
            if (index >= slots.length) break;

            String path = "rank-shop.ranks." + rankId;

            String name = plugin.getConfig().getString(
                    path + ".name",
                    rankId
            );

            long price = plugin.getConfig().getLong(
                    path + ".price",
                    0L
            );

            if (price <= 0) continue;

            Material icon = getRankIcon(rankId);

            inventory.setItem(
                    slots[index],
                    item(
                            icon,
                            getRankColor(rankId) + "&l" + smallCaps(name),
                            List.of(
                                    "&8━━━━━━━━━━━━━━━━",
                                    "&7Rank: " + getRankColor(rankId) + name,
                                    "&7Price: &e" + format(price) + " Coin",
                                    "",
                                    "&a✦ &fExclusive Rank",
                                    "&a✦ &fPermanent Benefits",
                                    "",
                                    "&e➜ Click to purchase",
                                    "&8━━━━━━━━━━━━━━━━"
                            )
                    )
            );

            holder.setRankSlot(slots[index], rankId);
            index++;
        }

        inventory.setItem(49, item(
                Material.ARROW,
                "&c&lʙᴀᴄᴋ",
                List.of(
                        "&7Return to Coin Market."
                )
        ));

        inventory.setItem(4, item(
                Material.NETHER_STAR,
                "&b&lᴇxᴄʟᴜѕɪᴠᴇ ʀᴀɴᴋѕ",
                List.of(
                        "&7Choose your desired rank.",
                        "",
                        "&e9 Ranks Available"
                )
        ));

        fillEmpty(inventory);
        player.openInventory(inventory);
    }

    // ==========================================
    // RANK ICONS
    // ==========================================

    private Material getRankIcon(String rankId) {
        return switch (rankId.toLowerCase()) {
            case "donatur" -> Material.NETHERITE_INGOT;
            case "ascendance" -> Material.DIAMOND;
            case "sovereign" -> Material.EMERALD;
            case "immortal" -> Material.GOLD_INGOT;
            case "duke" -> Material.IRON_INGOT;
            case "viscount" -> Material.COPPER_INGOT;
            case "baron" -> Material.LAPIS_LAZULI;
            case "knight" -> Material.REDSTONE;
            case "peasant" -> Material.COAL;
            default -> Material.RAW_IRON;
        };
    }

    private String getRankColor(String rankId) {
        return switch (rankId.toLowerCase()) {
            case "donatur" -> "&6";
            case "ascendance" -> "&b";
            case "sovereign" -> "&a";
            case "immortal" -> "&e";
            case "duke" -> "&f";
            case "viscount" -> "&6";
            case "baron" -> "&9";
            case "knight" -> "&c";
            case "peasant" -> "&7";
            default -> "&f";
        };
    }

    // ==========================================
    // COMING SOON MENU
    // ==========================================

    private void openComingSoonMenu(
            Player player,
            String category,
            Material icon,
            String description
    ) {
        CoinHolder holder = new CoinHolder(MenuType.COMING_SOON);

        Inventory inventory = Bukkit.createInventory(
                holder,
                27,
                color("&8ᴍᴀʀᴋᴇᴛᴘʟᴀᴄᴇ &8| &cᴄᴏᴍɪɴɢ ѕᴏᴏɴ")
        );

        holder.setInventory(inventory);

        inventory.setItem(13, item(
                icon,
                "&d&l" + smallCaps(category),
                List.of(
                        "&7" + description,
                        "",
                        "&c&lCOMING SOON",
                        "",
                        "&7This category is currently",
                        "&7under development.",
                        "",
                        "&eStay tuned for future updates!"
                )
        ));

        inventory.setItem(22, item(
                Material.ARROW,
                "&c&lʙᴀᴄᴋ",
                List.of("&7Return to Coin Market.")
        ));

        fillEmpty(inventory);
        player.openInventory(inventory);
    }

    // ==========================================
    // CONFIRM PURCHASE
    // ==========================================

    private void openConfirmMenu(
            Player player,
            String rankId
    ) {
        String path = "rank-shop.ranks." + rankId;

        if (!plugin.getConfig().contains(path)) {
            player.sendMessage(color("&cRank tidak ditemukan."));
            return;
        }

        String rankName = plugin.getConfig().getString(
                path + ".name",
                rankId
        );

        long price = plugin.getConfig().getLong(
                path + ".price",
                0L
        );

        if (price <= 0) {
            player.sendMessage(color("&cHarga rank tidak valid."));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            long balance;

            try {
                balance = coinManager.getBalance(
                        player.getUniqueId()
                );
            } catch (Exception exception) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage(color(
                                "&cGagal mengambil saldo Coin."
                        ))
                );
                return;
            }

            long finalBalance = balance;

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;

                CoinHolder holder = new CoinHolder(MenuType.CONFIRM);
                holder.setRankId(rankId);

                Inventory inventory = Bukkit.createInventory(
                        holder,
                        27,
                        color("&8ᴄᴏɴꜰɪʀᴍ ᴘᴜʀᴄʜᴀѕᴇ")
                );

                holder.setInventory(inventory);

                inventory.setItem(11, item(
                        Material.LIME_DYE,
                        "&a&lᴄᴏɴꜰɪʀᴍ",
                        List.of(
                                "&7Purchase this rank?",
                                "",
                                "&aClick to confirm."
                        )
                ));

                inventory.setItem(13, item(
                        getRankIcon(rankId),
                        getRankColor(rankId) + "&l" + smallCaps(rankName),
                        List.of(
                                "&8━━━━━━━━━━━━━━━━",
                                "&7Rank: &f" + rankName,
                                "&7Price: &e" + format(price) + " Coin",
                                "&7Balance: &f" + format(finalBalance) + " Coin",
                                "",
                                "&7Remaining: &a"
                                        + format(Math.max(0L, finalBalance - price))
                                        + " Coin",
                                "&8━━━━━━━━━━━━━━━━"
                        )
                ));

                inventory.setItem(15, item(
                        Material.RED_DYE,
                        "&c&lᴄᴀɴᴄᴇʟ",
                        List.of(
                                "&7Cancel this purchase."
                        )
                ));

                fillEmpty(inventory);
                player.openInventory(inventory);
            });
        });
    }

    // ==========================================
    // PURCHASE PROCESS
    // ==========================================

    private void purchaseRank(
            Player player,
            String rankId
    ) {
        String path = "rank-shop.ranks." + rankId;

        if (!plugin.getConfig().contains(path)) {
            player.sendMessage(color("&cRank tidak ditemukan."));
            return;
        }

        String rankName = plugin.getConfig().getString(
                path + ".name",
                rankId
        );

        long price = plugin.getConfig().getLong(
                path + ".price",
                0L
        );

        String commandTemplate = plugin.getConfig().getString(
                path + ".command",
                ""
        );

        if (price <= 0 || commandTemplate.isBlank()) {
            player.sendMessage(color(
                    "&cKonfigurasi rank tidak valid."
            ));
            return;
        }

        player.closeInventory();
        player.sendMessage(color(
                "&eMemproses pembelian rank..."
        ));

        UUID uuid = player.getUniqueId();
        String username = player.getName();

        String reference = "RANK_" + UUID.randomUUID()
                .toString()
                .replace("-", "");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                coinManager.createAccount(uuid, username);

                long balance = coinManager.getBalance(uuid);

                if (balance < price) {
                    long shortage = price - balance;

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage(color(
                                "&c&lCOIN TIDAK CUKUP!"
                        ));

                        player.sendMessage(color(
                                "&7Harga: &e" + format(price) + " Coin"
                        ));

                        player.sendMessage(color(
                                "&7Saldo: &e" + format(balance) + " Coin"
                        ));

                        player.sendMessage(color(
                                "&7Kekurangan: &c"
                                        + format(shortage) + " Coin"
                        ));
                    });

                    return;
                }

                boolean deducted = coinManager.removeCoins(
                        uuid,
                        username,
                        price,
                        reference,
                        "Pembelian rank " + rankName
                );

                if (!deducted) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                            player.sendMessage(color(
                                    "&cPembelian gagal. Silakan coba lagi."
                            ))
                    );
                    return;
                }

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;

                    String consoleCommand = commandTemplate
                            .replace("%player%", username)
                            .replace("%uuid%", uuid.toString())
                            .replace("%rank%", rankId)
                            .replace("%rank_name%", rankName);

                    boolean commandSuccess = Bukkit.dispatchCommand(
                            Bukkit.getConsoleSender(),
                            consoleCommand
                    );

                    if (!commandSuccess) {
                        Bukkit.getScheduler().runTaskAsynchronously(
                                plugin,
                                () -> refundPurchase(
                                        uuid,
                                        username,
                                        price,
                                        reference,
                                        player
                                )
                        );

                        player.sendMessage(color(
                                "&cGagal memberikan rank. Coin sedang dikembalikan."
                        ));

                        plugin.getLogger().severe(
                                "Rank command failed for " + username
                        );

                        return;
                    }

                    player.sendMessage(color(
                            "&a&lPEMBELIAN BERHASIL!"
                    ));

                    player.sendMessage(color(
                            "&7Rank: " + getRankColor(rankId) + rankName
                    ));

                    player.sendMessage(color(
                            "&7Coin digunakan: &e" + format(price)
                    ));

                    player.sendMessage(color(
                            "&bTerima kasih telah berbelanja!"
                    ));

                    plugin.getLogger().info(
                            username + " purchased rank " + rankName
                                    + " for " + price + " Coin."
                    );
                });

            } catch (Exception exception) {
                plugin.getLogger().severe(
                        "Rank purchase error for " + username
                                + ": " + exception.getMessage()
                );

                Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage(color(
                                "&cTerjadi kesalahan saat membeli rank."
                        ))
                );
            }
        });
    }

    private void refundPurchase(
            UUID uuid,
            String username,
            long price,
            String reference,
            Player player
    ) {
        try {
            coinManager.addCoins(
                    uuid,
                    username,
                    price,
                    "REFUND_" + reference,
                    "Refund pembelian rank gagal"
            );

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    player.sendMessage(color(
                            "&aCoin kamu berhasil dikembalikan."
                    ));
                }
            });

        } catch (Exception exception) {
            plugin.getLogger().severe(
                    "Failed to refund rank purchase for "
                            + username + ": " + exception.getMessage()
            );
        }
    }

    // ==========================================
    // INVENTORY CLICK HANDLER
    // ==========================================

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!(event.getInventory().getHolder() instanceof CoinHolder holder)) {
            return;
        }

        event.setCancelled(true);

        if (event.getClickedInventory() == null
                || event.getClickedInventory()
                != event.getView().getTopInventory()) {
            return;
        }

        int slot = event.getRawSlot();

        if (holder.type == MenuType.MAIN) {
            switch (slot) {
                case 20 -> openRankMenu(player);

                case 22 -> openComingSoonMenu(
                        player,
                        "ᴄʟᴀɪᴍ ʙʟᴏᴄᴋ",
                        Material.EMERALD_BLOCK,
                        "Protect your land with Claim Blocks."
                );

                case 24 -> openComingSoonMenu(
                        player,
                        "ᴘᴇᴛѕ",
                        Material.CREEPER_SPAWN_EGG,
                        "Find your own loyal companions."
                );

                case 31 -> openComingSoonMenu(
                        player,
                        "ᴄᴏѕᴍᴇᴛɪᴄ",
                        Material.AMETHYST_SHARD,
                        "Customize your character."
                );

                default -> {
                }
            }

            return;
        }

        if (holder.type == MenuType.RANKS) {
            if (slot == 49) {
                openMainMenu(player);
                return;
            }

            String rankId = holder.getRankAtSlot(slot);

            if (rankId != null) {
                openConfirmMenu(player, rankId);
            }

            return;
        }

        if (holder.type == MenuType.CONFIRM) {
            if (slot == 11 && holder.rankId != null) {
                purchaseRank(player, holder.rankId);
            } else if (slot == 15) {
                openRankMenu(player);
            }

            return;
        }

        if (holder.type == MenuType.COMING_SOON) {
            if (slot == 22) {
                openMainMenu(player);
            }
        }
    }

    // ==========================================
    // ITEM BUILDER
    // ==========================================

    private ItemStack item(
            Material material,
            String name,
            List<String> lore
    ) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(color(name));

            List<String> coloredLore = new ArrayList<>();

            for (String line : lore) {
                coloredLore.add(color(line));
            }

            meta.setLore(coloredLore);
            stack.setItemMeta(meta);
        }

        return stack;
    }

    // ==========================================
    // SMALL CAPS FONT
    // ==========================================

    private String smallCaps(String text) {
        return style(text);
    }

    private String style(String text) {
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);

            if (current == '&' && i + 1 < text.length()) {
                result.append(current);
                result.append(text.charAt(++i));
                continue;
            }

            result.append(toSmallCaps(current));
        }

        return result.toString();
    }

    private char toSmallCaps(char c) {
        return switch (c) {
            case 'a' -> 'ᴀ';
            case 'b' -> 'ʙ';
            case 'c' -> 'ᴄ';
            case 'd' -> 'ᴅ';
            case 'e' -> 'ᴇ';
            case 'f' -> 'ꜰ';
            case 'g' -> 'ɢ';
            case 'h' -> 'ʜ';
            case 'i' -> 'ɪ';
            case 'j' -> 'ᴊ';
            case 'k' -> 'ᴋ';
            case 'l' -> 'ʟ';
            case 'm' -> 'ᴍ';
            case 'n' -> 'ɴ';
            case 'o' -> 'ᴏ';
            case 'p' -> 'ᴘ';
            case 'q' -> 'ǫ';
            case 'r' -> 'ʀ';
            case 's' -> 'ѕ';
            case 't' -> 'ᴛ';
            case 'u' -> 'ᴜ';
            case 'v' -> 'ᴠ';
            case 'w' -> 'ᴡ';
            case 'x' -> 'x';
            case 'y' -> 'ʏ';
            case 'z' -> 'ᴢ';
            default -> c;
        };
    }

    // ==========================================
    // INVENTORY FILLER
    // ==========================================

    private void fillEmpty(Inventory inventory) {
        ItemStack filler = item(
                Material.BLACK_STAINED_GLASS_PANE,
                "&r",
                List.of()
        );

        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }

    private String format(long number) {
        return String.format("%,d", number);
    }

    private String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    // ==========================================
    // MENU HOLDER
    // ==========================================

    private enum MenuType {
        MAIN,
        RANKS,
        CONFIRM,
        COMING_SOON
    }

    private static final class CoinHolder implements InventoryHolder {

        private final MenuType type;

        private final Map<Integer, String> rankSlots =
                new HashMap<>();

        private Inventory inventory;
        private String rankId;

        private CoinHolder(MenuType type) {
            this.type = type;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        private void setRankId(String rankId) {
            this.rankId = rankId;
        }

        private void setRankSlot(int slot, String rankId) {
            rankSlots.put(slot, rankId);
        }

        private String getRankAtSlot(int slot) {
            return rankSlots.get(slot);
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
