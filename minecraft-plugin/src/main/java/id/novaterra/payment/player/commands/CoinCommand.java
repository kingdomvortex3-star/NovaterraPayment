
package id.novaterra.payment.commands;

import id.novaterra.payment.NovaTerraPayment;
import id.novaterra.payment.economy.CoinManager;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class CoinCommand implements CommandExecutor {

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

        UUID uuid = player.getUniqueId();
        long balance = coinManager.getCachedBalance(uuid);

        String prefix = plugin.getConfig().getString(
                "messages.prefix",
                "&8[&bNovaTerra&8] "
        );

        player.sendMessage(color(
                prefix + "&7Saldo Coin kamu: &e"
                + String.format("%,d", balance) + " Coin"
        ));

        return true;
    }

    private String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
