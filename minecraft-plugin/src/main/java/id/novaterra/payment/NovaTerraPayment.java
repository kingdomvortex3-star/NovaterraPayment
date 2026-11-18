
package id.novaterra.payment;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class NovaTerraPayment extends JavaPlugin {

    private static NovaTerraPayment instance;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        getLogger().info("======================================");
        getLogger().info("       NOVATERRA PAYMENT");
        getLogger().info("       Developed by Vortex Teams");
        getLogger().info("======================================");
        getLogger().info("Version: " + getDescription().getVersion());
        getLogger().info("Plugin successfully enabled!");
        getLogger().info("======================================");
    }

    @Override
    public void onDisable() {
        getLogger().info("NovaTerra Payment has been disabled.");
        instance = null;
    }

    public static NovaTerraPayment getInstance() {
        return instance;
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {

        if (!sender.hasPermission("novaterra.payment.admin")) {
            sender.sendMessage(color(
                    getConfig().getString("messages.prefix", "")
                    + getConfig().getString(
                            "messages.no-permission",
                            "&cKamu tidak memiliki izin!"
                    )
            ));
            return true;
        }

        if (args.length == 1
                && args[0].equalsIgnoreCase("reload")) {

            reloadConfig();

            sender.sendMessage(color(
                    getConfig().getString("messages.prefix", "")
                    + getConfig().getString(
                            "messages.reload",
                            "&aKonfigurasi berhasil dimuat ulang."
                    )
            ));

            return true;
        }

        sender.sendMessage(color("&b&lNOVATERRA PAYMENT"));
        sender.sendMessage(color("&7Developed by &fVortex Teams"));
        sender.sendMessage(color("&7Version: &f"
                + getDescription().getVersion()));
        sender.sendMessage(color("&7Command: &f/novapayment reload"));

        return true;
    }

    private String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
