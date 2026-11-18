
package id.novaterra.payment.security;

import id.novaterra.payment.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class DuplicateProtection {

    private final DatabaseManager database;

    public DuplicateProtection(DatabaseManager database) {
        this.database = database;
    }

    /**
     * Registers an event exactly once.
     *
     * Returns true if this event is new.
     * Returns false if this event was already registered.
     */
    public boolean registerEvent(
            String eventId,
            String transactionId
    ) throws SQLException {

        validate(eventId, "Event ID");
        validate(transactionId, "Transaction ID");

        String sql = """
                INSERT INTO processed_events (
                    event_id,
                    transaction_id,
                    processed_at
                )
                VALUES (?, ?, ?)
                """;

        try (Connection connection = database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, eventId);
            statement.setString(2, transactionId);
            statement.setLong(
                    3, System.currentTimeMillis()
            );

            return statement.executeUpdate() == 1;

        } catch (SQLException exception) {

            if (isUniqueConstraintViolation(exception)) {
                return false;
            }

            throw exception;
        }
    }

    public boolean isEventProcessed(String eventId)
            throws SQLException {

        validate(eventId, "Event ID");

        String sql = """
                SELECT event_id
                FROM processed_events
                WHERE event_id = ?
                """;

        try (Connection connection = database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, eventId);

            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private boolean isUniqueConstraintViolation(
            SQLException exception
    ) {

        SQLException current = exception;

        while (current != null) {

            String state = current.getSQLState();

            if ("23505".equals(state)
                    || "23000".equals(state)
                    || current.getErrorCode() == 1062
                    || current.getErrorCode() == 19) {
                return true;
            }

            current = current.getNextException();
        }

        return false;
    }

    private void validate(String value, String field) {

        if (value == null
                || value.isBlank()
                || value.length() > 128) {

            throw new IllegalArgumentException(
                    field + " is invalid."
            );
        }
    }
}
