
package id.novaterra.payment.api;

import id.novaterra.payment.economy.CoinManager;

import java.sql.SQLException;
import java.util.UUID;

public final class CoinAPI {

    private final CoinManager coinManager;

    public CoinAPI(CoinManager coinManager) {
        this.coinManager = coinManager;
    }

    /**
     * Get player's current Coin balance.
     */
    public long getBalance(UUID playerUUID) throws SQLException {
        return coinManager.getBalance(playerUUID);
    }

    /**
     * Get cached balance without database query.
     */
    public long getCachedBalance(UUID playerUUID) {
        return coinManager.getCachedBalance(playerUUID);
    }

    /**
     * Add Coin to a player's account.
     */
    public boolean addCoins(
            UUID playerUUID,
            String playerName,
            long amount,
            String referenceId,
            String description
    ) throws SQLException {

        return coinManager.addCoins(
                playerUUID,
                playerName,
                amount,
                referenceId,
                description
        );
    }

    /**
     * Remove Coin from a player's account.
     */
    public boolean removeCoins(
            UUID playerUUID,
            String playerName,
            long amount,
            String referenceId,
            String description
    ) throws SQLException {

        return coinManager.removeCoins(
                playerUUID,
                playerName,
                amount,
                referenceId,
                description
        );
    }

    /**
     * Check whether player has enough Coin.
     */
    public boolean hasCoins(
            UUID playerUUID,
            long requiredAmount
    ) throws SQLException {

        if (requiredAmount < 0) {
            return false;
        }

        return getBalance(playerUUID) >= requiredAmount;
    }
}
