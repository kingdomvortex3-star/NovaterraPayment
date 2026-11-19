
package id.novaterra.payment.player.commands;

import id.novaterra.payment.NovaTerraPayment;
import id.novaterra.payment.economy.CoinManager;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

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

        if (args.length == 1
                && args[0].equalsIgnoreCase("reload")) {
            player.sendMessage(color("&cGunakan /novapayment reload untuk reload plugin."));
            return true;
        }

        openMainMenu(player);
        return true;
    }

    private void openMainMenu(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            long balance;

            try {
                coinManager.createAccount(
                        player.getUniqueId(),
                        player.getName()
                );
                balance = coinManager.getBalance(player.getUniqueId());
            } catch (Exception exception) {
                plugin.getLogger().severe(
                        "Failed to load Coin balance for "
                                + player.getName() + ": "
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
                if (!player.isOnline()) {
                    return;
                }

                CoinHolder holder = new CoinHolder(MenuType.MAIN, null);
                Inventory inventory = Bukkit.createInventory(
                        holder,
                        27,
                        color("&8NOVATERRA &bCOIN MARKET")
                );
                holder.setInventory(inventory);

                inventory.setItem(4, item(
                        Material.GOLD_INGOT,
                        "&e&lSALDO COIN",
                        List.of(
                                "&7Saldo Coin kamu:",
                                "&e" + format(finalBalance) + " Coin",
                                "",
                                "&8NOVATERRA PAYMENT"
                        )
                ));

                inventory.setItem(11, item(
                        Material.NETHER_STAR,
                        "&b&lRANK",
                        List.of(
                                "&7Beli rank menggunakan Coin.",
                                "",
                                "&eKlik untuk melihat daftar rank."
                        )
                ));

                inventory.setItem(15, item(
                        Material.SUNFLOWER,
                        "&a&lCOIN MARKET",
                        List.of(
                                "&7Top up Coin melalui Discord.",
                                "",
                                "&eLihat channel Coin Market Discord."
                        )
                ));

                fillEmpty(inventory);
                player.openInventory(inventory);
            });
        });
    }

    private void openRankMenu(Player player) {
        var section = plugin.getConfig()
                .getConfigurationSection("rank-shop.ranks");

        if (section == null || section.getKeys(false).isEmpty()) {
            player.sendMessage(color(
                    "&cDaftar rank belum dikonfigurasi."
            ));
            return;
        }

        CoinHolder holder = new CoinHolder(MenuType.RANKS, null);
        Inventory inventory = Bukkit.createInventory(
                holder,
                54,
                color("&8NOVATERRA &bRANK SHOP")
        );
        holder.setInventory(inventory);

        int[] slots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25
        };

        int index = 0;

        for (String rankId : section.getKeys(false)) {
            if (index >= slots.length) {
                break;
            }

            String path = "rank-shop.ranks." + rankId;
            String name = plugin.getConfig().getString(
                    path + ".name",
                    rankId
            );
            long price = plugin.getConfig().getLong(
                    path + ".price",
                    0L
            );

            if (price <= 0) {
                continue;
            }

            inventory.setItem(
                    slots[index],
                    item(
                            Material.DIAMOND,
                            "&b&l" + name,
                            List.of(
                                    "&7Harga: &e" + format(price) + " Coin",
                                    "",
                                    "&aKlik untuk membeli rank."
                            )
                    )
            );

            holder.setRankSlot(slots[index], rankId);
            index++;
        }

        inventory.setItem(49, item(
                Material.ARROW,
                "&cKEMBALI",
                List.of("&7Kembali ke menu utama.")
        ));

        fillEmpty(inventory);
        player.openInventory(inventory);
    }

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
                balance = coinManager.getBalance(player.getUniqueId());
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
                if (!player.isOnline()) {
                    return;
                }

                CoinHolder holder = new CoinHolder(
                        MenuType.CONFIRM,
                        rankId
                );

                Inventory inventory = Bukkit.createInventory(
                        holder,
                        27,
                        color("&8KONFIRMASI PEMBELIAN")
                );
                holder.setInventory(inventory);

                inventory.setItem(11, item(
                        Material.LIME_DYE,
                        "&a&lKONFIRMASI",
                        List.of(
                                "&7Rank: &f" + rankName,
                                "&7Harga: &e" + format(price) + " Coin",
                                "",
                                "&aKlik untuk membeli."
                        )
                ));

                inventory.setItem(13, item(
                        Material.GOLD_INGOT,
                        "&e&lINFORMASI PEMBELIAN",
                        List.of(
                                "&7Rank: &f" + rankName,
                                "&7Harga: &e" + format(price) + " Coin",
                                "&7Saldo: &f" + format(finalBalance) + " Coin",
                                "&7Sisa setelah pembelian: &f"
                                        + format(Math.max(0L, finalBalance - price))
                                        + " Coin"
                        )
                ));

                inventory.setItem(15, item(
                        Material.RED_DYE,
                        "&c&lBATAL",
                        List.of("&7Batalkan pembelian.")
                ));

                fillEmpty(inventory);
                player.openInventory(inventory);
            });
        });
    }

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

        if (price <= 0) {
            player.sendMessage(color("&cHarga rank tidak valid."));
            return;
        }

        String commandTemplate = plugin.getConfig().getString(
                path + ".command",
                ""
        );

        if (commandTemplate.isBlank()) {
            player.sendMessage(color(
                    "&cCommand pemberian rank belum dikonfigurasi."
            ));
            return;
        }

        player.closeInventory();
        player.sendMessage(color("&eMemproses pembelian rank..."));

        UUID uuid = player.getUniqueId();
        String username = player.getName();

        String reference = "RANK_" + UUID.randomUUID()
                .toString().replace("-", "");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                coinManager.createAccount(uuid, username);

                long balance = coinManager.getBalance(uuid);

                if (balance < price) {
                    long shortage = price - balance;

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage(color(
                                "&cCoin kamu tidak cukup!"
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
                    if (!player.isOnline()) {
                        return;
                    }

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
                                        + ": " + consoleCommand
                        );
                        return;
                    }

                    player.sendMessage(color("&a&lPEMBELIAN BERHASIL!"));
                    player.sendMessage(color(
                            "&7Rank: &b" + rankName
                    ));
                    player.sendMessage(color(
                            "&7Coin digunakan: &e" + format(price)
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
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        int slot = event.getRawSlot();

        if (holder.type == MenuType.MAIN) {
            if (slot == 11) {
                openRankMenu(player);
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
        }
    }

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

    private void fillEmpty(Inventory inventory) {
        ItemStack filler = item(
                Material.GRAY_STAINED_GLASS_PANE,
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

    private enum MenuType {
        MAIN,
        RANKS,
        CONFIRM
    }

    private static final class CoinHolder implements InventoryHolder {

        private final MenuType type;
        private final String rankId;
        private final java.util.Map<Integer, String> rankSlots =
                new java.util.HashMap<>();

        private Inventory inventory;

        private CoinHolder(MenuType type, String rankId) {
            this.type = type;
            this.rankId = rankId;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
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
