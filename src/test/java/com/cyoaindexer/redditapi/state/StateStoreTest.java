package com.cyoaindexer.redditapi.state;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StateStoreTest {
    @Test
    void generatesStateKeyPerSubreddit() {
        assertEquals("reddit_backfill_cursor:nsfwcyoa", StateStore.key("reddit_backfill_cursor", "nsfwcyoa"));
    }
}
