
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

    public void registerPlayer(UUID uuid, String username)
            throws SQLException {

        if (uuid == null) {
            throw new IllegalArgumentException("Player UUID cannot be null.");
        }

        if (username == null
                || username.isBlank()
                || username.length() > 16
                || !username.matches("[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException("Invalid Minecraft username.");
        }

        long now = System.currentTimeMillis();

        String insertSql = """
                INSERT INTO players (
                    uuid, username, balance, created_at, updated_at
                )
                VALUES (?, ?, 0, ?, ?)
                """;

        try (Connection connection = database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(insertSql)) {

            statement.setString(1, uuid.toString());
            statement.setString(2, username);
            statement.setLong(3, now);
            statement.setLong(4, now);

            statement.executeUpdate();
            return;

        } catch (SQLException exception) {
            if (!isDuplicateKey(exception)) {
                throw exception;
            }
        }

        String updateSql = """
                UPDATE players
                SET username = ?, updated_at = ?
                WHERE uuid = ?
                """;

        try (Connection connection = database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(updateSql)) {

            statement.setString(1, username);
            statement.setLong(2, now);
            statement.setString(3, uuid.toString());

            if (statement.executeUpdate() == 0) {
                throw new SQLException(
                        "Could not register or update player: " + uuid
                );
            }
        }
    }

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
                    return Optional.ofNullable(result.getString("username"));
                }
            }
        }

        return Optional.empty();
    }

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

    private boolean isDuplicateKey(SQLException exception) {
        SQLException current = exception;

        while (current != null) {
            String state = current.getSQLState();
            int code = current.getErrorCode();
            String message = current.getMessage();

            if ("23505".equals(state)
                    || code == 1062
                    || code == 1555
                    || code == 2067
                    || (message != null
                    && (message.contains("UNIQUE constraint failed")
                    || message.contains("PRIMARY KEY constraint failed")))) {
                return true;
            }

            current = current.getNextException();
        }

        return false;
    }
}
