package com.cyoaindexer.redditapi.reddit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RedditApiClientTest {
    @Test
    void generatesSubmissionFullname() {
        assertEquals("t3_abc123", RedditApiClient.toFullname("abc123"));
        assertEquals("t3_abc123", RedditApiClient.toFullname("t3_abc123"));
    }
}
