
package id.novaterra.payment.listeners;

import id.novaterra.payment.economy.CoinManager;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public final class PlayerQuitListener implements Listener {

    private final CoinManager coinManager;

    public PlayerQuitListener(CoinManager coinManager) {
        this.coinManager = coinManager;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerUUID = event.getPlayer().getUniqueId();
        coinManager.unloadBalance(playerUUID);
    }
}
