
require("dotenv").config();

const express = require("express");
const midtransClient = require("midtrans-client");

const app = express();

app.disable("x-powered-by");
app.use(express.json({ limit: "100kb" }));

const PORT = Number(process.env.PORT || 3000);
const IS_PRODUCTION =
    process.env.MIDTRANS_IS_PRODUCTION === "true";

const SERVER_KEY = process.env.MIDTRANS_SERVER_KEY;
const CLIENT_KEY = process.env.MIDTRANS_CLIENT_KEY;

if (!SERVER_KEY || !CLIENT_KEY) {
    console.error(
        "[NOVATERRA] Midtrans Server Key atau Client Key belum diatur."
    );
    process.exit(1);
}

// Inisialisasi Midtrans Snap.
const snap = new midtransClient.Snap({
    isProduction: IS_PRODUCTION,
    serverKey: SERVER_KEY,
    clientKey: CLIENT_KEY
});

// Simpan client agar dapat digunakan oleh route berikutnya.
app.locals.midtrans = snap;

// Endpoint pemeriksaan server.
app.get("/health", (req, res) => {
    res.status(200).json({
        success: true,
        project: "NOVATERRA PAYMENT",
        developer: "Vortex Teams",
        status: "online",
        environment: IS_PRODUCTION
            ? "production"
            : "sandbox",
        timestamp: new Date().toISOString()
    });
});

// Endpoint yang tidak dikenal.
app.use((req, res) => {
    res.status(404).json({
        success: false,
        message: "Endpoint tidak ditemukan."
    });
});

// Penanganan error.
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

app.listen(PORT, "0.0.0.0", () => {
    console.log("====================================");
    console.log("       NOVATERRA PAYMENT API");
    console.log("       Developed by Vortex Teams");
    console.log("====================================");
    console.log("Port        : " + PORT);
    console.log("Environment : " +
        (IS_PRODUCTION ? "Production" : "Sandbox"));
    console.log("Midtrans    : Connected client initialized");
    console.log("====================================");
});
