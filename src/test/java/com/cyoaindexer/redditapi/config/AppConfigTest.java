package com.cyoaindexer.redditapi.config;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppConfigTest {
    @Test
    void parsesMultipleSubreddits() {
        AppConfig config = AppConfig.from(baseEnv());

        assertEquals(3, config.subreddits().size());
        assertEquals("makeyourchoice", config.subreddits().get(0));
        assertEquals("InteractiveCYOA", config.subreddits().get(1));
        assertEquals("nsfwcyoa", config.subreddits().get(2));
        assertEquals(LocalDate.of(2025, 5, 1), config.backfillStartDate());
    }

    static Map<String, String> baseEnv() {
        return Map.of(
                "REDDIT_CLIENT_ID", "client",
                "REDDIT_CLIENT_SECRET", "secret",
                "REDDIT_USER_AGENT", "script:test:v1 (by u/test)",
                "GOOGLE_SHEET_ID", "sheet",
                "GOOGLE_SERVICE_ACCOUNT_JSON", "{\"client_email\":\"test@example.com\",\"private_key\":\"key\"}",
                "SUBREDDITS", "makeyourchoice, InteractiveCYOA, nsfwcyoa",
                "BACKFILL_START_DATE", "2025-05-01"
        );
    }
}
