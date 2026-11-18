
package id.novaterra.payment.commands;

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

        long amount;
        try {
            amount = Long.parseLong(args[2]);
        } catch (NumberFormatException exception) {
            sender.sendMessage(color("&cJumlah Coin harus berupa angka."));
            return true;
        }

        if (amount <= 0) {
            sender.sendMessage(color("&cJumlah Coin harus lebih dari 0."));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(color("&cPlayer tidak ditemukan."));
            return true;
        }

        UUID uuid = target.getUniqueId();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                switch (action) {
                    case "give" ->
                            coinManager.addCoins(
                                    uuid, amount,
                                    "ADMIN_GIVE", "Admin command"
                            );

                    case "take" ->
                            coinManager.removeCoins(
                                    uuid, amount,
                                    "ADMIN_TAKE", "Admin command"
                            );

                    case "set" -> {
                        long current = coinManager.getBalance(uuid);
                        long difference = amount - current;

                        if (difference > 0) {
                            coinManager.addCoins(
                                    uuid, difference,
                                    "ADMIN_SET", "Admin set balance"
                            );
                        } else if (difference < 0) {
                            coinManager.removeCoins(
                                    uuid, Math.abs(difference),
                                    "ADMIN_SET", "Admin set balance"
                            );
                        }
                    }

                    default -> {
                        sender.sendMessage(color("&cAksi tidak dikenal."));
                        return;
                    }
                }

                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(color(
                            "&aBerhasil menjalankan " + action
                            + " Coin untuk &e" + targetName
                            + " &asebanyak &e"
                            + String.format("%,d", amount) + " Coin."
                    ));
                });

            } catch (Exception exception) {
                plugin.getLogger().severe(
                        "Coin admin command failed: "
                        + exception.getMessage()
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
