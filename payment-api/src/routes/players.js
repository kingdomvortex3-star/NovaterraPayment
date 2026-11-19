
const UUID_REGEX =
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function validServerId(value) {
    return (
        typeof value === "string" &&
        /^[a-zA-Z0-9_-]{3,64}$/.test(value)
    );
}

module.exports = function registerPlayerRoutes(app, db) {
    db.exec(`
        CREATE TABLE IF NOT EXISTS player_identities (
            player_uuid TEXT NOT NULL,
            server_id TEXT NOT NULL,
            player_name TEXT NOT NULL,
            platform TEXT NOT NULL DEFAULT 'UNKNOWN',
            last_seen TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
            PRIMARY KEY (player_uuid, server_id)
        )
    `);

    db.exec(`
        CREATE INDEX IF NOT EXISTS
        idx_player_identities_name
        ON player_identities(player_name)
    `);

    // =====================================
    // SYNC PLAYER IDENTITY
    // POST /api/players/sync
    // =====================================

    app.post("/api/players/sync", (req, res) => {
        try {
            const body = req.body || {};

            const playerUuid =
                typeof body.player_uuid === "string"
                    ? body.player_uuid.trim().toLowerCase()
                    : "";

            const playerName =
                typeof body.player_name === "string"
                    ? body.player_name.trim()
                    : "";

            const serverId =
                typeof body.server_id === "string"
                    ? body.server_id.trim()
                    : "";

            const platform =
                typeof body.platform === "string"
                    ? body.platform.trim().toUpperCase()
                    : "UNKNOWN";

            if (!UUID_REGEX.test(playerUuid)) {
                return res.status(400).json({
                    success: false,
                    message: "UUID pemain tidak valid."
                });
            }

            if (
                playerName.length < 1 ||
                playerName.length > 32 ||
                /[\u0000-\u001F\u007F]/.test(playerName)
            ) {
                return res.status(400).json({
                    success: false,
                    message: "Nama pemain tidak valid."
                });
            }

            if (!validServerId(serverId)) {
                return res.status(400).json({
                    success: false,
                    message: "server_id tidak valid."
                });
            }

            if (!["JAVA", "BEDROCK", "UNKNOWN"].includes(platform)) {
                return res.status(400).json({
                    success: false,
                    message: "Platform pemain tidak valid."
                });
            }

            db.prepare(`
                INSERT INTO player_identities (
                    player_uuid,
                    server_id,
                    player_name,
                    platform,
                    last_seen
                )
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT(player_uuid, server_id)
                DO UPDATE SET
                    player_name = excluded.player_name,
                    platform = excluded.platform,
                    last_seen = CURRENT_TIMESTAMP
            `).run(
                playerUuid,
                serverId,
                playerName,
                platform
            );

            return res.json({
                success: true,
                message: "Identitas pemain berhasil disinkronkan.",
                player: {
                    uuid: playerUuid,
                    name: playerName,
                    platform,
                    server_id: serverId
                }
            });

        } catch (error) {
            console.error(
                "[NOVATERRA] Player sync error:",
                error.message
            );

            return res.status(500).json({
                success: false,
                message: "Gagal menyinkronkan identitas pemain."
            });
        }
    });

    console.log("[NOVATERRA] Player identity routes: Ready");
};
