
const path = require("path");
const fs = require("fs");
const Database = require("better-sqlite3");

const databaseDirectory = path.join(__dirname, "../../data");

if (!fs.existsSync(databaseDirectory)) {
    fs.mkdirSync(databaseDirectory, { recursive: true });
}

const databasePath = path.join(
    databaseDirectory,
    "novaterra.db"
);

const db = new Database(databasePath);

db.pragma("journal_mode = WAL");
db.pragma("foreign_keys = ON");

db.exec(`
    CREATE TABLE IF NOT EXISTS transactions (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        order_id TEXT NOT NULL UNIQUE,
        player_uuid TEXT NOT NULL,
        player_name TEXT NOT NULL,
        coin_amount INTEGER NOT NULL,
        payment_amount INTEGER NOT NULL,
        payment_method TEXT NOT NULL DEFAULT 'QRIS',
        transaction_status TEXT NOT NULL DEFAULT 'PENDING',
        midtrans_transaction_id TEXT,
        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
        paid_at TEXT
    );

    CREATE TABLE IF NOT EXISTS processed_events (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        event_id TEXT NOT NULL UNIQUE,
        order_id TEXT NOT NULL,
        processed_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
    );
`);

module.exports = db;
