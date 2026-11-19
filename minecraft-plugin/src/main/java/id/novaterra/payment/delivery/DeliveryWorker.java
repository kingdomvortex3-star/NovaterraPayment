
package id.novaterra.payment.delivery;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class DeliveryWorker {

    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .create();

    private static final long MAX_COIN_AMOUNT =
            1_000_000_000_000L;

    private final NovaTerraPayment plugin;
    private final CoinManager coinManager;
    private final HttpClient httpClient;

    private final AtomicBoolean processing =
            new AtomicBoolean(false);

    private BukkitTask workerTask;

    private String baseUrl;
    private String serverId;
    private String secret;

    private int intervalSeconds;
    private int readTimeout;

    public DeliveryWorker(
            NovaTerraPayment plugin,
            CoinManager coinManager
    ) {
        this.plugin = plugin;
        this.coinManager = coinManager;

        this.readTimeout = Math.max(
                1000,
                plugin.getConfig().getInt(
                        "api.read-timeout",
                        15000
                )
        );

        int connectTimeout = Math.max(
                1000,
                plugin.getConfig().getInt(
                        "api.connection-timeout",
                        10000
                )
        );

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(
                        java.time.Duration.ofMillis(connectTimeout)
                )
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public synchronized void start() {
        if (workerTask != null) {
            return;
        }

        if (!plugin.getConfig().getBoolean(
                "api.enabled",
                false
        )) {
            plugin.getLogger().info(
                    "Coin delivery worker disabled by configuration."
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

        baseUrl = plugin.getConfig()
                .getString("api.url", "")
                .trim()
                .replaceAll("/+$", "");

        serverId = plugin.getConfig()
                .getString("api.server-id", "")
                .trim();

        secret = plugin.getConfig()
                .getString("api.secret", "");

        intervalSeconds = Math.max(
                5,
                plugin.getConfig().getInt(
                        "api.synchronization.interval-seconds",
                        10
                )
        );

        if (!baseUrl.startsWith("https://")) {
            plugin.getLogger().severe(
                    "Delivery worker requires HTTPS."
            );
            return;
        }

        if (!validServerId(serverId)) {
            plugin.getLogger().severe(
                    "Invalid api.server-id."
            );
            return;
        }

        if (secret == null
                || secret.isBlank()
                || secret.equals("CHANGE_THIS_SECRET")) {
            plugin.getLogger().severe(
                    "API secret is not configured."
            );
            return;
        }

        long period = intervalSeconds * 20L;

        workerTask = Bukkit.getScheduler()
                .runTaskTimerAsynchronously(
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

    private boolean validServerId(String value) {
        return value != null
                && value.matches("[a-zA-Z0-9_-]{3,64}");
    }

    private void processQueue() {
        if (!processing.compareAndSet(false, true)) {
            return;
        }

        try {
            JsonObject requestBody = new JsonObject();

            requestBody.addProperty("server_id", serverId);

            /*
             * Process one job at a time.
             * This avoids holding a large batch while its lease expires.
             */
            requestBody.addProperty("limit", 1);

            JsonObject response = post(
                    "/api/delivery/claim",
                    requestBody
            );

            if (!isSuccessful(response)) {
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

        } finally {
            processing.set(false);
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

            if (coinAmount <= 0
                    || coinAmount > MAX_COIN_AMOUNT) {
                throw new IllegalArgumentException(
                        "Coin amount is outside the allowed limit."
                );
            }

            /*
             * Extend the lease before touching the local database.
             */
            renewLease(orderId);

            /*
             * Account creation and credit are performed locally.
             * The order ID is the unique idempotency reference.
             */
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

            /*
             * APPLIED means the credit was committed.
             * DUPLICATE means the same order was already committed.
             */
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
                reportFailure(
                        orderId,
                        exception.getMessage()
                );
            }
        }
    }

    private void renewLease(String orderId)
            throws Exception {

        JsonObject body = new JsonObject();

        body.addProperty("server_id", serverId);
        body.addProperty("order_id", orderId);

        JsonObject response = post(
                "/api/delivery/renew",
                body
        );

        if (!isSuccessful(response)) {
            throw new IllegalStateException(
                    "Could not renew delivery lease: "
                            + response
            );
        }

        String status = response.has("status")
                ? response.get("status").getAsString()
                : "";

        if (!"RENEWED".equals(status)) {
            throw new IllegalStateException(
                    "Unexpected lease status: " + status
            );
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

        if (!isSuccessful(response)) {
            throw new IllegalStateException(
                    "API did not confirm Coin delivery: "
                            + response
            );
        }

        String status = response.has("status")
                ? response.get("status").getAsString()
                : "";

        if (!"DELIVERED".equals(status)
                && !"ALREADY_DELIVERED".equals(status)) {
            throw new IllegalStateException(
                    "Unexpected delivery completion status: "
                            + status
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

            String safeError = error == null
                    ? "Unknown delivery error."
                    : error;

            if (safeError.length() > 500) {
                safeError = safeError.substring(0, 500);
            }

            body.addProperty("error", safeError);

            JsonObject response = post(
                    "/api/delivery/fail",
                    body
            );

            if (!isSuccessful(response)) {
                plugin.getLogger().warning(
                        "API could not record delivery failure for "
                                + orderId
                );
            }

        } catch (Exception exception) {
            plugin.getLogger().log(
                    Level.WARNING,
                    "Could not report delivery failure for "
                            + orderId,
                    exception
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
                .timeout(
                        java.time.Duration.ofMillis(readTimeout)
                )
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

        JsonElement parsed;

        try {
            parsed = JsonParser.parseString(response.body());
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "API returned invalid JSON. HTTP "
                            + response.statusCode(),
                    exception
            );
        }

        if (!parsed.isJsonObject()) {
            throw new IllegalStateException(
                    "API response is not a JSON object."
            );
        }

        JsonObject result = parsed.getAsJsonObject();

        if (response.statusCode() < 200
                || response.statusCode() >= 300) {
            throw new IllegalStateException(
                    "API HTTP status "
                            + response.statusCode()
                            + ": "
                            + result
            );
        }

        return result;
    }

    private boolean isSuccessful(JsonObject response) {
        return response.has("success")
                && response.get("success").isJsonPrimitive()
                && response.get("success").getAsBoolean();
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
                || object.get(key).isJsonNull()
                || !object.get(key).isJsonPrimitive()) {
            throw new IllegalArgumentException(
                    "Missing or invalid field: " + key
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
                || object.get(key).isJsonNull()
                || !object.get(key).isJsonPrimitive()) {
            throw new IllegalArgumentException(
                    "Missing or invalid numeric field: " + key
            );
        }

        try {
            return object.get(key).getAsLong();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Invalid numeric field: " + key,
                    exception
            );
        }
    }
}
