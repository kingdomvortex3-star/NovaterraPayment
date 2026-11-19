
package id.novaterra.payment.transaction;

import id.novaterra.payment.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TransactionManager {

    private final DatabaseManager database;

    public TransactionManager(DatabaseManager database) {
        this.database = database;
    }

    public boolean createTransaction(Transaction transaction)
            throws SQLException {

        if (transaction == null) {
            throw new IllegalArgumentException("Transaction cannot be null.");
        }

        String sql = """
                INSERT INTO transactions (
                    transaction_id,
                    player_uuid,
                    nickname,
                    coins,
                    amount,
                    currency,
                    status,
                    gateway,
                    created_at,
                    paid_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, transaction.getTransactionId());
            statement.setString(2, transaction.getPlayerUUID().toString());
            statement.setString(3, transaction.getPlayerName());
            statement.setLong(4, transaction.getCoins());
            statement.setLong(5, transaction.getAmount());
            statement.setString(6, transaction.getCurrency());
            statement.setString(7, transaction.getStatus().name());
            statement.setString(8, transaction.getGateway());
            statement.setLong(9, transaction.getCreatedAt());

            if (transaction.getPaidAt() == null) {
                statement.setNull(10, java.sql.Types.BIGINT);
            } else {
                statement.setLong(10, transaction.getPaidAt());
            }

            return statement.executeUpdate() == 1;
        }
    }

    public Transaction getTransaction(String transactionId)
            throws SQLException {

        if (transactionId == null || transactionId.isBlank()) {
            return null;
        }

        String sql = """
                SELECT *
                FROM transactions
                WHERE transaction_id = ?
                """;

        try (Connection connection = database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, transactionId);

            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? mapTransaction(result) : null;
            }
        }
    }

    public List<Transaction> getPlayerTransactions(UUID playerUUID)
            throws SQLException {

        List<Transaction> transactions = new ArrayList<>();

        if (playerUUID == null) {
            return transactions;
        }

        String sql = """
                SELECT *
                FROM transactions
                WHERE player_uuid = ?
                ORDER BY created_at DESC
                """;

        try (Connection connection = database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, playerUUID.toString());

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    transactions.add(mapTransaction(result));
                }
            }
        }

        return transactions;
    }

    public boolean updateStatus(
            String transactionId,
            TransactionStatus newStatus
    ) throws SQLException {

        if (transactionId == null || transactionId.isBlank()
                || newStatus == null) {
            return false;
        }

        String allowedPreviousStatuses = switch (newStatus) {
            case WAITING_PAYMENT -> "'PENDING'";
            case PAID -> "'PENDING', 'WAITING_PAYMENT'";
            case PROCESSING -> "'PAID'";
            case COMPLETED -> "'PAID', 'PROCESSING'";
            case FAILED -> "'PENDING', 'WAITING_PAYMENT', 'PROCESSING'";
            case EXPIRED -> "'PENDING', 'WAITING_PAYMENT'";
            case CANCELLED -> "'PENDING', 'WAITING_PAYMENT'";
            case REFUNDED -> "'PAID', 'PROCESSING', 'COMPLETED'";
            case PENDING -> null;
        };

        if (allowedPreviousStatuses == null) {
            return false;
        }

        String sql;

        if (newStatus == TransactionStatus.PAID) {
            sql = """
                    UPDATE transactions
                    SET status = 'PAID',
                        paid_at = COALESCE(paid_at, ?)
                    WHERE transaction_id = ?
                      AND status IN (%s)
                    """.formatted(allowedPreviousStatuses);
        } else {
            sql = """
                    UPDATE transactions
                    SET status = ?
                    WHERE transaction_id = ?
                      AND status IN (%s)
                    """.formatted(allowedPreviousStatuses);
        }

        try (Connection connection = database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            if (newStatus == TransactionStatus.PAID) {
                statement.setLong(1, System.currentTimeMillis());
                statement.setString(2, transactionId);
            } else {
                statement.setString(1, newStatus.name());
                statement.setString(2, transactionId);
            }

            return statement.executeUpdate() == 1;
        }
    }

    public boolean markPaid(String transactionId)
            throws SQLException {
        return updateStatus(transactionId, TransactionStatus.PAID);
    }

    public int getTotalTransactions() throws SQLException {
        String sql = "SELECT COUNT(*) FROM transactions";

        try (Connection connection = database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {

            return result.next() ? result.getInt(1) : 0;
        }
    }

    private Transaction mapTransaction(ResultSet result)
            throws SQLException {

        long paidTimestamp = result.getLong("paid_at");

        Long paidAt = result.wasNull() ? null : paidTimestamp;

        return new Transaction(
                result.getString("transaction_id"),
                UUID.fromString(result.getString("player_uuid")),
                result.getString("nickname"),
                result.getLong("coins"),
                result.getLong("amount"),
                result.getString("currency"),
                result.getString("gateway"),
                TransactionStatus.valueOf(result.getString("status")),
                result.getLong("created_at"),
                paidAt
        );
    }
}
