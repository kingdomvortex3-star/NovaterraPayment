
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

        String sql = """
                INSERT INTO players (uuid, username, balance)
                VALUES (?, ?, 0)
                ON CONFLICT(uuid) DO UPDATE SET
                    username = excluded.username
                """;

        try (Connection connection = database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, uuid.toString());
            statement.setString(2, username);
            statement.executeUpdate();
        }
    }

    public Optional<String> getUsername(UUID uuid)
            throws SQLException {

        String sql = "SELECT username FROM players WHERE uuid = ?";

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

    public boolean exists(UUID uuid) throws SQLException {
        String sql = "SELECT 1 FROM players WHERE uuid = ?";

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
