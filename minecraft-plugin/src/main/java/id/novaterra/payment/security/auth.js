
const crypto = require("crypto");

const MAX_TIME_DIFFERENCE = 5 * 60 * 1000;

function verifySignature(req, res, next) {
    const secret = process.env.API_SECRET;
    const timestamp = req.headers["x-nova-timestamp"];
    const signature = req.headers["x-nova-signature"];

    if (!secret) {
        console.error("[NOVATERRA] API_SECRET belum dikonfigurasi.");

        return res.status(503).json({
            success: false,
            message: "API authentication belum dikonfigurasi."
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
            message: "Timestamp tidak valid atau sudah kedaluwarsa."
        });
    }

    const body = JSON.stringify(req.body || {});
    const payload = `${timestamp}.${body}`;

    const expectedSignature = crypto
        .createHmac("sha256", secret)
        .update(payload)
        .digest("hex");

    const received = Buffer.from(signature, "hex");
    const expected = Buffer.from(expectedSignature, "hex");

    if (
        received.length !== expected.length ||
        !crypto.timingSafeEqual(received, expected)
    ) {
        return res.status(401).json({
            success: false,
            message: "Signature tidak valid."
        });
    }

    next();
}

module.exports = verifySignature;
