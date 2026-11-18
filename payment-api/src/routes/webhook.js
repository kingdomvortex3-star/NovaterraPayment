
const fs = require("fs");
const path = require("path");
const initSqlJs = require("sql.js");

async function initializeDatabase() {
    const SQL = await initSqlJs();

    const dataDirectory = path.join(
        process.cwd(),
        "data"
    );

    fs.mkdirSync(dataDirectory, { recursive: true });

    const databasePath = path.join(
        dataDirectory,
        "novaterra.db"
    );

    const temporaryPath = databasePath + ".tmp";

    const database = fs.existsSync(databasePath)
        ? new SQL.Database(
            new Uint8Array(fs.readFileSync(databasePath))
        )
        : new SQL.Database();

    let transactionDepth = 0;

    function saveDatabase() {
        const exported = database.export();

        fs.writeFileSync(
            temporaryPath,
            Buffer.from(exported)
        );

        fs.renameSync(temporaryPath, databasePath);
    }

    database.run("PRAGMA foreign_keys = ON");

    // =====================================
    // TRANSACTIONS
    // =====================================

    database.run(`
        CREATE TABLE IF NOT EXISTS transactions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            order_id TEXT NOT NULL UNIQUE,
            player_uuid TEXT NOT NULL,
            player_name TEXT NOT NULL,
            coin_amount INTEGER NOT NULL CHECK (coin_amount > 0),
            payment_amount INTEGER NOT NULL CHECK (payment_amount > 0),
            payment_method TEXT NOT NULL DEFAULT 'QRIS',
            transaction_status TEXT NOT NULL DEFAULT 'PENDING',
            midtrans_transaction_id TEXT,
            created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
            paid_at TEXT
        )
    `);

    // =====================================
    // PROCESSED WEBHOOK EVENTS
    // =====================================

    database.run(`
        CREATE TABLE IF NOT EXISTS processed_events (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            event_id TEXT NOT NULL UNIQUE,
            order_id TEXT NOT NULL,
            processed_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
    `);

    // =====================================
    // COIN DELIVERY JOBS
    // =====================================

    database.run(`
        CREATE TABLE IF NOT EXISTS coin_delivery_jobs (
            id INTEGER PRIMARY KEY AUTOINCREMENT,

            order_id TEXT NOT NULL UNIQUE,

            player_uuid TEXT NOT NULL,
            player_name TEXT NOT NULL,

            coin_amount INTEGER NOT NULL
                CHECK (coin_amount > 0),

            delivery_status TEXT NOT NULL DEFAULT 'PENDING',

            attempts INTEGER NOT NULL DEFAULT 0,

            last_error TEXT,

            created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
            delivered_at TEXT,

            FOREIGN KEY (order_id)
                REFERENCES transactions(order_id)
        )
    `);

    // =====================================
    // INDEXES
    // =====================================

    database.run(`
        CREATE INDEX IF NOT EXISTS
        idx_transactions_player
        ON transactions(player_uuid)
    `);

    database.run(`
        CREATE INDEX IF NOT EXISTS
        idx_transactions_status
        ON transactions(transaction_status)
    `);

    database.run(`
        CREATE INDEX IF NOT EXISTS
        idx_delivery_status
        ON coin_delivery_jobs(delivery_status)
    `);

    database.run(`
        CREATE INDEX IF NOT EXISTS
        idx_delivery_player
        ON coin_delivery_jobs(player_uuid)
    `);

    saveDatabase();

    // =====================================
    // DATABASE ADAPTER
    // =====================================

    const db = {
        prepare(sql) {
            return {
                get(...params) {
                    const statement = database.prepare(sql);

                    try {
                        statement.bind(params);

                        if (!statement.step()) {
                            return undefined;
                        }

                        return statement.getAsObject();
                    } finally {
                        statement.free();
                    }
                },

                all(...params) {
                    const statement = database.prepare(sql);
                    const rows = [];

                    try {
                        statement.bind(params);

                        while (statement.step()) {
                            rows.push(statement.getAsObject());
                        }

                        return rows;
                    } finally {
                        statement.free();
                    }
                },

                run(...params) {
                    database.run(sql, params);

                    const result = {
                        changes: database.getRowsModified(),
                        lastInsertRowid:
                            database.exec(
                                "SELECT last_insert_rowid() AS id"
                            )[0]?.values[0][0] || 0
                    };

                    if (transactionDepth === 0) {
                        saveDatabase();
                    }

                    return result;
                }
            };
        },

        exec(sql) {
            database.exec(sql);

            if (transactionDepth === 0) {
                saveDatabase();
            }
        },

        transaction(callback) {
            return (...args) => {
                if (transactionDepth !== 0) {
                    throw new Error(
                        "Nested database transactions are not supported."
                    );
                }

                database.run("BEGIN TRANSACTION");
                transactionDepth++;

                let result;

                try {
                    result = callback(...args);
                    database.run("COMMIT");
                } catch (error) {
                    try {
                        database.run("ROLLBACK");
                    } catch (rollbackError) {
                        console.error(
                            "[NOVATERRA] Rollback error:",
                            rollbackError.message
                        );
                    }

                    transactionDepth = 0;
                    throw error;
                }

                transactionDepth = 0;

                // Simpan setelah transaksi berhasil di-commit.
                saveDatabase();

                return result;
            };
        },

        close() {
            saveDatabase();
            database.close();
        }
    };

    console.log("[NOVATERRA] SQLite database initialized.");
    console.log("[NOVATERRA] Database: data/novaterra.db");
    console.log("[NOVATERRA] Transaction table: Ready");
    console.log("[NOVATERRA] Webhook event table: Ready");
    console.log("[NOVATERRA] Coin delivery queue: Ready");

    return db;
}

module.exports = initializeDatabase;
