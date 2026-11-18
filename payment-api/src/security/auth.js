
const crypto = require("crypto");

const MAX_TIME_DIFFERENCE = 5 * 60 * 1000;

function verifySignature(req, res, next) {
    const secret = process.env.API_SECRET;
    const timestamp = req.headers["x-nova-timestamp"];
    const signature = req.headers["x-nova-signature"];

    if (!secret) {
        return res.status(503).json({
            success: false,
            message: "API_SECRET belum dikonfigurasi."
        });
    }

    if (!timestamp || !signature) {
        return res.status(401).json({
            success: false,
            message: "Authentication headers tidak lengkap."
        });
    }

    const timestampNumber = Number(timestamp);

    if (
        !Number.isSafeInteger(timestampNumber) ||
        Math.abs(Date.now() - timestampNumber) > MAX_TIME_DIFFERENCE
    ) {
        return res.status(401).json({
            success: false,
            message: "Timestamp tidak valid."
        });
    }

    const payload =
        `${timestamp}.${JSON.stringify(req.body || {})}`;

    const expected = crypto
        .createHmac("sha256", secret)
        .update(payload)
        .digest();

    let received;

    try {
        if (!/^[a-f0-9]{64}$/i.test(signature)) {
            throw new Error("Invalid signature format");
        }

        received = Buffer.from(signature, "hex");
    } catch {
        return res.status(401).json({
            success: false,
            message: "Signature tidak valid."
        });
    }

    if (!crypto.timingSafeEqual(received, expected)) {
        return res.status(401).json({
            success: false,
            message: "Signature tidak valid."
        });
    }

    next();
}

module.exports = verifySignature;
