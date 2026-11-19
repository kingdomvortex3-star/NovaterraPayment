
package id.novaterra.payment.database;

import id.novaterra.payment.NovaTerraPayment;

import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

public final class DatabaseManager {

    private final NovaTerraPayment plugin;

    private String databaseType;
    private String sqlitePath;
    private String mysqlUrl;
    private String mysqlUsername;
    private String mysqlPassword;

    public DatabaseManager(NovaTerraPayment plugin) {
        this.plugin = plugin;
    }

    public void initialize() throws SQLException {
        databaseType = plugin.getConfig()
                .getString("database.type", "SQLITE")
                .trim()
                .toUpperCase(Locale.ROOT);

        switch (databaseType) {
            case "SQLITE" -> initializeSQLite();
            case "MYSQL" -> initializeMySQL();
            default -> throw new SQLException(
                    "Unsupported database type: " + databaseType
            );
        }

        createTables();
        createIndexes();

        plugin.getLogger().info("Database connected: " + databaseType);
        plugin.getLogger().info("Database tables: Ready");
        plugin.getLogger().info("Database indexes: Ready");
    }

    private void initializeSQLite() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException exception) {
            throw new SQLException("SQLite JDBC driver not found!", exception);
        }

        File folder = plugin.getDataFolder();

        if (!folder.exists() && !folder.mkdirs()) {
            throw new SQLException("Cannot create plugin data folder!");
        }

        String filename = plugin.getConfig()
                .getString("database.sqlite.file", "database.db");

        File databaseFile = new File(folder, filename);
        File parent = databaseFile.getParentFile();

        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new SQLException("Cannot create SQLite database directory!");
        }

        sqlitePath = "jdbc:sqlite:" + databaseFile.getAbsolutePath();

        try (Connection connection = DriverManager.getConnection(sqlitePath);
             Statement statement = connection.createStatement()) {

            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
    }

    private void initializeMySQL() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException exception) {
            throw new SQLException("MySQL JDBC driver not found!", exception);
        }

        String host = plugin.getConfig()
                .getString("database.mysql.host", "localhost");

        int port = plugin.getConfig()
                .getInt("database.mysql.port", 3306);

        String database = plugin.getConfig()
                .getString("database.mysql.database", "novaterra_payment");

        mysqlUsername = plugin.getConfig()
                .getString("database.mysql.username", "root");

        mysqlPassword = plugin.getConfig()
                .getString("database.mysql.password", "");

        boolean ssl = plugin.getConfig()
                .getBoolean("database.mysql.ssl", false);

        if (host == null || host.isBlank()
                || database == null || database.isBlank()
                || mysqlUsername == null || mysqlUsername.isBlank()
                || port < 1 || port > 65535) {
            throw new SQLException("Invalid MySQL configuration!");
        }

        mysqlUrl = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=" + ssl
                + "&serverTimezone=UTC"
                + "&characterEncoding=UTF-8";
    }

    public Connection getConnection() throws SQLException {
        if ("SQLITE".equals(databaseType)) {
            Connection connection = DriverManager.getConnection(sqlitePath);

            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
                statement.execute("PRAGMA busy_timeout = 5000");
            }

            return connection;
        }

        if ("MYSQL".equals(databaseType)) {
            return DriverManager.getConnection(
                    mysqlUrl,
                    mysqlUsername,
                    mysqlPassword
            );
        }

        throw new SQLException("Database is not initialized!");
    }

    private void createTables() throws SQLException {
        String[] tables = {
                """
                CREATE TABLE IF NOT EXISTS players (
                    uuid VARCHAR(36) PRIMARY KEY,
                    username VARCHAR(16) NOT NULL,
                    balance BIGINT NOT NULL DEFAULT 0,
                    created_at BIGINT NOT NULL,
                    updated_at BIGINT NOT NULL
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS transactions (
                    transaction_id VARCHAR(64) PRIMARY KEY,
                    player_uuid VARCHAR(36) NOT NULL,
                    nickname VARCHAR(16) NOT NULL,
                    coins BIGINT NOT NULL,
                    amount BIGINT NOT NULL,
                    currency VARCHAR(8) NOT NULL DEFAULT 'IDR',
                    status VARCHAR(32) NOT NULL,
                    gateway VARCHAR(32) NOT NULL,
                    created_at BIGINT NOT NULL,
                    paid_at BIGINT
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS coin_history (
                    history_id VARCHAR(36) PRIMARY KEY,
                    player_uuid VARCHAR(36) NOT NULL,
                    amount BIGINT NOT NULL,
                    action VARCHAR(32) NOT NULL,
                    reference_id VARCHAR(64),
                    description VARCHAR(255),
                    created_at BIGINT NOT NULL
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS processed_events (
                    event_id VARCHAR(128) PRIMARY KEY,
                    transaction_id VARCHAR(64) NOT NULL,
                    processed_at BIGINT NOT NULL
                )
                """
        };

        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {

            for (String sql : tables) {
                statement.executeUpdate(sql);
            }
        }
    }

    private void createIndexes() throws SQLException {
        String[][] indexes = {
                {"players", "idx_players_username",
                        "CREATE INDEX idx_players_username ON players(username)"},
                {"transactions", "idx_transactions_player",
                        "CREATE INDEX idx_transactions_player ON transactions(player_uuid)"},
                {"transactions", "idx_transactions_status",
                        "CREATE INDEX idx_transactions_status ON transactions(status)"},
                {"coin_history", "idx_coin_history_player",
                        "CREATE INDEX idx_coin_history_player ON coin_history(player_uuid)"},
                {"coin_history", "idx_coin_history_reference",
                        "CREATE INDEX idx_coin_history_reference ON coin_history(reference_id)"},
                {"processed_events", "idx_processed_events_transaction",
                        "CREATE INDEX idx_processed_events_transaction ON processed_events(transaction_id)"}
        };

        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {

            DatabaseMetaData metadata = connection.getMetaData();

            for (String[] index : indexes) {
                if (!indexExists(metadata, connection, index[0], index[1])) {
                    statement.executeUpdate(index[2]);
                }
            }
        }
    }

    private boolean indexExists(
            DatabaseMetaData metadata,
            Connection connection,
            String table,
            String indexName
    ) throws SQLException {

        String catalog = connection.getCatalog();

        for (String tableName : new String[]{
                table,
                table.toUpperCase(Locale.ROOT)
        }) {
            try (ResultSet result = metadata.getIndexInfo(
                    catalog, null, tableName, false, false
            )) {
                while (result.next()) {
                    String existingName = result.getString("INDEX_NAME");

                    if (existingName != null
                            && existingName.equalsIgnoreCase(indexName)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public void close() {
        plugin.getLogger().info("Database manager closed.");
    }

    public String getDatabaseType() {
        return databaseType;
    }
}
