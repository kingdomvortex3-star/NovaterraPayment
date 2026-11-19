
const MAX_BATCH_SIZE = 20;
const MAX_ATTEMPTS = 10;
const LEASE_SECONDS = 180;

function validServerId(value) {
    return (
        typeof value === "string" &&
        /^[a-zA-Z0-9_-]{3,64}$/.test(value)
    );
}

function validOrderId(value) {
    return (
        typeof value === "string" &&
        value.length >= 1 &&
        value.length <= 64
    );
}

function registerDeliveryRoutes(app, db) {
    // =====================================
    // DATABASE MIGRATIONS
    // =====================================

    const columns = db.prepare(`
        PRAGMA table_info(coin_delivery_jobs)
    `).all();

    const columnNames = columns.map(column => column.name);

    if (!columnNames.includes("claimed_by")) {
        db.exec(`
            ALTER TABLE coin_delivery_jobs
            ADD COLUMN claimed_by TEXT
        `);
    }

    if (!columnNames.includes("lease_expires_at")) {
        db.exec(`
            ALTER TABLE coin_delivery_jobs
            ADD COLUMN lease_expires_at TEXT
        `);
    }

    // =====================================
    // CLAIM DELIVERY JOBS
    // POST /api/delivery/claim
    // =====================================

    app.post("/api/delivery/claim", (req, res) => {
        try {
            const { server_id, limit } = req.body || {};

            if (!validServerId(server_id)) {
                return res.status(400).json({
                    success: false,
                    message: "server_id tidak valid."
                });
            }

            const requestedLimit = Number(limit);

            const batchSize = Math.min(
                Math.max(
                    Number.isInteger(requestedLimit)
                        ? requestedLimit
                        : 1,
                    1
                ),
                MAX_BATCH_SIZE
            );

            const claimJobs = db.transaction(() => {
                const jobs = db.prepare(`
                    SELECT j.*
                    FROM coin_delivery_jobs j
                    INNER JOIN transactions t
                        ON t.order_id = j.order_id
                    WHERE
                        t.transaction_status = 'PAID'
                        AND (
                            j.delivery_status = 'PENDING'
                            OR (
                                j.delivery_status = 'PROCESSING'
                                AND j.lease_expires_at IS NOT NULL
                                AND j.lease_expires_at <= CURRENT_TIMESTAMP
                            )
                        )
                    ORDER BY j.id ASC
                    LIMIT ?
                `).all(batchSize);

                const claimed = [];

                for (const job of jobs) {
                    if (Number(job.attempts) >= MAX_ATTEMPTS) {
                        db.prepare(`
                            UPDATE coin_delivery_jobs
                            SET
                                delivery_status = 'FAILED',
                                claimed_by = NULL,
                                lease_expires_at = NULL,
                                updated_at = CURRENT_TIMESTAMP,
                                last_error = COALESCE(
                                    last_error,
                                    'Maximum delivery attempts reached.'
                                )
                            WHERE id = ?
                            AND delivery_status != 'DELIVERED'
                        `).run(job.id);

                        continue;
                    }

                    const result = db.prepare(`
                        UPDATE coin_delivery_jobs
                        SET
                            delivery_status = 'PROCESSING',
                            claimed_by = ?,
                            lease_expires_at = datetime(
                                'now',
                                '+${LEASE_SECONDS} seconds'
                            ),
                            attempts = attempts + 1,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                        AND EXISTS (
                            SELECT 1
                            FROM transactions t
                            WHERE t.order_id = coin_delivery_jobs.order_id
                            AND t.transaction_status = 'PAID'
                        )
                        AND (
                            delivery_status = 'PENDING'
                            OR (
                                delivery_status = 'PROCESSING'
                                AND lease_expires_at <= CURRENT_TIMESTAMP
                            )
                        )
                    `).run(server_id, job.id);

                    if (result.changes > 0) {
                        claimed.push({
                            order_id: job.order_id,
                            player_uuid: job.player_uuid,
                            player_name: job.player_name,
                            coin_amount: Number(job.coin_amount),
                            attempts: Number(job.attempts) + 1
                        });
                    }
                }

                return claimed;
            });

            const jobs = claimJobs();

            return res.json({
                success: true,
                count: jobs.length,
                lease_seconds: LEASE_SECONDS,
                jobs
            });

        } catch (error) {
            console.error(
                "[NOVATERRA] Delivery claim error:",
                error.message
            );

            return res.status(500).json({
                success: false,
                message: "Gagal mengambil antrean Coin."
            });
        }
    });

    // =====================================
    // RENEW DELIVERY LEASE
    // POST /api/delivery/renew
    // =====================================

    app.post("/api/delivery/renew", (req, res) => {
        try {
            const { server_id, order_id } = req.body || {};

            if (
                !validServerId(server_id) ||
                !validOrderId(order_id)
            ) {
                return res.status(400).json({
                    success: false,
                    message: "Data perpanjangan lease tidak valid."
                });
            }

            const result = db.transaction(() => {
                const job = db.prepare(`
                    SELECT
                        j.delivery_status,
                        j.claimed_by,
                        t.transaction_status
                    FROM coin_delivery_jobs j
                    INNER JOIN transactions t
                        ON t.order_id = j.order_id
                    WHERE j.order_id = ?
                `).get(order_id);

                if (!job) {
                    return "NOT_FOUND";
                }

                if (job.delivery_status === "DELIVERED") {
                    return "ALREADY_DELIVERED";
                }

                if (
                    job.delivery_status !== "PROCESSING" ||
                    job.claimed_by !== server_id
                ) {
                    return "NOT_CLAIMED";
                }

                if (job.transaction_status !== "PAID") {
                    db.prepare(`
                        UPDATE coin_delivery_jobs
                        SET
                            delivery_status = 'REFUND_REQUIRED',
                            claimed_by = NULL,
                            lease_expires_at = NULL,
                            last_error = 'Payment is no longer PAID. Reconciliation required.',
                            updated_at = CURRENT_TIMESTAMP
                        WHERE order_id = ?
                        AND claimed_by = ?
                    `).run(order_id, server_id);

                    return "PAYMENT_NOT_PAID";
                }

                db.prepare(`
                    UPDATE coin_delivery_jobs
                    SET
                        lease_expires_at = datetime(
                            'now',
                            '+${LEASE_SECONDS} seconds'
                        ),
                        updated_at = CURRENT_TIMESTAMP
                    WHERE order_id = ?
                    AND claimed_by = ?
                    AND delivery_status = 'PROCESSING'
                `).run(order_id, server_id);

                return "RENEWED";
            })();

            if (result === "NOT_FOUND") {
                return res.status(404).json({
                    success: false,
                    message: "Pekerjaan Coin tidak ditemukan."
                });
            }

            if (result === "NOT_CLAIMED") {
                return res.status(409).json({
                    success: false,
                    message: "Pekerjaan tidak dimiliki server ini."
                });
            }

            if (result === "PAYMENT_NOT_PAID") {
                return res.status(409).json({
                    success: false,
                    status: result,
                    message: "Pembayaran tidak lagi berstatus PAID."
                });
            }

            return res.json({
                success: true,
                status: result,
                lease_seconds: LEASE_SECONDS
            });

        } catch (error) {
            console.error(
                "[NOVATERRA] Delivery renew error:",
                error.message
            );

            return res.status(500).json({
                success: false,
                message: "Gagal memperpanjang lease."
            });
        }
    });

    // =====================================
    // COMPLETE DELIVERY
    // POST /api/delivery/complete
    // =====================================

    app.post("/api/delivery/complete", (req, res) => {
        try {
            const { server_id, order_id } = req.body || {};

            if (
                !validServerId(server_id) ||
                !validOrderId(order_id)
            ) {
                return res.status(400).json({
                    success: false,
                    message: "Data konfirmasi tidak valid."
                });
            }

            const result = db.transaction(() => {
                const job = db.prepare(`
                    SELECT
                        j.delivery_status,
                        j.claimed_by,
                        t.transaction_status
                    FROM coin_delivery_jobs j
                    INNER JOIN transactions t
                        ON t.order_id = j.order_id
                    WHERE j.order_id = ?
                `).get(order_id);

                if (!job) {
                    return "NOT_FOUND";
                }

                if (job.delivery_status === "DELIVERED") {
                    return "ALREADY_DELIVERED";
                }

                if (
                    job.delivery_status !== "PROCESSING" ||
                    job.claimed_by !== server_id
                ) {
                    if (job.delivery_status === "REFUND_REQUIRED") {
                        return "REFUND_REQUIRED";
                    }

                    return "NOT_CLAIMED";
                }

                if (job.transaction_status !== "PAID") {
                    db.prepare(`
                        UPDATE coin_delivery_jobs
                        SET
                            delivery_status = 'REFUND_REQUIRED',
                            claimed_by = NULL,
                            lease_expires_at = NULL,
                            last_error = 'Payment is no longer PAID. Reconciliation required.',
                            updated_at = CURRENT_TIMESTAMP
                        WHERE order_id = ?
                        AND claimed_by = ?
                    `).run(order_id, server_id);

                    return "REFUND_REQUIRED";
                }

                db.prepare(`
                    UPDATE coin_delivery_jobs
                    SET
                        delivery_status = 'DELIVERED',
                        delivered_at = CURRENT_TIMESTAMP,
                        updated_at = CURRENT_TIMESTAMP,
                        claimed_by = NULL,
                        lease_expires_at = NULL,
                        last_error = NULL
                    WHERE order_id = ?
                    AND claimed_by = ?
                    AND delivery_status = 'PROCESSING'
                `).run(order_id, server_id);

                return "DELIVERED";
            })();

            if (result === "NOT_FOUND") {
                return res.status(404).json({
                    success: false,
                    message: "Pekerjaan Coin tidak ditemukan."
                });
            }

            if (result === "NOT_CLAIMED") {
                return res.status(409).json({
                    success: false,
                    message: "Pekerjaan tidak dimiliki server ini."
                });
            }

            if (result === "REFUND_REQUIRED") {
                return res.status(409).json({
                    success: false,
                    status: result,
                    message: "Pembayaran memerlukan rekonsiliasi refund."
                });
            }

            return res.json({
                success: true,
                status: result
            });

        } catch (error) {
            console.error(
                "[NOVATERRA] Delivery complete error:",
                error.message
            );

            return res.status(500).json({
                success: false,
                message: "Gagal mengonfirmasi pengiriman Coin."
            });
        }
    });

    // =====================================
    // REPORT DELIVERY FAILURE
    // POST /api/delivery/fail
    // =====================================

    app.post("/api/delivery/fail", (req, res) => {
        try {
            const {
                server_id,
                order_id,
                error: deliveryError
            } = req.body || {};

            if (
                !validServerId(server_id) ||
                !validOrderId(order_id)
            ) {
                return res.status(400).json({
                    success: false,
                    message: "Data laporan gagal tidak valid."
                });
            }

            const errorMessage = String(
                deliveryError || "Unknown delivery error."
            ).slice(0, 500);

            const result = db.transaction(() => {
                const job = db.prepare(`
                    SELECT
                        j.delivery_status,
                        j.claimed_by,
                        j.attempts,
                        t.transaction_status
                    FROM coin_delivery_jobs j
                    INNER JOIN transactions t
                        ON t.order_id = j.order_id
                    WHERE j.order_id = ?
                `).get(order_id);

                if (!job) {
                    return "NOT_FOUND";
                }

                if (job.delivery_status === "DELIVERED") {
                    return "ALREADY_DELIVERED";
                }

                if (job.delivery_status === "REFUND_REQUIRED") {
                    return "REFUND_REQUIRED";
                }

                if (
                    job.delivery_status !== "PROCESSING" ||
                    job.claimed_by !== server_id
                ) {
                    return "NOT_CLAIMED";
                }

                const nextStatus =
                    job.transaction_status !== "PAID"
                        ? "REFUND_REQUIRED"
                        : Number(job.attempts) >= MAX_ATTEMPTS
                            ? "FAILED"
                            : "PENDING";

                db.prepare(`
                    UPDATE coin_delivery_jobs
                    SET
                        delivery_status = ?,
                        last_error = ?,
                        claimed_by = NULL,
                        lease_expires_at = NULL,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE order_id = ?
                    AND claimed_by = ?
                    AND delivery_status = 'PROCESSING'
                `).run(
                    nextStatus,
                    errorMessage,
                    order_id,
                    server_id
                );

                return nextStatus;
            })();

            if (result === "NOT_FOUND") {
                return res.status(404).json({
                    success: false,
                    message: "Pekerjaan Coin tidak ditemukan."
                });
            }

            if (result === "NOT_CLAIMED") {
                return res.status(409).json({
                    success: false,
                    message: "Pekerjaan tidak dimiliki server ini."
                });
            }

            if (result === "REFUND_REQUIRED") {
                return res.status(409).json({
                    success: false,
                    status: result,
                    message: "Pembayaran memerlukan rekonsiliasi refund."
                });
            }

            return res.json({
                success: true,
                status: result
            });

        } catch (error) {
            console.error(
                "[NOVATERRA] Delivery failure error:",
                error.message
            );

            return res.status(500).json({
                success: false,
                message: "Gagal melaporkan pengiriman Coin."
            });
        }
    });

    console.log("[NOVATERRA] Coin delivery routes: Ready");
}

module.exports = registerDeliveryRoutes;
