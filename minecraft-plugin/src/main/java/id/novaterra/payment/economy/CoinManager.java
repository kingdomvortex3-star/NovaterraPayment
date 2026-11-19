
package id.novaterra.payment.economy;

import id.novaterra.payment.NovaTerraPayment;
import id.novaterra.payment.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class CoinManager {

    private static final int MAX_REFERENCE_LENGTH = 64;

    public enum CreditResult {
        APPLIED,
        DUPLICATE
    }

    private final NovaTerraPayment plugin;
    private final DatabaseManager database;

    private final ConcurrentMap<UUID, Long> balanceCache =
            new ConcurrentHashMap<>();

    public CoinManager(
            NovaTerraPayment plugin,
            DatabaseManager database
    ) {
        this.plugin = plugin;
        this.database = database;
    }

    /**
     * Creates a player account if it does not exist.
     * Uses the same lock as balance transactions.
     */
    public synchronized void createAccount(
            UUID uuid,
            String username
    ) throws SQLException {

        validatePlayer(uuid, username);

        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);

            try {
                Long balance = getBalance(connection, uuid);

                if (balance != null) {
                    updateUsername(connection, uuid, username);
                    connection.commit();
                    balanceCache.put(uuid, balance);
                    return;
                }

                long startingBalance = plugin.getConfig()
                        .getLong("coin.starting-balance", 0);

                if (!CoinValidator.isValidBalance(startingBalance)) {
                    throw new SQLException(
                            "Invalid starting balance!"
                    );
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
                    statement.setLong(3, startingBalance);
                    statement.setLong(4, now);
                    statement.setLong(5, now);

                    statement.executeUpdate();
                }

                connection.commit();
                balanceCache.put(uuid, startingBalance);

            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    /**
     * Gets the player's current balance.
     */
    public synchronized long getBalance(UUID uuid)
            throws SQLException {

        if (uuid == null) {
            throw new IllegalArgumentException(
                    "Player UUID cannot be null."
            );
        }

        Long cached = balanceCache.get(uuid);

        if (cached != null) {
            return cached;
        }

        try (Connection connection = database.getConnection()) {
            Long balance = getBalance(connection, uuid);

            if (balance == null) {
                return 0L;
            }

            balanceCache.put(uuid, balance);
            return balance;
        }
    }

    /**
     * Gets balance from cache only.
     */
    public synchronized long getCachedBalance(UUID uuid) {
        return balanceCache.getOrDefault(uuid, 0L);
    }

    /**
     * Loads balance from database into cache.
     */
    public synchronized void loadBalance(UUID uuid)
            throws SQLException {

        if (uuid != null) {
            balanceCache.put(
                    uuid,
                    getDatabaseBalance(uuid)
            );
        }
    }

    /**
     * Removes player balance from cache.
     */
    public synchronized void unloadBalance(UUID uuid) {
        if (uuid != null) {
            balanceCache.remove(uuid);
        }
    }

    private long getDatabaseBalance(UUID uuid)
            throws SQLException {

        try (Connection connection = database.getConnection()) {
            Long balance = getBalance(connection, uuid);
            return balance == null ? 0L : balance;
        }
    }

    /**
     * Reads balance using an existing connection.
     */
    private Long getBalance(
            Connection connection,
            UUID uuid
    ) throws SQLException {

        String sql = "SELECT balance FROM players WHERE uuid = ?";

        if ("MYSQL".equalsIgnoreCase(
                database.getDatabaseType()
        )) {
            sql += " FOR UPDATE";
        }

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

    /**
     * Backward-compatible method.
     * Returns false only when the reference was already processed.
     * Invalid amounts and database failures throw exceptions.
     */
    public boolean addCoins(
            UUID uuid,
            String username,
            long amount,
            String referenceId,
            String description
    ) throws SQLException {

        return addCoinsResult(
                uuid,
                username,
                amount,
                referenceId,
                description
        ) == CreditResult.APPLIED;
    }

    /**
     * Adds Coin and returns an explicit result.
     */
    public CreditResult addCoinsResult(
            UUID uuid,
            String username,
            long amount,
            String referenceId,
            String description
    ) throws SQLException {

        if (!CoinValidator.isValidAmount(amount)) {
            throw new IllegalArgumentException(
                    "Invalid coin amount."
            );
        }

        return changeBalance(
                uuid,
                username,
                amount,
                "CREDIT",
                referenceId,
                description
        );
    }

    /**
     * Removes Coin from a player.
     */
    public boolean removeCoins(
            UUID uuid,
            String username,
            long amount,
            String referenceId,
            String description
    ) throws SQLException {

        if (!CoinValidator.isValidAmount(amount)) {
            throw new IllegalArgumentException(
                    "Invalid coin amount."
            );
        }

        return changeBalance(
                uuid,
                username,
                -amount,
                "DEBIT",
                referenceId,
                description
        ) == CreditResult.APPLIED;
    }

    /**
     * Updates balance, history, and processed event atomically.
     */
    private synchronized CreditResult changeBalance(
            UUID uuid,
            String username,
            long amount,
            String action,
            String referenceId,
            String description
    ) throws SQLException {

        validatePlayer(uuid, username);
        validateReference(referenceId);

        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);

            try {
                /*
                 * Register reference in the same transaction.
                 * A duplicate reference must never credit Coin twice.
                 */
                if (referenceId != null
                        && !registerReference(
                                connection,
                                referenceId
                        )) {

                    connection.rollback();
                    return CreditResult.DUPLICATE;
                }

                Long currentBalance = getBalance(
                        connection,
                        uuid
                );

                if (currentBalance == null) {
                    throw new SQLException(
                            "Player account does not exist: " + uuid
                    );
                }

                final long newBalance;

                try {
                    newBalance = CoinValidator.calculateNewBalance(
                            currentBalance,
                            amount
                    );
                } catch (IllegalArgumentException exception) {
                    throw new SQLException(
                            "Coin balance limit exceeded for player "
                                    + uuid,
                            exception
                    );
                }

                String updateSql = """
                        UPDATE players
                        SET
                            balance = ?,
                            username = ?,
                            updated_at = ?
                        WHERE uuid = ?
                        """;

                try (PreparedStatement statement =
                             connection.prepareStatement(updateSql)) {

                    statement.setLong(1, newBalance);
                    statement.setString(2, username);
                    statement.setLong(
                            3,
                            System.currentTimeMillis()
                    );
                    statement.setString(4, uuid.toString());

                    if (statement.executeUpdate() != 1) {
                        throw new SQLException(
                                "Failed to update player balance."
                        );
                    }
                }

                String historySql = """
                        INSERT INTO coin_history (
                            history_id,
                            player_uuid,
                            amount,
                            action,
                            reference_id,
                            description,
                            created_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """;

                try (PreparedStatement statement =
                             connection.prepareStatement(historySql)) {

                    statement.setString(
                            1,
                            UUID.randomUUID().toString()
                    );
                    statement.setString(2, uuid.toString());
                    statement.setLong(3, amount);
                    statement.setString(4, action);
                    statement.setString(5, referenceId);
                    statement.setString(6, description);
                    statement.setLong(
                            7,
                            System.currentTimeMillis()
                    );

                    statement.executeUpdate();
                }

                connection.commit();

                balanceCache.put(uuid, newBalance);

                return CreditResult.APPLIED;

            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    /**
     * Registers a unique payment reference.
     */
    private boolean registerReference(
            Connection connection,
            String referenceId
    ) throws SQLException {

        String sql = """
                INSERT INTO processed_events (
                    event_id,
                    transaction_id,
                    processed_at
                )
                VALUES (?, ?, ?)
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, referenceId);
            statement.setString(2, referenceId);
            statement.setLong(
                    3,
                    System.currentTimeMillis()
            );

            statement.executeUpdate();
            return true;

        } catch (SQLException exception) {
            if (isDuplicateKey(exception)) {
                return false;
            }

            throw exception;
        }
    }

    /**
     * Detects actual unique constraint violations.
     */
    private boolean isDuplicateKey(SQLException exception) {
        SQLException current = exception;

        while (current != null) {
            String message = current.getMessage();
            String state = current.getSQLState();
            int errorCode = current.getErrorCode();

            if ("23505".equals(state)
                    || errorCode == 1062
                    || errorCode == 19) {
                return true;
            }

            if (message != null) {
                String normalized = message.toLowerCase();

                if (normalized.contains("unique constraint failed")
                        || normalized.contains("primary key constraint failed")
                        || normalized.contains("duplicate entry")) {
                    return true;
                }
            }

            current = current.getNextException();
        }

        return false;
    }

    private void validateReference(String referenceId) {
        if (referenceId != null
                && (referenceId.isBlank()
                || referenceId.length() > MAX_REFERENCE_LENGTH)) {

            throw new IllegalArgumentException(
                    "Invalid transaction reference."
            );
        }
    }

    /**
     * Updates an existing player's username.
     */
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
            statement.setLong(
                    2,
                    System.currentTimeMillis()
            );
            statement.setString(3, uuid.toString());

            statement.executeUpdate();
        }
    }

    private void validatePlayer(
            UUID uuid,
            String username
    ) {
        if (uuid == null) {
            throw new IllegalArgumentException(
                    "Player UUID cannot be null."
            );
        }

        if (username == null
                || username.isBlank()
                || username.length() > 16) {

            throw new IllegalArgumentException(
                    "Invalid player username."
            );
        }
    }

    /**
     * Clears all cached player balances.
     */
    public synchronized void clearCache() {
        balanceCache.clear();
    }
}
