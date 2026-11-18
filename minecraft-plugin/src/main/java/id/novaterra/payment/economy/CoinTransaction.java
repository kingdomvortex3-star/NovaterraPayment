
package id.novaterra.payment.economy;

import java.util.Objects;
import java.util.UUID;

public final class CoinTransaction {

    public enum Type {
        CREDIT,
        DEBIT
    }

    private final String transactionId;
    private final UUID playerUUID;
    private final String playerName;
    private final long amount;
    private final Type type;
    private final String referenceId;
    private final String description;
    private final long timestamp;

    public CoinTransaction(
            String transactionId,
            UUID playerUUID,
            String playerName,
            long amount,
            Type type,
            String referenceId,
            String description,
            long timestamp
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

        if (amount <= 0) {
            throw new IllegalArgumentException(
                    "Transaction amount must be positive."
            );
        }

        if (timestamp <= 0) {
            throw new IllegalArgumentException(
                    "Invalid transaction timestamp."
            );
        }

        this.amount = amount;
        this.type = Objects.requireNonNull(
                type, "Transaction type cannot be null"
        );

        this.referenceId = referenceId;
        this.description = description == null
                ? ""
                : description;

        this.timestamp = timestamp;
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

    public String getTransactionId() {
        return transactionId;
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public String getPlayerName() {
        return playerName;
    }

    public long getAmount() {
        return amount;
    }

    public Type getType() {
        return type;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public String getDescription() {
        return description;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isCredit() {
        return type == Type.CREDIT;
    }

    public boolean isDebit() {
        return type == Type.DEBIT;
    }

    @Override
    public String toString() {
        return "CoinTransaction{" +
                "transactionId='" + transactionId + '\'' +
                ", playerUUID=" + playerUUID +
                ", playerName='" + playerName + '\'' +
                ", amount=" + amount +
                ", type=" + type +
                ", referenceId='" + referenceId + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
