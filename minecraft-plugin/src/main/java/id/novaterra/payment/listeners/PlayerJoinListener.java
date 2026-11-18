
package id.novaterra.payment.listeners;

import id.novaterra.payment.NovaTerraPayment;
import id.novaterra.payment.economy.CoinManager;

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

    public PlayerJoinListener(
            NovaTerraPayment plugin,
            CoinManager coinManager
    ) {
        this.plugin = plugin;
        this.coinManager = coinManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        UUID playerUUID = player.getUniqueId();
        String playerName = player.getName();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                coinManager.createAccount(playerUUID, playerName);
                coinManager.loadBalance(playerUUID);

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
}
