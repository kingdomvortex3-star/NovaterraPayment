
require("dotenv").config();

const express = require("express");
const midtransClient = require("midtrans-client");

const initializeDatabase = require("./src/database/database");
const verifySignature = require("./src/security/auth");

const registerPaymentRoutes = require("./src/routes/payment");
const registerWebhook = require("./src/routes/webhook");
const registerDeliveryRoutes = require("./src/routes/delivery");

async function startServer() {
    const app = express();

    app.disable("x-powered-by");
    app.use(express.json({ limit: "100kb" }));

    const PORT = Number(
        process.env.SERVER_PORT ||
        process.env.PORT ||
        3000
    );

    const SERVER_KEY = process.env.MIDTRANS_SERVER_KEY;
    const CLIENT_KEY = process.env.MIDTRANS_CLIENT_KEY;

    const IS_PRODUCTION =
        process.env.MIDTRANS_IS_PRODUCTION === "true";

    if (!SERVER_KEY || !CLIENT_KEY) {
        throw new Error("Midtrans keys belum dikonfigurasi.");
    }

    if (!process.env.API_SECRET) {
        throw new Error("API_SECRET belum dikonfigurasi.");
    }

    const db = await initializeDatabase();

    const snap = new midtransClient.Snap({
        isProduction: IS_PRODUCTION,
        serverKey: SERVER_KEY,
        clientKey: CLIENT_KEY
    });

    app.locals.midtrans = snap;
    app.locals.database = db;

    app.get("/health", (req, res) => {
        res.json({
            success: true,
            project: "NOVATERRA PAYMENT",
            developer: "Vortex Teams",
            status: "online",
            environment: IS_PRODUCTION
                ? "production"
                : "sandbox"
        });
    });

    // API authentication
    app.use("/api/payment", verifySignature);
    app.use("/api/delivery", verifySignature);

    // Register routes
    registerPaymentRoutes(app, db);
    registerWebhook(app, db);
    registerDeliveryRoutes(app, db);

    // 404 handler
    app.use((req, res) => {
        res.status(404).json({
            success: false,
            message: "Endpoint tidak ditemukan."
        });
    });

    // Error handler
    app.use((error, req, res, next) => {
        console.error(
            "[NOVATERRA] Server error:",
            error.message
        );

        res.status(500).json({
            success: false,
            message: "Terjadi kesalahan pada server."
        });
    });

    const server = app.listen(PORT, "0.0.0.0", () => {
        console.log("====================================");
        console.log("       NOVATERRA PAYMENT API");
        console.log("       Developed by Vortex Teams");
        console.log("====================================");
        console.log("Port           : " + PORT);
        console.log("Environment    : " +
            (IS_PRODUCTION ? "Production" : "Sandbox"));
        console.log("Database       : SQLite initialized");
        console.log("Authentication : Enabled");
        console.log("Midtrans       : Initialized");
        console.log("Coin Delivery  : Enabled");
        console.log("====================================");
    });

    function shutdown() {
        console.log("[NOVATERRA] Shutting down...");

        server.close(() => {
            db.close();
            process.exit(0);
        });
    }

    process.on("SIGTERM", shutdown);
    process.on("SIGINT", shutdown);
}

startServer().catch(error => {
    console.error(
        "[NOVATERRA] Startup failed:",
        error.message
    );

    process.exit(1);
});
