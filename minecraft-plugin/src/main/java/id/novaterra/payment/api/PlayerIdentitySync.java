
package id.novaterra.payment.api;

import com.google.gson.JsonObject;
import id.novaterra.payment.NovaTerraPayment;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

public final class PlayerIdentitySync {

    private final NovaTerraPayment plugin;

    public PlayerIdentitySync(NovaTerraPayment plugin) {
        this.plugin = plugin;
    }

    public void sync(UUID uuid, String username, String platform) {
        if (!plugin.getConfig().getBoolean("api.enabled", false)
                || !plugin.getConfig().getBoolean("api.synchronization.enabled", false)) {
            return;
        }

        String baseUrl = plugin.getConfig().getString("api.url", "").trim();
        String secret = plugin.getConfig().getString("api.secret", "").trim();
        String serverId = plugin.getConfig().getString("api.server-id", "").trim();

        if (baseUrl.isEmpty() || secret.isEmpty() || serverId.isEmpty()
                || secret.equals("CHANGE_THIS_SECRET")) {
            plugin.getLogger().warning(
                    "Player sync skipped: API URL, secret, or server-id is not configured."
            );
            return;
        }

        HttpURLConnection connection = null;

        try {
            JsonObject body = new JsonObject();
            body.addProperty("player_uuid", uuid.toString());
            body.addProperty("player_name", username);
            body.addProperty("server_id", serverId);
            body.addProperty("platform", platform);

            String json = body.toString();
            String timestamp = Long.toString(System.currentTimeMillis());
            String signature = createSignature(secret, timestamp + "." + json);

            URL url = new URL(
                    baseUrl.replaceAll("/+$", "") + "/api/players/sync"
            );

            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(
                    plugin.getConfig().getInt("api.connection-timeout", 10000)
            );
            connection.setReadTimeout(
                    plugin.getConfig().getInt("api.read-timeout", 15000)
            );
            connection.setDoOutput(true);

            connection.setRequestProperty(
                    "Content-Type", "application/json; charset=UTF-8"
            );
            connection.setRequestProperty("X-Nova-Timestamp", timestamp);
            connection.setRequestProperty("X-Nova-Signature", signature);

            byte[] data = json.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(data.length);

            try (var output = connection.getOutputStream()) {
                output.write(data);
            }

            int status = connection.getResponseCode();

            if (status >= 200 && status < 300) {
                plugin.getLogger().info(
                        "Player identity synced: " + username
                                + " (" + platform + ") HTTP " + status
                );
            } else {
                plugin.getLogger().warning(
                        "Player identity sync failed for " + username
                                + ": HTTP " + status
                );
            }

        } catch (Exception exception) {
            plugin.getLogger().warning(
                    "Player identity sync failed for " + username
                            + ": " + exception.getMessage()
            );
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String createSignature(String secret, String payload)
            throws Exception {

        Mac mac = Mac.getInstance("HmacSHA256");

        mac.init(new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        ));

        byte[] hash = mac.doFinal(
                payload.getBytes(StandardCharsets.UTF_8)
        );

        return HexFormat.of().formatHex(hash);
    }
}
