package com.cyoaindexer.redditapi.classify;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UrlExtractorTest {
    @Test
    void extractsUrlsFromSelftext() {
        UrlExtractor extractor = new UrlExtractor();

        assertEquals(
                "https://example.neocities.org/cyoa",
                extractor.fromText("Play here: https://example.neocities.org/cyoa.").get(0)
        );
    }
}
