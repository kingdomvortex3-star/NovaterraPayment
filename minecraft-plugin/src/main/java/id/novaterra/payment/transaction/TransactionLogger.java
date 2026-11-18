
package id.novaterra.payment.transaction;

import id.novaterra.payment.NovaTerraPayment;

import java.util.logging.Level;

public final class TransactionLogger {

    private final NovaTerraPayment plugin;

    public TransactionLogger(NovaTerraPayment plugin) {
        this.plugin = plugin;
    }

    public void info(String message) {
        plugin.getLogger().info(
                "[Transaction] " + message
        );
    }

    public void warning(String message) {
        plugin.getLogger().warning(
                "[Transaction] " + message
        );
    }

    public void error(String message, Throwable throwable) {
        plugin.getLogger().log(
                Level.SEVERE,
                "[Transaction] " + message,
                throwable
        );
    }

    public void transactionCreated(Transaction transaction) {
        info(
                "Created: "
                + transaction.getTransactionId()
                + " | Player: "
                + transaction.getPlayerName()
                + " | Coins: "
                + transaction.getCoins()
        );
    }

    public void paymentReceived(Transaction transaction) {
        info(
                "Payment received: "
                + transaction.getTransactionId()
                + " | Player: "
                + transaction.getPlayerName()
                + " | Amount: "
                + transaction.getAmount()
        );
    }

    public void transactionCompleted(Transaction transaction) {
        info(
                "Completed: "
                + transaction.getTransactionId()
                + " | Player: "
                + transaction.getPlayerName()
                + " | Coins delivered: "
                + transaction.getCoins()
        );
    }

    public void transactionFailed(
            String transactionId,
            String reason
    ) {
        warning(
                "Failed: "
                + transactionId
                + " | Reason: "
                + reason
        );
    }
}
