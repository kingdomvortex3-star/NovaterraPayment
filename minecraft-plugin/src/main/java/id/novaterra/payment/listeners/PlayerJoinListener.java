
package id.novaterra.payment.listeners;

import id.novaterra.payment.NovaTerraPayment;
import id.novaterra.payment.economy.CoinManager;
import id.novaterra.payment.api.PlayerIdentitySync;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.UUID;
import java.util.logging.Level;

public final class PlayerJoinListener implements Listener {

    private final NovaTerraPayment plugin;
    private final CoinManager coinManager;
    private final PlayerIdentitySync identitySync;

    public PlayerJoinListener(
            NovaTerraPayment plugin,
            CoinManager coinManager
    ) {
        this.plugin = plugin;
        this.coinManager = coinManager;
        this.identitySync = new PlayerIdentitySync(plugin);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {

        Player player = event.getPlayer();

        UUID playerUUID = player.getUniqueId();
        String playerName = player.getName();
        String platform = detectPlatform(playerUUID);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                coinManager.createAccount(playerUUID, playerName);
                coinManager.loadBalance(playerUUID);

                identitySync.sync(
                        playerUUID,
                        playerName,
                        platform
                );

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (Bukkit.getPlayer(playerUUID) == null) {
                        coinManager.unloadBalance(playerUUID);
                        return;
                    }

                    plugin.getLogger().info(
                            "Coin account loaded for " + playerName
                    );
                });

            } catch (Exception exception) {
                plugin.getLogger().log(
                        Level.SEVERE,
                        "Failed to load Coin account for " + playerName,
                        exception
                );
            }
        });
    }

    private String detectPlatform(UUID uuid) {

        try {
            Class<?> apiClass = Class.forName(
                    "org.geysermc.floodgate.api.FloodgateApi"
            );

            Object api = apiClass
                    .getMethod("getInstance")
                    .invoke(null);

            Object result = apiClass
                    .getMethod("isFloodgatePlayer", UUID.class)
                    .invoke(api, uuid);

            return Boolean.TRUE.equals(result)
                    ? "BEDROCK"
                    : "JAVA";

        } catch (Throwable ignored) {
            return "UNKNOWN";
        }
    }
}
