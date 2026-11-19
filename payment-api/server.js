
require("dotenv").config();

const express = require("express");
const midtransClient = require("midtrans-client");
const fs = require("node:fs");
const path = require("node:path");
const { spawn } = require("node:child_process");

const initializeDatabase = require("./src/database/database");
const verifySignature = require("./src/security/auth");

const registerPaymentRoutes = require("./src/routes/payment");
const registerWebhook = require("./src/routes/webhook");
const registerDeliveryRoutes = require("./src/routes/delivery");
const registerPlayerRoutes = require("./src/routes/players");

let tunnelProcess = null;
let discordBot = null;
let httpServer = null;

async function startCloudflareTunnel(port) {
    if (process.env.CLOUDFLARE_TUNNEL_ENABLED === "false") {
        console.log("[CLOUDFLARE] Tunnel disabled.");
        return;
    }

    try {
        const { bin, install } = require("cloudflared");

        console.log("[CLOUDFLARE] Checking binary...");

        if (!fs.existsSync(bin)) {
            await install(bin);
        }

        console.log("[CLOUDFLARE] Starting Quick Tunnel...");

        tunnelProcess = spawn(
            bin,
            [
                "tunnel",
                "--url",
                `http://127.0.0.1:${port}`,
                "--no-autoupdate"
            ],
            {
                stdio: ["ignore", "pipe", "pipe"]
            }
        );

        const handleOutput = (data) => {
            const output = data.toString();

            const urlMatch = output.match(
                /https:\/\/[a-zA-Z0-9-]+\.trycloudflare\.com/
            );

            if (urlMatch) {
                console.log("====================================");
                console.log("[CLOUDFLARE] HTTPS TUNNEL READY");
                console.log("API URL : " + urlMatch[0]);
                console.log("Health  : " + urlMatch[0] + "/health");
                console.log("====================================");
            }

            const cleaned = output.trim();

            if (cleaned) {
                console.log("[CLOUDFLARE]", cleaned);
            }
        };

        tunnelProcess.stdout.on("data", handleOutput);
        tunnelProcess.stderr.on("data", handleOutput);

        tunnelProcess.on("error", (error) => {
            console.error(
                "[CLOUDFLARE] Process error:",
                error.message
            );
        });

        tunnelProcess.on("exit", (code, signal) => {
            console.log(
                `[CLOUDFLARE] Tunnel stopped. Code: ${code}, Signal: ${signal}`
            );

            tunnelProcess = null;
        });

    } catch (error) {
        console.error(
            "[CLOUDFLARE] Failed:",
            error.message
        );
    }
}

async function startDiscordBot(db, snap, app) {
    const token = process.env.DISCORD_TOKEN;
    const clientId = process.env.DISCORD_CLIENT_ID;
    const guildId = process.env.DISCORD_GUILD_ID;

    if (!token || !clientId || !guildId) {
        console.log(
            "[DISCORD] Bot disabled: Discord environment variables belum lengkap."
        );
        return;
    }

    const botPath = path.join(
        __dirname,
        "src",
        "discord",
        "bot.js"
    );

    if (!fs.existsSync(botPath)) {
        console.log(
            "[DISCORD] Bot source belum tersedia: src/discord/bot.js"
        );
        console.log("[DISCORD] API tetap berjalan normal.");
        return;
    }

    try {
        console.log("[DISCORD] Starting NOVATERRA PAYMENT Bot...");

        const discordModule = require(botPath);

        if (
            typeof discordModule.startDiscordBot !== "function"
        ) {
            throw new Error(
                "src/discord/bot.js harus mengekspor fungsi startDiscordBot."
            );
        }

        discordBot = await discordModule.startDiscordBot({
            db,
            snap,
            app,
            clientId,
            guildId
        });

        console.log("[DISCORD] Bot startup completed.");

    } catch (error) {
        discordBot = null;

        console.error(
            "[DISCORD] Bot startup failed:",
            error.message
        );

        console.error(
            "[DISCORD] API tetap berjalan. Periksa konfigurasi bot."
        );
    }
}

async function stopDiscordBot() {
    if (!discordBot) return;

    try {
        if (typeof discordBot.stop === "function") {
            await discordBot.stop();
        } else if (typeof discordBot.destroy === "function") {
            discordBot.destroy();
        }

        console.log("[DISCORD] Bot disconnected.");

    } catch (error) {
        console.error(
            "[DISCORD] Shutdown error:",
            error.message
        );
    }

    discordBot = null;
}

async function publishTestimonial(transaction) {
    if (!discordBot) {
        throw new Error("Discord Bot belum terhubung.");
    }

    if (
        typeof discordBot.publishTestimonial !== "function"
    ) {
        throw new Error(
            "Fungsi publishTestimonial belum tersedia di bot.js."
        );
    }

    return discordBot.publishTestimonial(transaction);
}

async function startServer() {
    const app = express();

    app.disable("x-powered-by");

    const PORT = Number(
        process.env.SERVER_PORT ||
        process.env.PORT ||
        3000
    );

    const SERVER_KEY = process.env.MIDTRANS_SERVER_KEY;
    const CLIENT_KEY = process.env.MIDTRANS_CLIENT_KEY;

    const IS_PRODUCTION =
        process.env.MIDTRANS_IS_PRODUCTION === "true";

    // ====================================
    // MIDTRANS CONFIGURATION DEBUG
    // ====================================

    console.log("====================================");
    console.log("[MIDTRANS DEBUG]");
    console.log("====================================");

    console.log(
        "Environment:",
        IS_PRODUCTION ? "PRODUCTION" : "SANDBOX"
    );

    console.log(
        "Server Key Loaded:",
        Boolean(SERVER_KEY)
    );

    console.log(
        "Server Key Format:",
        SERVER_KEY
            ? SERVER_KEY.startsWith(
                IS_PRODUCTION
                    ? "Mid-server-"
                    : "SB-Mid-server-"
            )
            : false
    );

    console.log(
        "Server Key Length:",
        SERVER_KEY ? SERVER_KEY.length : 0
    );

    console.log(
        "Client Key Loaded:",
        Boolean(CLIENT_KEY)
    );

    console.log(
        "Client Key Format:",
        CLIENT_KEY
            ? CLIENT_KEY.startsWith(
                IS_PRODUCTION
                    ? "Mid-client-"
                    : "SB-Mid-client-"
            )
            : false
    );

    console.log(
        "Client Key Length:",
        CLIENT_KEY ? CLIENT_KEY.length : 0
    );

    console.log("====================================");

    if (!SERVER_KEY || !CLIENT_KEY) {
        throw new Error(
            "Midtrans keys belum dikonfigurasi."
        );
    }

    if (!process.env.API_SECRET) {
        throw new Error(
            "API_SECRET belum dikonfigurasi."
        );
    }

    if (
        process.env.DISCORD_TOKEN &&
        (
            !process.env.DISCORD_CLIENT_ID ||
            !process.env.DISCORD_GUILD_ID
        )
    ) {
        console.warn(
            "[DISCORD] DISCORD_CLIENT_ID atau DISCORD_GUILD_ID belum diisi."
        );
    }

    // ====================================
    // DATABASE
    // ====================================

    const db = await initializeDatabase();

    // ====================================
    // MIDTRANS
    // ====================================

    const snap = new midtransClient.Snap({
        isProduction: IS_PRODUCTION,
        serverKey: SERVER_KEY,
        clientKey: CLIENT_KEY
    });

    app.locals.midtrans = snap;
    app.locals.database = db;

    // ====================================
    // HTTP REQUEST LOGGER
    // ====================================

    app.use((req, res, next) => {
        const startedAt = Date.now();

        res.on("finish", () => {
            const duration = Date.now() - startedAt;

            console.log(
                `[HTTP] ${req.method} ${req.path} ` +
                `${res.statusCode} - ${duration}ms`
            );
        });

        next();
    });

    // ====================================
    // JSON PARSER
    // ====================================

    app.use(express.json({ limit: "100kb" }));

    // ====================================
    // HEALTH CHECK
    // ====================================

    app.get("/health", (req, res) => {
        res.json({
            success: true,
            project: "NOVATERRA PAYMENT",
            developer: "Vortex Teams",
            status: "online",
            environment: IS_PRODUCTION
                ? "production"
                : "sandbox",
            discord: discordBot
                ? "connected"
                : "not_started",
            testimonial: Boolean(
                discordBot &&
                typeof discordBot.publishTestimonial === "function"
            )
                ? "ready"
                : "not_ready"
        });
    });

    // ====================================
    // API AUTHENTICATION
    // ====================================

    app.use("/api/payment", verifySignature);
    app.use("/api/delivery", verifySignature);
    app.use("/api/players", verifySignature);

    // ====================================
    // REGISTER API ROUTES
    // ====================================

    registerPaymentRoutes(app, db);

    registerWebhook(
        app,
        db,
        publishTestimonial
    );

    registerDeliveryRoutes(app, db);
    registerPlayerRoutes(app, db);

    // ====================================
    // 404 HANDLER
    // ====================================

    app.use((req, res) => {
        res.status(404).json({
            success: false,
            message: "Endpoint tidak ditemukan."
        });
    });

    // ====================================
    // GLOBAL ERROR HANDLER
    // ====================================

    app.use((error, req, res, next) => {
        console.error(
            "[NOVATERRA] Server error:",
            error.message
        );

        if (res.headersSent) {
            return next(error);
        }

        res.status(500).json({
            success: false,
            message: "Terjadi kesalahan pada server."
        });
    });

    // ====================================
    // START HTTP SERVER
    // ====================================

    httpServer = app.listen(PORT, "0.0.0.0", () => {
        console.log("====================================");
        console.log("       NOVATERRA PAYMENT API");
        console.log("       Developed by Vortex Teams");
        console.log("====================================");
        console.log("Port           : " + PORT);
        console.log(
            "Environment    : " +
            (IS_PRODUCTION ? "Production" : "Sandbox")
        );
        console.log("Database       : SQLite initialized");
        console.log("Authentication : Enabled");
        console.log("Midtrans       : Initialized");
        console.log("Coin Delivery  : Enabled");
        console.log("Player Sync    : Enabled");
        console.log("Request Logger : Enabled");
        console.log("Discord Bot    : Preparing...");
        console.log("Testimonial    : Enabled");
        console.log("Cloudflare     : Starting...");
        console.log("====================================");

        startCloudflareTunnel(PORT);

        startDiscordBot(db, snap, app);
    });

    // ====================================
    // GRACEFUL SHUTDOWN
    // ====================================

    let shuttingDown = false;

    async function shutdown() {
        if (shuttingDown) return;

        shuttingDown = true;

        console.log("[NOVATERRA] Shutting down...");

        if (tunnelProcess) {
            tunnelProcess.kill("SIGTERM");
            tunnelProcess = null;
        }

        await stopDiscordBot();

        if (httpServer) {
            httpServer.close(() => {
                try {
                    db.close();

                    console.log(
                        "[NOVATERRA] Database closed."
                    );

                } catch (error) {
                    console.error(
                        "[NOVATERRA] Database close error:",
                        error.message
                    );
                }

                process.exit(0);
            });
        }

        setTimeout(() => {
            console.error(
                "[NOVATERRA] Forced shutdown."
            );

            process.exit(1);
        }, 10000).unref();
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
