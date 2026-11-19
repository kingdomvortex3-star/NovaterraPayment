
package id.novaterra.payment.player.commands;

import id.novaterra.payment.NovaTerraPayment;
import id.novaterra.payment.economy.CoinManager;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.UUID;

public final class CoinAdminCommand implements CommandExecutor {

    private final NovaTerraPayment plugin;
    private final CoinManager coinManager;

    public CoinAdminCommand(
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
        if (!sender.hasPermission("novaterra.payment.admin")) {
            sender.sendMessage(color("&cKamu tidak memiliki izin."));
            return true;
        }

        if (args.length != 3) {
            sender.sendMessage(color("&b&lNOVATERRA PAYMENT"));
            sender.sendMessage(color("&e/coinadmin give <player> <jumlah>"));
            sender.sendMessage(color("&e/coinadmin take <player> <jumlah>"));
            sender.sendMessage(color("&e/coinadmin set <player> <jumlah>"));
            return true;
        }

        String action = args[0].toLowerCase();
        String targetName = args[1];

        if (!action.equals("give")
                && !action.equals("take")
                && !action.equals("set")) {
            sender.sendMessage(color("&cAksi tidak dikenal."));
            return true;
        }

        long amount;

        try {
            amount = Long.parseLong(args[2]);
        } catch (NumberFormatException exception) {
            sender.sendMessage(color("&cJumlah Coin tidak valid."));
            return true;
        }

        if (amount <= 0) {
            sender.sendMessage(color("&cJumlah harus lebih dari 0."));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(color("&cPlayer tidak ditemukan."));
            return true;
        }

        UUID uuid = target.getUniqueId();

        String username = target.getName() != null
                ? target.getName()
                : targetName;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                coinManager.createAccount(uuid, username);

                String reference = "ADMIN_" + UUID.randomUUID();
                String description = "Admin command: " + action;

                boolean success = true;

                switch (action) {
                    case "give" -> success = coinManager.addCoins(
                            uuid,
                            username,
                            amount,
                            reference,
                            description
                    );

                    case "take" -> success = coinManager.removeCoins(
                            uuid,
                            username,
                            amount,
                            reference,
                            description
                    );

                    case "set" -> {
                        long current = coinManager.getBalance(uuid);
                        long difference = amount - current;

                        if (difference > 0) {
                            success = coinManager.addCoins(
                                    uuid,
                                    username,
                                    difference,
                                    reference,
                                    description
                            );
                        } else if (difference < 0) {
                            success = coinManager.removeCoins(
                                    uuid,
                                    username,
                                    -difference,
                                    reference,
                                    description
                            );
                        }
                    }
                }

                boolean result = success;

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!result) {
                        sender.sendMessage(color(
                                "&cTransaksi gagal atau reference duplikat."
                        ));
                        return;
                    }

                    sender.sendMessage(color(
                            "&aBerhasil " + action
                            + " &e" + String.format("%,d", amount)
                            + " Coin &auntuk &e" + username
                    ));
                });

            } catch (Exception exception) {
                plugin.getLogger().severe(
                        "Coin admin error: " + exception.getMessage()
                );

                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(color(
                                "&cGagal memproses Coin. Periksa console."
                        ))
                );
            }
        });

        return true;
    }

    private String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
