
package id.novaterra.payment.delivery;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import id.novaterra.payment.NovaTerraPayment;
import id.novaterra.payment.economy.CoinManager;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class DeliveryWorker {

    private static final Gson GSON = new Gson();

    private final NovaTerraPayment plugin;
    private final CoinManager coinManager;

    private final HttpClient httpClient;

    private BukkitTask workerTask;

    private String baseUrl;
    private String serverId;
    private String secret;

    private int batchSize;
    private int intervalSeconds;

    public DeliveryWorker(
            NovaTerraPayment plugin,
            CoinManager coinManager
    ) {
        this.plugin = plugin;
        this.coinManager = coinManager;

        int timeout = Math.max(
                1000,
                plugin.getConfig().getInt(
                        "api.read-timeout",
                        15000
                )
        );

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(
                        java.time.Duration.ofMillis(timeout)
                )
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public synchronized void start() {
        if (workerTask != null) {
            return;
        }

        if (!plugin.getConfig().getBoolean("api.enabled", false)) {
            plugin.getLogger().info(
                    "Coin delivery worker disabled by configuration."
            );
            return;
        }

        baseUrl = plugin.getConfig()
                .getString("api.url", "")
                .trim()
                .replaceAll("/+$", "");

        serverId = plugin.getConfig()
                .getString("api.server-id", "").trim();

        secret = plugin.getConfig()
                .getString("api.secret", "");

        batchSize = Math.max(
                1,
                Math.min(
                        20,
                        plugin.getConfig().getInt(
                                "api.synchronization.batch-size",
                                5
                        )
                )
        );

        intervalSeconds = Math.max(
                5,
                plugin.getConfig().getInt(
                        "api.synchronization.interval-seconds",
                        10
                )
        );

        if (!baseUrl.startsWith("https://")) {
            plugin.getLogger().severe(
                    "Delivery worker requires HTTPS. Worker was not started."
            );
            return;
        }

        if (serverId.isBlank()
                || !serverId.matches("[a-zA-Z0-9_-]{3,64}")) {
            plugin.getLogger().severe(
                    "Invalid api.server-id. Worker was not started."
            );
            return;
        }

        if (secret == null
                || secret.isBlank()
                || secret.equals("CHANGE_THIS_SECRET")) {
            plugin.getLogger().severe(
                    "API secret is not configured. Worker was not started."
            );
            return;
        }

        if (!plugin.getConfig().getBoolean(
                "api.synchronization.enabled",
                false
        )) {
            plugin.getLogger().info(
                    "API synchronization is disabled."
            );
            return;
        }

        long period = intervalSeconds * 20L;

        workerTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin,
                this::processQueue,
                40L,
                period
        );

        plugin.getLogger().info(
                "Coin delivery worker started."
        );
    }

    public synchronized void stop() {
        if (workerTask != null) {
            workerTask.cancel();
            workerTask = null;
        }
    }

    private void processQueue() {
        try {
            JsonObject requestBody = new JsonObject();

            requestBody.addProperty("server_id", serverId);
            requestBody.addProperty("limit", batchSize);

            JsonObject response = post(
                    "/api/delivery/claim",
                    requestBody
            );

            if (!response.has("success")
                    || !response.get("success").getAsBoolean()) {
                throw new IllegalStateException(
                        "API rejected delivery claim."
                );
            }

            JsonArray jobs = response.has("jobs")
                    && response.get("jobs").isJsonArray()
                    ? response.getAsJsonArray("jobs")
                    : new JsonArray();

            if (jobs.isEmpty()) {
                return;
            }

            for (JsonElement element : jobs) {
                if (!element.isJsonObject()) {
                    continue;
                }

                processJob(element.getAsJsonObject());
            }

        } catch (Exception exception) {
            plugin.getLogger().log(
                    Level.WARNING,
                    "Coin delivery queue error: "
                            + exception.getMessage()
            );
        }
    }

    private void processJob(JsonObject job) {
        String orderId = null;

        try {
            orderId = requiredString(job, "order_id");

            String playerUUID = requiredString(
                    job,
                    "player_uuid"
            );

            String playerName = requiredString(
                    job,
                    "player_name"
            );

            long coinAmount = requiredLong(
                    job,
                    "coin_amount"
            );

            UUID uuid = UUID.fromString(playerUUID);

            if (coinAmount <= 0) {
                throw new IllegalArgumentException(
                        "Invalid Coin amount."
                );
            }

            if (coinAmount > 1_000_000_000_000L) {
                throw new IllegalArgumentException(
                        "Coin amount exceeds transaction limit."
                );
            }

            coinManager.createAccount(uuid, playerName);

            CoinManager.CreditResult result =
                    coinManager.addCoinsResult(
                            uuid,
                            playerName,
                            coinAmount,
                            orderId,
                            "NOVATERRA PAYMENT - " + orderId
                    );

            if (result != CoinManager.CreditResult.APPLIED
                    && result != CoinManager.CreditResult.DUPLICATE) {
                throw new IllegalStateException(
                        "Unexpected Coin credit result."
                );
            }

            completeDelivery(orderId);

            plugin.getLogger().info(
                    "Delivery confirmed: "
                            + orderId
                            + " ("
                            + result.name()
                            + ")"
            );

        } catch (Exception exception) {
            plugin.getLogger().log(
                    Level.SEVERE,
                    "Coin delivery failed for order "
                            + orderId,
                    exception
            );

            if (orderId != null) {
                reportFailure(orderId, exception.getMessage());
            }
        }
    }

    private void completeDelivery(String orderId)
            throws Exception {

        JsonObject body = new JsonObject();

        body.addProperty("server_id", serverId);
        body.addProperty("order_id", orderId);

        JsonObject response = post(
                "/api/delivery/complete",
                body
        );

        if (!response.has("success")
                || !response.get("success").getAsBoolean()) {
            throw new IllegalStateException(
                    "API did not confirm Coin delivery."
            );
        }
    }

    private void reportFailure(
            String orderId,
            String error
    ) {
        try {
            JsonObject body = new JsonObject();

            body.addProperty("server_id", serverId);
            body.addProperty("order_id", orderId);

            body.addProperty(
                    "error",
                    error == null
                            ? "Unknown delivery error."
                            : error.substring(
                                    0,
                                    Math.min(error.length(), 500)
                            )
            );

            post("/api/delivery/fail", body);

        } catch (Exception exception) {
            plugin.getLogger().warning(
                    "Could not report delivery failure for "
                            + orderId
                            + ": "
                            + exception.getMessage()
            );
        }
    }

    private JsonObject post(
            String endpoint,
            JsonObject body
    ) throws Exception {

        String requestBody = GSON.toJson(body);

        String timestamp = String.valueOf(
                System.currentTimeMillis()
        );

        String signature = generateSignature(
                timestamp + "." + requestBody
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + endpoint))
                .timeout(java.time.Duration.ofMillis(
                        plugin.getConfig().getInt(
                                "api.read-timeout",
                                15000
                        )
                ))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("x-nova-timestamp", timestamp)
                .header("x-nova-signature", signature)
                .POST(HttpRequest.BodyPublishers.ofString(
                        requestBody,
                        StandardCharsets.UTF_8
                ))
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(
                        StandardCharsets.UTF_8
                )
        );

        if (response.statusCode() < 200
                || response.statusCode() >= 300) {
            throw new IllegalStateException(
                    "API HTTP status: " + response.statusCode()
            );
        }

        JsonElement parsed = JsonParser.parseString(
                response.body()
        );

        if (!parsed.isJsonObject()) {
            throw new IllegalStateException(
                    "Invalid API JSON response."
            );
        }

        return parsed.getAsJsonObject();
    }

    private String generateSignature(String payload)
            throws Exception {

        Mac mac = Mac.getInstance("HmacSHA256");

        SecretKeySpec key = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );

        mac.init(key);

        byte[] hash = mac.doFinal(
                payload.getBytes(StandardCharsets.UTF_8)
        );

        return HexFormat.of().formatHex(hash);
    }

    private String requiredString(
            JsonObject object,
            String key
    ) {
        if (!object.has(key)
                || object.get(key).isJsonNull()) {
            throw new IllegalArgumentException(
                    "Missing field: " + key
            );
        }

        String value = object.get(key).getAsString();

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    "Empty field: " + key
            );
        }

        return value;
    }

    private long requiredLong(
            JsonObject object,
            String key
    ) {
        if (!object.has(key)
                || object.get(key).isJsonNull()) {
            throw new IllegalArgumentException(
                    "Missing numeric field: " + key
            );
        }

        return object.get(key).getAsLong();
    }
}
