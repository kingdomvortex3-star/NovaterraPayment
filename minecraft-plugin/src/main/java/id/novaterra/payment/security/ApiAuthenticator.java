
package id.novaterra.payment.security;

import id.novaterra.payment.NovaTerraPayment;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class ApiAuthenticator {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private static final long MAX_TIMESTAMP_DIFFERENCE = 300_000L;

    private final NovaTerraPayment plugin;

    public ApiAuthenticator(NovaTerraPayment plugin) {
        this.plugin = plugin;
    }

    public boolean validate(
            String timestamp,
            String signature,
            String requestBody
    ) {

        if (timestamp == null
                || signature == null
                || requestBody == null) {
            return false;
        }

        String secret = plugin.getConfig()
                .getString("api.secret", "");

        if (secret == null
                || secret.isBlank()
                || secret.equals("CHANGE_THIS_SECRET")) {
            return false;
        }

        if (!isValidTimestamp(timestamp)) {
            return false;
        }

        if (!signature.matches("(?i)[0-9a-f]{64}")) {
            return false;
        }

        try {
            String expected = generateSignature(
                    secret,
                    timestamp + "." + requestBody
            );

            byte[] expectedBytes = HexFormat.of()
                    .parseHex(expected);

            byte[] receivedBytes = HexFormat.of()
                    .parseHex(signature);

            return MessageDigest.isEqual(
                    expectedBytes,
                    receivedBytes
            );

        } catch (Exception exception) {
            plugin.getLogger().warning(
                    "API authentication failed: "
                            + exception.getMessage()
            );

            return false;
        }
    }

    private boolean isValidTimestamp(String timestamp) {

        try {
            long requestTime = Long.parseLong(timestamp);

            long currentTime = System.currentTimeMillis();

            long difference = Math.abs(
                    currentTime - requestTime
            );

            return difference <= MAX_TIMESTAMP_DIFFERENCE;

        } catch (NumberFormatException exception) {
            return false;
        }
    }

    public String generateSignature(
            String secret,
            String message
    ) throws Exception {

        Mac mac = Mac.getInstance(HMAC_ALGORITHM);

        SecretKeySpec key = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                HMAC_ALGORITHM
        );

        mac.init(key);

        byte[] hash = mac.doFinal(
                message.getBytes(StandardCharsets.UTF_8)
        );

        return HexFormat.of().formatHex(hash);
    }
}
