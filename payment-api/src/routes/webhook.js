
const crypto = require("crypto");

function safeCompareHex(received, expected) {
    if (
        typeof received !== "string" ||
        typeof expected !== "string" ||
        !/^[a-f0-9]{128}$/i.test(received) ||
        !/^[a-f0-9]{128}$/i.test(expected)
    ) {
        return false;
    }

    const first = Buffer.from(received, "hex");
    const second = Buffer.from(expected, "hex");

    return crypto.timingSafeEqual(first, second);
}

function getPaymentStatus(status, fraudStatus) {
    switch (status) {
        case "settlement":
            return "PAID";

        case "capture":
            return fraudStatus === "accept" ? "PAID" : null;

        case "pending":
            return "PENDING";

        case "expire":
            return "EXPIRED";

        case "cancel":
            return "CANCELLED";

        case "deny":
            return "FAILED";

        case "refund":
        case "chargeback":
            return "REFUNDED";

        default:
            return null;
    }
}

module.exports = function registerWebhook(
    app,
    db,
    notifyTestimonial = null
) {
    const serverKey = process.env.MIDTRANS_SERVER_KEY;

    if (!serverKey) {
        throw new Error(
            "MIDTRANS_SERVER_KEY belum dikonfigurasi."
        );
    }

    // =====================================
    // GET HEALTH CHECK
    // =====================================

    app.get("/webhook/midtrans", (req, res) => {
        return res.status(200).json({
            success: true,
            project: "NOVATERRA PAYMENT",
            message: "Midtrans Webhook is ready."
        });
    });

    // =====================================
    // MIDTRANS NOTIFICATION
    // =====================================

    app.post("/webhook/midtrans", async (req, res) => {
        try {
            const body = req.body || {};

            const {
                order_id,
                status_code,
                gross_amount,
                signature_key,
                transaction_status,
                fraud_status,
                transaction_id
            } = body;

            if (
                typeof order_id !== "string" ||
                typeof status_code !== "string" ||
                typeof gross_amount !== "string" ||
                typeof transaction_status !== "string"
            ) {
                return res.status(400).json({
                    success: false,
                    message: "Notifikasi tidak lengkap."
                });
            }

            // =================================
            // MIDTRANS TEST NOTIFICATION
            // =================================

            if (order_id.startsWith("payment_notif_test_")) {
                console.log(
                    "[NOVATERRA] Midtrans test notification received:",
                    order_id
                );

                return res.status(200).json({
                    success: true,
                    test: true,
                    message: "Midtrans test notification received."
                });
            }

            // =================================
            // VERIFY REAL PAYMENT SIGNATURE
            // =================================

            if (typeof signature_key !== "string") {
                return res.status(400).json({
                    success: false,
                    message: "Signature tidak ditemukan."
                });
            }

            const signatureInput =
                order_id +
                status_code +
                gross_amount +
                serverKey;

            const expectedSignature = crypto
                .createHash("sha512")
                .update(signatureInput)
                .digest("hex");

            if (
                !safeCompareHex(
                    signature_key,
                    expectedSignature
                )
            ) {
                console.warn(
                    "[NOVATERRA] Invalid Midtrans signature:",
                    order_id
                );

                return res.status(403).json({
                    success: false,
                    message: "Signature tidak valid."
                });
            }

            // =================================
            // FIND TRANSACTION
            // =================================

            const transaction = db.prepare(`
                SELECT *
                FROM transactions
                WHERE order_id = ?
            `).get(order_id);

            if (!transaction) {
                return res.status(404).json({
                    success: false,
                    message: "Transaksi tidak ditemukan."
                });
            }

            // =================================
            // VALIDATE AMOUNT
            // =================================

            const receivedAmount = Number(gross_amount);
            const expectedAmount = Number(
                transaction.payment_amount
            );

            if (
                !Number.isFinite(receivedAmount) ||
                !Number.isFinite(expectedAmount) ||
                Math.round(receivedAmount * 100) !==
                Math.round(expectedAmount * 100)
            ) {
                console.warn(
                    "[NOVATERRA] Payment amount mismatch:",
                    order_id
                );

                return res.status(400).json({
                    success: false,
                    message: "Nominal pembayaran tidak cocok."
                });
            }

            const requestedStatus = getPaymentStatus(
                transaction_status,
                fraud_status
            );

            const eventId = crypto
                .createHash("sha256")
                .update([
                    order_id,
                    transaction_status,
                    status_code,
                    gross_amount,
                    transaction_id || ""
                ].join(":"))
                .digest("hex");

            // =================================
            // PROCESS DATABASE TRANSACTION
            // =================================

            const processEvent = db.transaction(() => {
                const existingEvent = db.prepare(`
                    SELECT id
                    FROM processed_events
                    WHERE event_id = ?
                `).get(eventId);

                if (existingEvent) {
                    const latest = db.prepare(`
                        SELECT transaction_status
                        FROM transactions
                        WHERE order_id = ?
                    `).get(order_id);

                    return {
                        processed: false,
                        status: latest
                            ? latest.transaction_status
                            : transaction.transaction_status,
                        deliveryQueued: false
                    };
                }

                db.prepare(`
                    INSERT INTO processed_events (
                        event_id,
                        order_id
                    )
                    VALUES (?, ?)
                `).run(eventId, order_id);

                const latestTransaction = db.prepare(`
                    SELECT transaction_status
                    FROM transactions
                    WHERE order_id = ?
                `).get(order_id);

                const currentStatus =
                    latestTransaction.transaction_status;

                let finalStatus = currentStatus;

                // REFUNDED tidak boleh kembali menjadi PAID.
                if (currentStatus === "REFUNDED") {
                    finalStatus = "REFUNDED";
                } else if (requestedStatus === "REFUNDED") {
                    finalStatus = "REFUNDED";
                } else if (requestedStatus === "PAID") {
                    finalStatus = "PAID";
                } else if (
                    currentStatus === "PAID" ||
                    currentStatus === "EXPIRED" ||
                    currentStatus === "CANCELLED" ||
                    currentStatus === "FAILED"
                ) {
                    finalStatus = currentStatus;
                } else if (requestedStatus) {
                    finalStatus = requestedStatus;
                }

                db.prepare(`
                    UPDATE transactions
                    SET
                        transaction_status = ?,
                        midtrans_transaction_id =
                            COALESCE(?, midtrans_transaction_id),
                        updated_at = CURRENT_TIMESTAMP,
                        paid_at = CASE
                            WHEN ? = 'PAID'
                            THEN COALESCE(
                                paid_at,
                                CURRENT_TIMESTAMP
                            )
                            ELSE paid_at
                        END
                    WHERE order_id = ?
                `).run(
                    finalStatus,
                    transaction_id || null,
                    finalStatus,
                    order_id
                );

                let deliveryQueued = false;

                if (finalStatus === "PAID") {
                    const result = db.prepare(`
                        INSERT OR IGNORE INTO coin_delivery_jobs (
                            order_id,
                            player_uuid,
                            player_name,
                            coin_amount,
                            delivery_status
                        )
                        VALUES (?, ?, ?, ?, 'PENDING')
                    `).run(
                        transaction.order_id,
                        transaction.player_uuid,
                        transaction.player_name,
                        transaction.coin_amount
                    );

                    deliveryQueued = result.changes > 0;
                }

                return {
                    processed: true,
                    status: finalStatus,
                    deliveryQueued
                };
            });

            const result = processEvent();

            console.log(
                "[NOVATERRA] Webhook:",
                order_id,
                "=>",
                result.status,
                "| Delivery queued:",
                result.deliveryQueued
            );

            // =================================
            // DISCORD TESTIMONIAL
            // =================================

            let testimonialSent = false;

            // Coba kirim selama transaksi PAID.
            // Bot memiliki pencegahan duplikat sendiri.
            if (
                result.status === "PAID" &&
                typeof notifyTestimonial === "function"
            ) {
                try {
                    await notifyTestimonial({
                        order_id: transaction.order_id,
                        player_name: transaction.player_name,
                        player_uuid: transaction.player_uuid,
                        coin_amount: transaction.coin_amount,
                        payment_amount: transaction.payment_amount,
                        transaction_status: "PAID",
                        midtrans_transaction_id:
                            transaction_id || null
                    });

                    testimonialSent = true;

                    console.log(
                        "[NOVATERRA] Testimonial sent:",
                        order_id
                    );
                } catch (error) {
                    console.error(
                        "[NOVATERRA] Testimonial failed:",
                        error.message
                    );
                }
            }

            return res.status(200).json({
                success: true,
                processed: result.processed,
                order_id,
                status: result.status,
                delivery_queued: result.deliveryQueued,
                testimonial_notified: testimonialSent
            });

        } catch (error) {
            console.error(
                "[NOVATERRA] Webhook error:",
                error.message
            );

            return res.status(500).json({
                success: false,
                message: "Gagal memproses notifikasi."
            });
        }
    });
};
