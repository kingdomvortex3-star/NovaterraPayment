
package id.novaterra.payment.api;

import id.novaterra.payment.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public final class PlayerAPI {

    private final DatabaseManager database;

    public PlayerAPI(DatabaseManager database) {
        this.database = database;
    }

    /**
     * Find player UUID using Minecraft nickname.
     */
    public Optional<UUID> findUUID(String username)
            throws SQLException {

        if (!isValidUsername(username)) {
            return Optional.empty();
        }

        String sql = """
                SELECT uuid
                FROM players
                WHERE LOWER(username) = LOWER(?)
                LIMIT 1
                """;

        try (Connection connection = database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, username);

            try (ResultSet result = statement.executeQuery()) {

                if (result.next()) {
                    return Optional.of(
                            UUID.fromString(result.getString("uuid"))
                    );
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Find Minecraft nickname using UUID.
     */
    public Optional<String> findUsername(UUID uuid)
            throws SQLException {

        if (uuid == null) {
            return Optional.empty();
        }

        String sql = """
                SELECT username
                FROM players
                WHERE uuid = ?
                LIMIT 1
                """;

        try (Connection connection = database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, uuid.toString());

            try (ResultSet result = statement.executeQuery()) {

                if (result.next()) {
                    return Optional.of(
                            result.getString("username")
                    );
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Check whether a player exists.
     */
    public boolean isRegistered(String username)
            throws SQLException {

        return findUUID(username).isPresent();
    }

    /**
     * Check whether a UUID exists.
     */
    public boolean isRegistered(UUID uuid)
            throws SQLException {

        if (uuid == null) {
            return false;
        }

        String sql = """
                SELECT uuid
                FROM players
                WHERE uuid = ?
                LIMIT 1
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

    private boolean isValidUsername(String username) {

        if (username == null || username.isBlank()) {
            return false;
        }

        return username.length() <= 16
                && username.matches("[a-zA-Z0-9_]+");
    }
}
