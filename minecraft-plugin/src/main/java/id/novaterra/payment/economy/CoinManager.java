
package id.novaterra.payment.economy;

import id.novaterra.payment.NovaTerraPayment;
import id.novaterra.payment.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

public final class CoinManager {

    private final NovaTerraPayment plugin;
    private final DatabaseManager database;

    public CoinManager(
            NovaTerraPayment plugin,
            DatabaseManager database
    ) {
        this.plugin = plugin;
        this.database = database;
    }

    public void createAccount(UUID uuid, String username)
            throws SQLException {

        try (Connection connection = database.getConnection()) {

            if (getBalance(connection, uuid) != null) {
                updateUsername(connection, uuid, username);
                return;
            }

            long now = System.currentTimeMillis();

            String sql = """
                    INSERT INTO players
                    (uuid, username, balance, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?)
                    """;

            try (PreparedStatement statement =
                         connection.prepareStatement(sql)) {

                statement.setString(1, uuid.toString());
                statement.setString(2, username);
                statement.setLong(3, plugin.getConfig()
                        .getLong("coin.starting-balance", 0));
                statement.setLong(4, now);
                statement.setLong(5, now);

                statement.executeUpdate();
            }
        }
    }

    public long getBalance(UUID uuid) throws SQLException {

        try (Connection connection = database.getConnection()) {

            Long balance = getBalance(connection, uuid);

            return balance == null ? 0L : balance;
        }
    }

    private Long getBalance(Connection connection, UUID uuid)
            throws SQLException {

        String sql = "SELECT balance FROM players WHERE uuid = ?";

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, uuid.toString());

            try (ResultSet result = statement.executeQuery()) {

                if (result.next()) {
                    return result.getLong("balance");
                }
            }
        }

        return null;
    }

    public boolean addCoins(
            UUID uuid,
            String username,
            long amount,
            String referenceId,
            String description
    ) throws SQLException {

        if (amount <= 0) {
            throw new IllegalArgumentException(
                    "Coin amount must be greater than zero."
            );
        }

        return changeBalance(
                uuid, username, amount,
                "CREDIT", referenceId, description
        );
    }

    public boolean removeCoins(
            UUID uuid,
            String username,
            long amount,
            String referenceId,
            String description
    ) throws SQLException {

        if (amount <= 0) {
            throw new IllegalArgumentException(
                    "Coin amount must be greater than zero."
            );
        }

        return changeBalance(
                uuid, username, -amount,
                "DEBIT", referenceId, description
        );
    }

    private boolean changeBalance(
            UUID uuid,
            String username,
            long amount,
            String action,
            String referenceId,
            String description
    ) throws SQLException {

        if (amount == 0) {
            return false;
        }

        try (Connection connection = database.getConnection()) {

            connection.setAutoCommit(false);

            try {
                if (referenceId != null) {
                    if (historyExists(connection, referenceId)) {
                        connection.rollback();
                        return false;
                    }
                }

                Long currentBalance = getBalance(connection, uuid);

                if (currentBalance == null) {
                    throw new SQLException(
                            "Player account does not exist: " + uuid
                    );
                }

                long newBalance;

                try {
                    newBalance = Math.addExact(
                            currentBalance, amount
                    );
                } catch (ArithmeticException exception) {
                    throw new SQLException(
                            "Coin balance overflow!", exception
                    );
                }

                if (newBalance < 0) {
                    connection.rollback();
                    return false;
                }

                String updateSql = """
                        UPDATE players
                        SET balance = ?, updated_at = ?
                        WHERE uuid = ?
                        """;

                try (PreparedStatement statement =
                             connection.prepareStatement(updateSql)) {

                    statement.setLong(1, newBalance);
                    statement.setLong(
                            2, System.currentTimeMillis()
                    );
                    statement.setString(3, uuid.toString());

                    if (statement.executeUpdate() != 1) {
                        throw new SQLException(
                                "Failed to update player balance."
                        );
                    }
                }

                String historySql = """
                        INSERT INTO coin_history
                        (history_id, player_uuid, amount, action,
                         reference_id, description, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """;

                try (PreparedStatement statement =
                             connection.prepareStatement(historySql)) {

                    statement.setString(1, UUID.randomUUID().toString());
                    statement.setString(2, uuid.toString());
                    statement.setLong(3, amount);
                    statement.setString(4, action);
                    statement.setString(5, referenceId);
                    statement.setString(6, description);
                    statement.setLong(
                            7, System.currentTimeMillis()
                    );

                    statement.executeUpdate();
                }

                connection.commit();
                return true;

            } catch (SQLException | RuntimeException exception) {

                connection.rollback();
                throw exception;

            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private boolean historyExists(
            Connection connection,
            String referenceId
    ) throws SQLException {

        String sql = """
                SELECT history_id
                FROM coin_history
                WHERE reference_id = ?
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, referenceId);

            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private void updateUsername(
            Connection connection,
            UUID uuid,
            String username
    ) throws SQLException {

        String sql = """
                UPDATE players
                SET username = ?, updated_at = ?
                WHERE uuid = ?
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, username);
            statement.setLong(2, System.currentTimeMillis());
            statement.setString(3, uuid.toString());

            statement.executeUpdate();
        }
    }
}
