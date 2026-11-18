
package id.novaterra.payment.api;

import id.novaterra.payment.transaction.Transaction;
import id.novaterra.payment.transaction.TransactionManager;
import id.novaterra.payment.transaction.TransactionStatus;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public final class PaymentAPI {

    private final TransactionManager transactionManager;
    private final PlayerAPI playerAPI;

    public PaymentAPI(
            TransactionManager transactionManager,
            PlayerAPI playerAPI
    ) {
        this.transactionManager = transactionManager;
        this.playerAPI = playerAPI;
    }

    /**
     * Create a pending payment record.
     */
    public Transaction createPayment(
            String transactionId,
            String playerName,
            long coins,
            long amount,
            String gateway
    ) throws SQLException {

        if (transactionId == null
                || transactionId.isBlank()
                || transactionId.length() > 64) {
            throw new IllegalArgumentException(
                    "Invalid transaction ID."
            );
        }

        if (coins <= 0 || amount <= 0) {
            throw new IllegalArgumentException(
                    "Invalid payment amount."
            );
        }

        if (gateway == null || gateway.isBlank()) {
            throw new IllegalArgumentException(
                    "Payment gateway cannot be empty."
            );
        }

        Optional<UUID> playerUUID =
                playerAPI.findUUID(playerName);

        if (playerUUID.isEmpty()) {
            throw new IllegalArgumentException(
                    "Minecraft player is not registered."
            );
        }

        Transaction transaction = new Transaction(
                transactionId,
                playerUUID.get(),
                playerName,
                coins,
                amount,
                "IDR",
                gateway,
                TransactionStatus.PENDING,
                System.currentTimeMillis(),
                null
        );

        boolean created =
                transactionManager.createTransaction(transaction);

        if (!created) {
            throw new SQLException(
                    "Failed to create payment transaction."
            );
        }

        return transaction;
    }

    /**
     * Get transaction by ID.
     */
    public Transaction getTransaction(String transactionId)
            throws SQLException {

        return transactionManager.getTransaction(transactionId);
    }

    /**
     * Check transaction status.
     */
    public Optional<TransactionStatus> getStatus(
            String transactionId
    ) throws SQLException {

        Transaction transaction =
                transactionManager.getTransaction(transactionId);

        if (transaction == null) {
            return Optional.empty();
        }

        return Optional.of(transaction.getStatus());
    }

    /**
     * Get all transactions belonging to a player.
     */
    public java.util.List<Transaction> getPlayerTransactions(
            UUID playerUUID
    ) throws SQLException {

        return transactionManager.getPlayerTransactions(playerUUID);
    }
}
