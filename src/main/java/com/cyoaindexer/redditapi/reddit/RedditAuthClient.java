package com.cyoaindexer.redditapi.reddit;

import com.cyoaindexer.redditapi.config.AppConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

public class RedditAuthClient {
    private static final URI TOKEN_URI = URI.create("https://www.reddit.com/api/v1/access_token");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final AppConfig config;
    private final HttpClient httpClient;
    private AccessToken cachedToken;

    public RedditAuthClient(AppConfig config) {
        this(config, HttpClient.newHttpClient());
    }

    public RedditAuthClient(AppConfig config, HttpClient httpClient) {
        this.config = config;
        this.httpClient = httpClient;
    }

    public synchronized String accessToken() {
        if (cachedToken != null && cachedToken.expiresAt().isAfter(Instant.now().plusSeconds(30))) {
            return cachedToken.value();
        }

        String basic = Base64.getEncoder().encodeToString(
                (config.redditClientId() + ":" + config.redditClientSecret()).getBytes(StandardCharsets.UTF_8)
        );
        HttpRequest request = HttpRequest.newBuilder(TOKEN_URI)
                .header("Authorization", "Basic " + basic)
                .header("User-Agent", config.redditUserAgent())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials"))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new RedditApiException(response.statusCode(), "Reddit auth failed: " + response.body());
            }
            JsonNode body = JSON.readTree(response.body());
            String token = body.path("access_token").asText("");
            long expiresIn = body.path("expires_in").asLong(3600);
            if (token.isBlank()) {
                throw new RedditApiException(response.statusCode(), "Reddit auth response did not include an access token.");
            }
            cachedToken = new AccessToken(token, Instant.now().plusSeconds(Math.max(60, expiresIn - 30)));
            return cachedToken.value();
        } catch (IOException exception) {
            throw new RedditApiException(0, "Reddit auth request failed: " + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RedditApiException(0, "Reddit auth request was interrupted.");
        }
    }

    private record AccessToken(String value, Instant expiresAt) {
    }
}
