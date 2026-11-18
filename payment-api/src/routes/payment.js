
const crypto = require("crypto");

const COIN_PACKAGES = Object.freeze({
    starter: {
        name: "Starter",
        price: 5000,
        coins: 500
    },
    basic: {
        name: "Basic",
        price: 10000,
        coins: 1100
    },
    premium: {
        name: "Premium",
        price: 20000,
        coins: 2300
    },
    ultimate: {
        name: "Ultimate",
        price: 50000,
        coins: 6000
    }
});

const UUID_REGEX =
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

module.exports = function registerPaymentRoutes(app, db) {
    const snap = app.locals.midtrans;

    if (!snap) {
        throw new Error("Midtrans Snap belum diinisialisasi.");
    }

    app.post("/api/payment/create", async (req, res) => {
        let orderId = null;

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

            const packageId =
                typeof body.package_id === "string"
                    ? body.package_id.trim().toLowerCase()
                    : "";

            // Validasi UUID
            if (!UUID_REGEX.test(playerUuid)) {
                return res.status(400).json({
                    success: false,
                    message: "UUID pemain tidak valid."
                });
            }

            // Validasi nama pemain
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

            // Validasi paket
            const selectedPackage = COIN_PACKAGES[packageId];

            if (!selectedPackage) {
                return res.status(400).json({
                    success: false,
                    message: "Paket Coin tidak ditemukan."
                });
            }

            // Membuat Order ID unik
            orderId =
                "NOVA-" +
                Date.now() +
                "-" +
                crypto.randomBytes(5).toString("hex").toUpperCase();

            // Simpan transaksi terlebih dahulu
            db.prepare(`
                INSERT INTO transactions (
                    order_id,
                    player_uuid,
                    player_name,
                    coin_amount,
                    payment_amount,
                    payment_method,
                    transaction_status
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
            `).run(
                orderId,
                playerUuid,
                playerName,
                selectedPackage.coins,
                selectedPackage.price,
                "QRIS",
                "PENDING"
            );

            // Parameter Snap Midtrans
            const parameter = {
                transaction_details: {
                    order_id: orderId,
                    gross_amount: selectedPackage.price
                },

                item_details: [
                    {
                        id: packageId,
                        price: selectedPackage.price,
                        quantity: 1,
                        name: selectedPackage.name + " Coin"
                    }
                ],

                customer_details: {
                    first_name: playerName
                },

                // QRIS khusus melalui Other QRIS.
                // Pastikan Other QRIS sudah aktif di dashboard Midtrans.
                enabled_payments: ["other_qris"]
            };

            // Membuat transaksi Midtrans
            let payment;

            try {
                payment = await snap.createTransaction(parameter);
            } catch (gatewayError) {
                db.prepare(`
                    UPDATE transactions
                    SET transaction_status = 'FAILED',
                        updated_at = CURRENT_TIMESTAMP
                    WHERE order_id = ?
                `).run(orderId);

                throw gatewayError;
            }

            if (!payment || !payment.redirect_url || !payment.token) {
                db.prepare(`
                    UPDATE transactions
                    SET transaction_status = 'FAILED',
                        updated_at = CURRENT_TIMESTAMP
                    WHERE order_id = ?
                `).run(orderId);

                throw new Error("Respons invoice Midtrans tidak lengkap.");
            }

            console.log(
                "[NOVATERRA] Invoice created:",
                orderId
            );

            return res.status(201).json({
                success: true,
                message: "Invoice berhasil dibuat.",

                order_id: orderId,

                player: {
                    uuid: playerUuid,
                    name: playerName
                },

                package: {
                    id: packageId,
                    name: selectedPackage.name,
                    coins: selectedPackage.coins
                },

                amount: selectedPackage.price,
                currency: "IDR",
                payment_method: "QRIS",

                payment_url: payment.redirect_url,
                token: payment.token,

                status: "PENDING"
            });

        } catch (error) {
            console.error(
                "[NOVATERRA] Payment creation error:",
                error.message
            );

            return res.status(500).json({
                success: false,
                message: "Gagal membuat pembayaran."
            });
        }
    });
};
