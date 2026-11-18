
package id.novaterra.payment.transaction;

import java.util.Objects;
import java.util.UUID;

public final class Transaction {

    private final String transactionId;
    private final UUID playerUUID;
    private final String playerName;
    private final long coins;
    private final long amount;
    private final String currency;
    private final String gateway;

    private TransactionStatus status;

    private final long createdAt;
    private Long paidAt;

    public Transaction(
            String transactionId,
            UUID playerUUID,
            String playerName,
            long coins,
            long amount,
            String currency,
            String gateway,
            TransactionStatus status,
            long createdAt,
            Long paidAt
    ) {
        this.transactionId = requireText(
                transactionId, "Transaction ID"
        );

        this.playerUUID = Objects.requireNonNull(
                playerUUID, "Player UUID cannot be null"
        );

        this.playerName = requireText(
                playerName, "Player name"
        );

        if (coins <= 0 || amount <= 0) {
            throw new IllegalArgumentException(
                    "Coins and payment amount must be positive."
            );
        }

        this.coins = coins;
        this.amount = amount;

        this.currency = requireText(
                currency, "Currency"
        );

        this.gateway = requireText(
                gateway, "Gateway"
        );

        this.status = Objects.requireNonNull(
                status, "Status cannot be null"
        );

        if (createdAt <= 0) {
            throw new IllegalArgumentException(
                    "Invalid creation timestamp."
            );
        }

        this.createdAt = createdAt;
        this.paidAt = paidAt;
    }

    private static String requireText(
            String value,
            String field
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " cannot be empty."
            );
        }

        return value;
    }

    public synchronized void setStatus(
            TransactionStatus status
    ) {
        this.status = Objects.requireNonNull(status);
    }

    public synchronized void markPaid() {
        if (status != TransactionStatus.PENDING
                && status != TransactionStatus.WAITING_PAYMENT) {
            throw new IllegalStateException(
                    "Transaction cannot be marked as paid."
            );
        }

        status = TransactionStatus.PAID;
        paidAt = System.currentTimeMillis();
    }

    public String getTransactionId() {
        return transactionId;
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public String getPlayerName() {
        return playerName;
    }

    public long getCoins() {
        return coins;
    }

    public long getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getGateway() {
        return gateway;
    }

    public synchronized TransactionStatus getStatus() {
        return status;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public synchronized Long getPaidAt() {
        return paidAt;
    }
}
