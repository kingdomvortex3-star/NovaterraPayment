
package id.novaterra.payment;

import id.novaterra.payment.api.CoinAPI;
import id.novaterra.payment.api.PaymentAPI;
import id.novaterra.payment.api.PlayerAPI;
import id.novaterra.payment.database.DatabaseManager;
import id.novaterra.payment.delivery.DeliveryWorker;
import id.novaterra.payment.economy.CoinManager;
import id.novaterra.payment.listeners.PlayerJoinListener;
import id.novaterra.payment.listeners.PlayerQuitListener;
import id.novaterra.payment.placeholder.NovaTerraExpansion;
import id.novaterra.payment.player.PlayerManager;
import id.novaterra.payment.player.commands.CoinAdminCommand;
import id.novaterra.payment.player.commands.CoinCommand;
import id.novaterra.payment.transaction.TransactionLogger;
import id.novaterra.payment.transaction.TransactionManager;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;

public final class NovaTerraPayment extends JavaPlugin {

    private static NovaTerraPayment instance;

    private DatabaseManager databaseManager;
    private CoinManager coinManager;
    private PlayerManager playerManager;
    private TransactionManager transactionManager;

    private CoinAPI coinAPI;
    private PlayerAPI playerAPI;
    private PaymentAPI paymentAPI;

    private TransactionLogger transactionLogger;
    private NovaTerraExpansion placeholderExpansion;
    private DeliveryWorker deliveryWorker;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        getLogger().info("======================================");
        getLogger().info("       NOVATERRA PAYMENT");
        getLogger().info("       Developed by Vortex Teams");
        getLogger().info("======================================");

        try {
            initializeDatabase();
            initializeManagers();
            registerCommands();
            registerListeners();
            registerPlaceholderAPI();
            initializeDeliveryWorker();

            getLogger().info("Database: Enabled");
            getLogger().info("Coin Manager: Enabled");
            getLogger().info("Player Manager: Enabled");
            getLogger().info("Transaction Manager: Enabled");
            getLogger().info("Commands: Registered");
            getLogger().info("Listeners: Registered");

            getLogger().info("======================================");
            getLogger().info("NOVATERRA PAYMENT ENABLED");
            getLogger().info("======================================");

        } catch (Exception exception) {
            getLogger().severe(
                    "Failed to initialize NOVATERRA PAYMENT!"
            );

            getLogger().log(
                    java.util.logging.Level.SEVERE,
                    "Initialization error",
                    exception
            );

            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void initializeDatabase() throws SQLException {
        databaseManager = new DatabaseManager(this);
        databaseManager.initialize();
    }

    private void initializeManagers() {
        coinManager = new CoinManager(this, databaseManager);
        playerManager = new PlayerManager(databaseManager);
        transactionManager = new TransactionManager(databaseManager);

        coinAPI = new CoinAPI(coinManager);
        playerAPI = new PlayerAPI(databaseManager);

        paymentAPI = new PaymentAPI(
                transactionManager,
                playerAPI
        );

        transactionLogger = new TransactionLogger(this);
    }

    private void registerCommands() {
        PluginCommand coinAdminCommand = getCommand("coinadmin");

        if (coinAdminCommand == null) {
            throw new IllegalStateException(
                    "Command coinadmin is missing from plugin.yml!"
            );
        }

        coinAdminCommand.setExecutor(
                new CoinAdminCommand(this, coinManager)
        );

        PluginCommand coinCommand = getCommand("coin");

        if (coinCommand == null) {
            throw new IllegalStateException(
                    "Command coin is missing from plugin.yml!"
            );
        }

        CoinCommand executor = new CoinCommand(this, coinManager);

        coinCommand.setExecutor(executor);

        getServer().getPluginManager().registerEvents(
                executor,
                this
        );

        getLogger().info("Coin Market GUI: Registered");
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(
                new PlayerJoinListener(this, coinManager),
                this
        );

        getServer().getPluginManager().registerEvents(
                new PlayerQuitListener(coinManager),
                this
        );
    }

    private void registerPlaceholderAPI() {
        if (!getServer().getPluginManager()
                .isPluginEnabled("PlaceholderAPI")) {

            getLogger().info(
                    "PlaceholderAPI not found. Expansion disabled."
            );

            return;
        }

        placeholderExpansion = new NovaTerraExpansion(
                this,
                coinManager
        );

        if (placeholderExpansion.register()) {
            getLogger().info(
                    "PlaceholderAPI expansion registered: %novaterra_coins%"
            );
        } else {
            getLogger().warning(
                    "Failed to register PlaceholderAPI expansion."
            );
        }
    }

    private void initializeDeliveryWorker() {
        deliveryWorker = new DeliveryWorker(this, coinManager);
        deliveryWorker.start();
    }

    private void restartDeliveryWorker() {
        if (deliveryWorker != null) {
            deliveryWorker.stop();
        }

        deliveryWorker = new DeliveryWorker(this, coinManager);
        deliveryWorker.start();
    }

    @Override
    public void onDisable() {
        if (deliveryWorker != null) {
            deliveryWorker.stop();
            deliveryWorker = null;
        }

        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
            placeholderExpansion = null;
        }

        if (coinManager != null) {
            coinManager.clearCache();
        }

        if (databaseManager != null) {
            databaseManager.close();
        }

        getLogger().info(
                "NOVATERRA PAYMENT has been disabled."
        );

        instance = null;
    }

    public static NovaTerraPayment getInstance() {
        return instance;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public CoinManager getCoinManager() {
        return coinManager;
    }

    public PlayerManager getPlayerManager() {
        return playerManager;
    }

    public TransactionManager getTransactionManager() {
        return transactionManager;
    }

    public CoinAPI getCoinAPI() {
        return coinAPI;
    }

    public PlayerAPI getPlayerAPI() {
        return playerAPI;
    }

    public PaymentAPI getPaymentAPI() {
        return paymentAPI;
    }

    public TransactionLogger getTransactionLogger() {
        return transactionLogger;
    }

    public DeliveryWorker getDeliveryWorker() {
        return deliveryWorker;
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
            restartDeliveryWorker();

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
        sender.sendMessage(color("&7Developer: &fVortex Teams"));
        sender.sendMessage(color("&7Version: &f"
                + getDescription().getVersion()));
        sender.sendMessage(color("&7Command: &f/novapayment reload"));

        return true;
    }

    private String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
