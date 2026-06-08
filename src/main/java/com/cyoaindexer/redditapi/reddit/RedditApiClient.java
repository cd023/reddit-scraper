package com.cyoaindexer.redditapi.reddit;

import com.cyoaindexer.redditapi.config.AppConfig;
import com.cyoaindexer.redditapi.model.RedditPost;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RedditApiClient {
    private static final String API_BASE = "https://oauth.reddit.com";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final AppConfig config;
    private final RedditAuthClient authClient;
    private final HttpClient httpClient;
    private final RedditPostMapper mapper;

    public RedditApiClient(AppConfig config, RedditAuthClient authClient) {
        this(config, authClient, HttpClient.newHttpClient(), new RedditPostMapper());
    }

    public RedditApiClient(AppConfig config, RedditAuthClient authClient, HttpClient httpClient, RedditPostMapper mapper) {
        this.config = config;
        this.authClient = authClient;
        this.httpClient = httpClient;
        this.mapper = mapper;
    }

    public RedditListingPage fetchNew(String subreddit, String after, int limit) {
        StringBuilder path = new StringBuilder()
                .append("/r/")
                .append(urlEncode(subreddit))
                .append("/new?raw_json=1&limit=")
                .append(Math.min(Math.max(limit, 1), 100));
        if (after != null && !after.isBlank()) {
            path.append("&after=").append(urlEncode(after));
        }

        JsonNode root = getJson(path.toString());
        JsonNode listing = root.path("data");
        List<RedditPost> posts = new ArrayList<>();
        for (JsonNode child : listing.path("children")) {
            posts.add(mapper.fromListingChild(child));
        }
        return new RedditListingPage(posts, listing.path("after").asText(""));
    }

    public List<RedditPost> lookupByFullnames(List<String> fullnames) {
        if (fullnames == null || fullnames.isEmpty()) {
            return List.of();
        }
        String ids = fullnames.stream()
                .map(RedditApiClient::toFullname)
                .collect(Collectors.joining(","));
        JsonNode root = getJson("/api/info?raw_json=1&id=" + urlEncode(ids));
        List<RedditPost> posts = new ArrayList<>();
        for (JsonNode child : root.path("data").path("children")) {
            posts.add(mapper.fromListingChild(child));
        }
        return posts;
    }

    public static String toFullname(String id) {
        if (id == null || id.isBlank()) {
            return "";
        }
        String trimmed = id.trim();
        return trimmed.startsWith("t3_") ? trimmed : "t3_" + trimmed;
    }

    private JsonNode getJson(String pathAndQuery) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(API_BASE + pathAndQuery))
                .header("Authorization", "Bearer " + authClient.accessToken())
                .header("User-Agent", config.redditUserAgent())
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new RedditApiException(response.statusCode(), "Reddit API request failed: " + response.body());
            }
            return JSON.readTree(response.body());
        } catch (IOException exception) {
            throw new RedditApiException(0, "Reddit API request failed: " + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RedditApiException(0, "Reddit API request was interrupted.");
        }
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
