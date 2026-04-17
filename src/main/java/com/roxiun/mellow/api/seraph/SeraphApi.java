package com.roxiun.mellow.api.seraph;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.roxiun.mellow.Mellow;
import com.roxiun.mellow.api.mojang.MojangApi;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class SeraphApi {

    private final MojangApi mojangApi;

    /**
     * TODO Properly implement Seraph authentication
     * To implement at a later date - Use proper authentication which allows more granular access to specific endpoints.
     * Use a placeholder now so the function is satisfied
     */
    private final String seraphApiToken = "";

    public SeraphApi(MojangApi mojangApi) {
        this.mojangApi = mojangApi;
    }

    /**
     * Fetches tags for the player should always return the standard api format unless the API is down.
     */
    public List<SeraphTag> fetchSeraphTags(@NotNull String uuidStr, @Nullable String seraphApiKey) throws IOException, IllegalArgumentException {
        try {
            UUID uuid = UUID.fromString(uuidStr);
            URL url = new URL(MessageFormat.format("https://api.seraph.si/cubelify/blacklist/{0}", uuid.toString()));
            String jsonResponse = executeGetRequest(url, seraphApiKey, seraphApiToken);
            return jsonResponse != null ? parseTags(jsonResponse) : new ArrayList<>();
        } catch (IllegalArgumentException | IOException e) {
            // For compatibility with the existing codebase, we'll rethrow the IllegalArgumentException if it's thrown
            if (e instanceof IllegalArgumentException) {
                throw e;
            } else {
                throw new IOException("Failed to fetch Seraph tags for UUID: " + uuidStr, e);
            }
        }
    }

    /**
     * Fetches Client data that can be nullable if the player is not on a client.
     * To be functional this requires a Seraph API Key with the "Admin" permission or an authorisation token with the "client" grant.
     */
    public List<SeraphTag> fetchSeraphClientData(@NotNull UUID uuid, @Nullable String seraphApiKey) {
        try {
            URL queryUrl = new URL(MessageFormat.format("https://api.seraph.si/private-access/client/{0}", uuid.toString()));
            String jsonResponse = executeGetRequest(queryUrl, seraphApiKey, seraphApiToken);
            return jsonResponse != null ? parseTags(jsonResponse) : new ArrayList<>();
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    /**
     * Fetches the Mojang name and UUID for a player.
     */
    public Pair<String, UUID> fetchSeraphMojang(@NotNull String nameOrId) {
        try {
            URL queryUrl = new URL(MessageFormat.format("https://mowojang.seraph.si/{0}", nameOrId));
            String jsonResponse = executeGetRequest(queryUrl, null, null);
            if (jsonResponse == null) return null;
            JsonObject json = new JsonParser().parse(jsonResponse).getAsJsonObject();
            return new Pair<>(json.get("name").getAsString(), UUID.fromString(json.get("uuid").getAsString()));
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Centralised request logic to a single function for easier maintainability.
     */
    private @Nullable String executeGetRequest(@NotNull URL url, @Nullable String apiKey, @Nullable String seraphApiToken) throws IOException {
        URLConnection urlObject = url.openConnection();
        if (apiKey != null && !apiKey.isEmpty()) {
            try {
                urlObject.setRequestProperty("seraph-api-key", UUID.fromString(apiKey).toString());
            } catch (IllegalArgumentException ignored) {
                throw new IllegalArgumentException("Seraph API Key is not a valid UUID.");
            }
        }
        if (seraphApiToken != null && !seraphApiToken.isEmpty()) {
            urlObject.setRequestProperty("Authorization", MessageFormat.format("Bearer {0}", seraphApiToken));
        }

        HttpURLConnection conn = (HttpURLConnection) urlObject;
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", MessageFormat.format("Mozilla/5.0 {0}/{1}", Mellow.NAME, Mellow.VERSION));
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        // Use proper Java standards for handling HTTP responses
        int responseCode = conn.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            // Use a buffered reader to parse the response more efficiently rather than dumping it all into memory
            try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                return bufferedReader.lines().collect(Collectors.joining());
            }
            // Check for 404 since the Seraph Reporting API uses this to indicate that there aren't any reports against the player specified
        } else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
            return null;
        } else {
            throw new IOException("Seraph API error: " + responseCode);
        }
    }

    private List<SeraphTag> parseTags(String response) {
        List<SeraphTag> tags = new ArrayList<>();
        try {
            JsonObject json = new JsonParser().parse(response).getAsJsonObject();
            if (!json.has("tags")) return tags;

            JsonArray tagsArray = json.getAsJsonArray("tags");
            for (JsonElement tagElement : tagsArray) {
                JsonObject tagObj = tagElement.getAsJsonObject();
                String tagName = getStringOrDefault(tagObj, "tag_name", "");

                // Filter out tags that we don't need; although an advertisement would be nice, I'll respect the decision to remove it
                if ("seraph.verified".equals(tagName) || "seraph.advertisement".equals(tagName)) {
                    continue;
                }

                tags.add(new SeraphTag(getStringOrDefault(tagObj, "icon", ""), getStringOrDefault(tagObj, "tooltip", ""), getIntOrDefault(tagObj, "color", 0), tagName, getStringOrDefault(tagObj, "text", null), getIntOrDefault(tagObj, "textColor", 0)));
            }
        } catch (Exception ignored) {
            // I couldn't find a logger instance, so I'll leave this as a placeholder
        }
        return tags;
    }

    // Basic implementation of kotlin default or null helpers
    private String getStringOrDefault(JsonObject obj, String propertyName, String fallback) {
        return (obj.has(propertyName) && !obj.get(propertyName).isJsonNull()) ? obj.get(propertyName).getAsString() : fallback;
    }

    // Set fallback to 0 by default? I wasn't sure what was preferred, so it's customisable for now
    private int getIntOrDefault(JsonObject obj, String propertyName, int fallback) {
        return (obj.has(propertyName) && !obj.get(propertyName).isJsonNull()) ? obj.get(propertyName).getAsInt() : fallback;
    }
}
