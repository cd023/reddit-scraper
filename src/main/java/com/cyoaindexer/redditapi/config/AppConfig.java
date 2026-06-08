package com.cyoaindexer.redditapi.config;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record AppConfig(
        String redditClientId,
        String redditClientSecret,
        String redditUserAgent,
        String googleSheetId,
        Optional<String> googleServiceAccountJson,
        Optional<String> googleApplicationCredentials,
        List<String> subreddits,
        LocalDate backfillStartDate,
        Duration requestDelay,
        int backfillMaxPages,
        List<String> verifyIds
) {
    public static AppConfig fromEnvironment() {
        return from(System.getenv());
    }

    public static AppConfig from(Map<String, String> env) {
        Optional<String> serviceAccountJson = optional(env, "GOOGLE_SERVICE_ACCOUNT_JSON");
        Optional<String> credentialsPath = optional(env, "GOOGLE_APPLICATION_CREDENTIALS");
        if (serviceAccountJson.isEmpty() && credentialsPath.isEmpty()) {
            throw new IllegalArgumentException("Set GOOGLE_SERVICE_ACCOUNT_JSON or GOOGLE_APPLICATION_CREDENTIALS.");
        }

        List<String> subreddits = splitCsv(required(env, "SUBREDDITS"));
        if (subreddits.isEmpty()) {
            throw new IllegalArgumentException("SUBREDDITS must contain at least one subreddit.");
        }

        return new AppConfig(
                required(env, "REDDIT_CLIENT_ID"),
                required(env, "REDDIT_CLIENT_SECRET"),
                required(env, "REDDIT_USER_AGENT"),
                required(env, "GOOGLE_SHEET_ID"),
                serviceAccountJson,
                credentialsPath,
                subreddits,
                LocalDate.parse(required(env, "BACKFILL_START_DATE")),
                Duration.ofMillis(parseLong(env.getOrDefault("REQUEST_DELAY_MILLIS", "1000"), "REQUEST_DELAY_MILLIS")),
                (int) parseLong(env.getOrDefault("BACKFILL_MAX_PAGES", "0"), "BACKFILL_MAX_PAGES"),
                splitCsv(env.getOrDefault("VERIFY_IDS", ""))
        );
    }

    public static AppConfig smokeConfig() {
        return from(Map.of(
                "REDDIT_CLIENT_ID", "sample-client-id",
                "REDDIT_CLIENT_SECRET", "sample-client-secret",
                "REDDIT_USER_AGENT", "script:reddit-api-cyoa-indexer:v1.0.0 (by u/sample)",
                "GOOGLE_SHEET_ID", "sample-sheet-id",
                "GOOGLE_SERVICE_ACCOUNT_JSON", "{\"client_email\":\"sample@example.com\",\"private_key\":\"sample\",\"token_uri\":\"https://oauth2.googleapis.com/token\"}",
                "SUBREDDITS", "makeyourchoice,InteractiveCYOA,nsfwcyoa",
                "BACKFILL_START_DATE", "2025-05-01"
        ));
    }

    private static String required(Map<String, String> env, String key) {
        String value = env.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required environment variable: " + key);
        }
        return value.trim();
    }

    private static Optional<String> optional(Map<String, String> env, String key) {
        String value = env.get(key);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value.trim());
    }

    private static List<String> splitCsv(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split("[,\\n]"))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }

    private static long parseLong(String raw, String key) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be a whole number.", exception);
        }
    }
}
