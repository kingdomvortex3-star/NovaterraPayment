
package id.novaterra.payment.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class SignatureValidator {

    private static final String ALGORITHM = "HmacSHA256";

    private SignatureValidator() {
    }

    public static boolean validateHmacSha256(
            String payload,
            String receivedSignature,
            String secret
    ) {

        if (payload == null
                || receivedSignature == null
                || secret == null
                || secret.isBlank()) {
            return false;
        }

        if (!receivedSignature.matches("(?i)[0-9a-f]{64}")) {
            return false;
        }

        try {
            String expectedSignature = generateHmacSha256(
                    payload,
                    secret
            );

            byte[] expected = HexFormat.of()
                    .parseHex(expectedSignature);

            byte[] received = HexFormat.of()
                    .parseHex(receivedSignature);

            return MessageDigest.isEqual(expected, received);

        } catch (Exception exception) {
            return false;
        }
    }

    public static String generateHmacSha256(
            String payload,
            String secret
    ) throws Exception {

        Mac mac = Mac.getInstance(ALGORITHM);

        SecretKeySpec key = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                ALGORITHM
        );

        mac.init(key);

        byte[] result = mac.doFinal(
                payload.getBytes(StandardCharsets.UTF_8)
        );

        return HexFormat.of().formatHex(result);
    }
}
