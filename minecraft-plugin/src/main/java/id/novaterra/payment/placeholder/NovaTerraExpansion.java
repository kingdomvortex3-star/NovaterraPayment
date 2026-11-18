
package id.novaterra.payment.placeholder;

import id.novaterra.payment.NovaTerraPayment;
import id.novaterra.payment.economy.CoinManager;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;

import org.bukkit.OfflinePlayer;

import java.text.NumberFormat;
import java.util.Locale;

public final class NovaTerraExpansion extends PlaceholderExpansion {

    private final NovaTerraPayment plugin;
    private final CoinManager coinManager;

    public NovaTerraExpansion(
            NovaTerraPayment plugin,
            CoinManager coinManager
    ) {
        this.plugin = plugin;
        this.coinManager = coinManager;
    }

    @Override
    public String getIdentifier() {
        return "novaterra";
    }

    @Override
    public String getAuthor() {
        return "Vortex Teams";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onRequest(
            OfflinePlayer player,
            String params
    ) {

        if (player == null || player.getUniqueId() == null) {
            return "0";
        }

        if (params.equalsIgnoreCase("coins")) {

            long balance = coinManager.getCachedBalance(
                    player.getUniqueId()
            );

            return NumberFormat.getNumberInstance(
                    Locale.US
            ).format(balance);
        }

        return null;
    }
}
