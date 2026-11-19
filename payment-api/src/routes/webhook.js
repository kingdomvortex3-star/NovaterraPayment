
const crypto = require("crypto");

function safeCompareHex(received, expected) {
    if (
        typeof received !== "string" ||
        !/^[a-f0-9]{128}$/i.test(received)
    ) {
        return false;
    }

    const first = Buffer.from(received, "hex");
    const second = Buffer.from(expected, "hex");

    return (
        first.length === second.length &&
        crypto.timingSafeEqual(first, second)
    );
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

function registerWebhook(app, db) {
    const serverKey = process.env.MIDTRANS_SERVER_KEY;

    if (!serverKey) {
        throw new Error(
            "MIDTRANS_SERVER_KEY belum dikonfigurasi."
        );
    }

    app.post("/webhook/midtrans", (req, res) => {
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
                order_id.length < 1 ||
                order_id.length > 64 ||
                typeof status_code !== "string" ||
                typeof gross_amount !== "string" ||
                typeof signature_key !== "string" ||
                typeof transaction_status !== "string"
            ) {
                return res.status(400).json({
                    success: false,
                    message: "Notifikasi tidak lengkap."
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

            if (!safeCompareHex(
                signature_key,
                expectedSignature
            )) {
                return res.status(403).json({
                    success: false,
                    message: "Signature tidak valid."
                });
            }

            const requestedStatus = getPaymentStatus(
                transaction_status,
                fraud_status
            );

            if (!requestedStatus) {
                return res.status(200).json({
                    success: true,
                    ignored: true,
                    message: "Status pembayaran tidak memerlukan tindakan."
                });
            }

            const eventId = crypto
                .createHash("sha256")
                .update([
                    order_id,
                    transaction_status,
                    status_code,
                    gross_amount,
                    transaction_id || "",
                    fraud_status || ""
                ].join(":"))
                .digest("hex");

            const processEvent = db.transaction(() => {
                const transaction = db.prepare(`
                    SELECT *
                    FROM transactions
                    WHERE order_id = ?
                `).get(order_id);

                if (!transaction) {
                    return {
                        type: "NOT_FOUND"
                    };
                }

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
                    return {
                        type: "INVALID_AMOUNT"
                    };
                }

                const existingEvent = db.prepare(`
                    SELECT id
                    FROM processed_events
                    WHERE event_id = ?
                `).get(eventId);

                if (existingEvent) {
                    return {
                        type: "DUPLICATE",
                        status: transaction.transaction_status
                    };
                }

                db.prepare(`
                    INSERT INTO processed_events (
                        event_id,
                        order_id
                    )
                    VALUES (?, ?)
                `).run(eventId, order_id);

                const currentStatus =
                    transaction.transaction_status;

                let finalStatus = currentStatus;

                /*
                 * REFUNDED is terminal.
                 * A late PAID notification must never restore it.
                 */
                if (currentStatus === "REFUNDED") {
                    finalStatus = "REFUNDED";

                } else if (requestedStatus === "REFUNDED") {
                    finalStatus = "REFUNDED";

                } else if (
                    currentStatus === "EXPIRED" ||
                    currentStatus === "CANCELLED" ||
                    currentStatus === "FAILED"
                ) {
                    finalStatus = currentStatus;

                } else if (currentStatus === "PAID") {
                    finalStatus = "PAID";

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
                let refundAction = "NONE";

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

                if (finalStatus === "REFUNDED") {
                    const job = db.prepare(`
                        SELECT delivery_status
                        FROM coin_delivery_jobs
                        WHERE order_id = ?
                    `).get(order_id);

                    if (job) {
                        const mayHaveCredited =
                            job.delivery_status === "DELIVERED" ||
                            job.delivery_status === "PROCESSING" ||
                            job.delivery_status === "REFUND_REQUIRED";

                        const nextDeliveryStatus =
                            mayHaveCredited
                                ? "REFUND_REQUIRED"
                                : "CANCELLED";

                        db.prepare(`
                            UPDATE coin_delivery_jobs
                            SET
                                delivery_status = ?,
                                claimed_by = NULL,
                                lease_expires_at = NULL,
                                last_error = ?,
                                updated_at = CURRENT_TIMESTAMP
                            WHERE order_id = ?
                        `).run(
                            nextDeliveryStatus,
                            mayHaveCredited
                                ? "Payment refunded. Coin reversal requires reconciliation."
                                : "Payment refunded before Coin delivery.",
                            order_id
                        );

                        refundAction = nextDeliveryStatus;
                    }
                }

                return {
                    type: "PROCESSED",
                    status: finalStatus,
                    deliveryQueued,
                    refundAction
                };
            });

            const result = processEvent();

            if (result.type === "NOT_FOUND") {
                return res.status(404).json({
                    success: false,
                    message: "Transaksi tidak ditemukan."
                });
            }

            if (result.type === "INVALID_AMOUNT") {
                return res.status(400).json({
                    success: false,
                    message: "Nominal pembayaran tidak cocok."
                });
            }

            if (result.type === "DUPLICATE") {
                return res.status(200).json({
                    success: true,
                    processed: false,
                    duplicate: true,
                    order_id,
                    status: result.status
                });
            }

            console.log(
                "[NOVATERRA] Webhook:",
                order_id,
                "=>",
                result.status,
                "| Delivery queued:",
                result.deliveryQueued,
                "| Refund action:",
                result.refundAction
            );

            return res.status(200).json({
                success: true,
                processed: true,
                order_id,
                status: result.status,
                delivery_queued: result.deliveryQueued,
                refund_action: result.refundAction
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
}

module.exports = registerWebhook;
