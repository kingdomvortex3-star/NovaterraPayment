
package id.novaterra.payment.player;

import id.novaterra.payment.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public final class PlayerManager {

    private final DatabaseManager database;

    public PlayerManager(DatabaseManager database) {
        this.database = database;
    }

    /**
     * Register a player or update their username.
     * Compatible with SQLite and MySQL.
     */
    public void registerPlayer(UUID uuid, String username)
            throws SQLException {

        if (uuid == null) {
            throw new IllegalArgumentException(
                    "Player UUID cannot be null."
            );
        }

        if (username == null
                || username.isBlank()
                || username.length() > 16
                || !username.matches("[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException(
                    "Invalid Minecraft username."
            );
        }

        long now = System.currentTimeMillis();

        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);

            try {
                int updated;

                String updateSql = """
                        UPDATE players
                        SET username = ?, updated_at = ?
                        WHERE uuid = ?
                        """;

                try (PreparedStatement statement =
                             connection.prepareStatement(updateSql)) {

                    statement.setString(1, username);
                    statement.setLong(2, now);
                    statement.setString(3, uuid.toString());

                    updated = statement.executeUpdate();
                }

                if (updated == 0) {
                    String insertSql = """
                            INSERT INTO players (
                                uuid,
                                username,
                                balance,
                                created_at,
                                updated_at
                            )
                            VALUES (?, ?, 0, ?, ?)
                            """;

                    try (PreparedStatement statement =
                                 connection.prepareStatement(insertSql)) {

                        statement.setString(1, uuid.toString());
                        statement.setString(2, username);
                        statement.setLong(3, now);
                        statement.setLong(4, now);

                        statement.executeUpdate();
                    }
                }

                connection.commit();

            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;

            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    /**
     * Get player's current username.
     */
    public Optional<String> getUsername(UUID uuid)
            throws SQLException {

        if (uuid == null) {
            return Optional.empty();
        }

        String sql = """
                SELECT username
                FROM players
                WHERE uuid = ?
                """;

        try (Connection connection = database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, uuid.toString());

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return Optional.ofNullable(
                            result.getString("username")
                    );
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Check whether a player is registered.
     */
    public boolean exists(UUID uuid) throws SQLException {

        if (uuid == null) {
            return false;
        }

        String sql = """
                SELECT 1
                FROM players
                WHERE uuid = ?
                """;

        try (Connection connection = database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, uuid.toString());

            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }
}
