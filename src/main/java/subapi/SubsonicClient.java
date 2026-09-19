package subapi;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

final class SubsonicClient {
    private final SubsonicConfig config;
    private final Consumer<String> debugLogger;
    private final Random random = new Random();

    SubsonicClient(SubsonicConfig config, Consumer<String> debugLogger) {
        this.config = config;
        this.debugLogger = debugLogger;
    }

    void ping() throws IOException {
        request("ping", java.util.Collections.<String, String>emptyMap());
    }

    JsonObject request(String method, Map<String, String> params) throws IOException {
        if ("password".equalsIgnoreCase(config.authentication)) {
            return requestWithPassword(method, params);
        }
        return requestWithToken(method, params);
    }

    private JsonObject requestWithToken(String method, Map<String, String> params) throws IOException {
        String salt = Long.toHexString(random.nextLong());
        return request(method, params, "&s=" + salt + "&t=" + md5(config.password + salt), "md5");
    }

    private JsonObject requestWithPassword(String method, Map<String, String> params) throws IOException {
        return request(method, params, "&p=" + encode(config.password), "password");
    }

    private JsonObject request(String method, Map<String, String> params,
                               String authentication, String authenticationMode) throws IOException {
        StringBuilder query = new StringBuilder();
        query.append("u=").append(encode(config.username));
        query.append(authentication);
        query.append("&v=").append(encode(config.apiVersion));
        query.append("&c=").append(encode(config.clientName));
        query.append("&f=json");
        for (Map.Entry<String, String> entry : params.entrySet()) {
            query.append('&').append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
        }

        String requestUrl = endpointUrl(method) + "?" + query;
        debug("请求 " + method + " [" + authenticationMode + "]");
        HttpURLConnection connection = (HttpURLConnection) new java.net.URL(requestUrl).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("Accept", "application/json");
        try {
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            if (stream == null) {
                throw new IOException("Subsonic returned HTTP " + status);
            }
            String body = read(stream);
            debug("响应 " + method + " HTTP " + status);
            if (status < 200 || status >= 300) {
                throw new IOException("Subsonic returned HTTP " + status + ": " + body);
            }
            JsonObject response = new JsonParser().parse(body).getAsJsonObject();
            JsonObject root = response.getAsJsonObject("subsonic-response");
            if (root == null || !root.has("status") || !"ok".equals(root.get("status").getAsString())) {
                String message = root != null && root.has("error")
                        ? root.getAsJsonObject("error").get("message").getAsString()
                        : "unknown Subsonic error";
                throw new IOException("Subsonic error: " + message);
            }
            return root;
        } finally {
            connection.disconnect();
        }
    }

    private void debug(String message) {
        if (config.debug && debugLogger != null) {
            debugLogger.accept(message);
        }
    }

    String streamUrl(String id) {
        String salt = Long.toHexString(random.nextLong());
        String authentication;
        if ("password".equalsIgnoreCase(config.authentication)) {
            authentication = "&p=" + encode(config.password);
        } else {
            authentication = "&s=" + salt + "&t=" + md5(config.password + salt);
        }
        return endpointUrl("stream") + "?u=" + encode(config.username)
                + authentication
                + "&v=" + encode(config.apiVersion) + "&c=" + encode(config.clientName)
                + "&id=" + encode(id);
    }

    private String endpointUrl(String method) {
        return config.serverUrl + "/" + method + ".view";
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to encode Subsonic parameter", e);
        }
    }

    private static String md5(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(
                    value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(32);
            for (byte item : digest) {
                result.append(String.format("%02x", item & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 is unavailable", e);
        }
    }

    private static String read(InputStream stream) throws IOException {
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    static List<JsonObject> array(JsonObject object, String name) {
        List<JsonObject> result = new ArrayList<>();
        JsonElement element = object.get(name);
        if (element == null) {
            return result;
        }
        if (element.isJsonObject()) {
            result.add(element.getAsJsonObject());
            return result;
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement item : array) {
                if (item.isJsonObject()) {
                    result.add(item.getAsJsonObject());
                }
            }
        }
        return result;
    }
}
