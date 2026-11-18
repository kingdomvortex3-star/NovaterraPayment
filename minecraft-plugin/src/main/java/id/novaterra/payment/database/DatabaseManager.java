
package id.novaterra.payment.database;

import id.novaterra.payment.NovaTerraPayment;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

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
                .toUpperCase();

        if (databaseType.equals("SQLITE")) {
            initializeSQLite();
        } else if (databaseType.equals("MYSQL")) {
            initializeMySQL();
        } else {
            throw new SQLException(
                    "Unsupported database type: " + databaseType
            );
        }

        createTables();

        plugin.getLogger().info(
                "Database connected successfully: " + databaseType
        );
    }

    private void initializeSQLite() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException exception) {
            throw new SQLException(
                    "SQLite JDBC driver not found!", exception
            );
        }

        File folder = plugin.getDataFolder();

        if (!folder.exists() && !folder.mkdirs()) {
            throw new SQLException(
                    "Cannot create plugin data folder!"
            );
        }

        String filename = plugin.getConfig()
                .getString("database.sqlite.file", "database.db");

        File databaseFile = new File(folder, filename);

        sqlitePath = "jdbc:sqlite:" + databaseFile.getAbsolutePath();

        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {

            statement.execute("PRAGMA foreign_keys = ON");
        }
    }

    private void initializeMySQL() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException exception) {
            throw new SQLException(
                    "MySQL JDBC driver not found!", exception
            );
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

        mysqlUrl = "jdbc:mysql://" + host + ":" + port + "/"
                + database
                + "?useSSL=" + ssl
                + "&serverTimezone=UTC"
                + "&characterEncoding=UTF-8";
    }

    public Connection getConnection() throws SQLException {
        if ("SQLITE".equals(databaseType)) {
            return DriverManager.getConnection(sqlitePath);
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
                """
        };

        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {

            for (String sql : tables) {
                statement.executeUpdate(sql);
            }
        }
    }

    public void close() {
        plugin.getLogger().info("Database manager closed.");
    }

    public String getDatabaseType() {
        return databaseType;
    }
}
