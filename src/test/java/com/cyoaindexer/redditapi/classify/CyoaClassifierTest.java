package com.cyoaindexer.redditapi.classify;

import com.cyoaindexer.redditapi.model.ClassificationResult;
import com.cyoaindexer.redditapi.model.RedditPost;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CyoaClassifierTest {
    private final CyoaClassifier classifier = new CyoaClassifier();

    @Test
    void classifiesStaticUrl() {
        ClassificationResult result = classifier.classify(post("CYOA", "https://i.redd.it/image.png", "", List.of()));

        assertEquals("static", result.detectedType());
        assertFalse(result.needsReview());
    }

    @Test
    void keepsDirectImageStaticEvenWhenFilenameContainsCyoa() {
        ClassificationResult result = classifier.classify(post("CYOA", "https://i.redd.it/my-cyoa.png", "", List.of()));

        assertEquals("static", result.detectedType());
    }

    @Test
    void classifiesInteractiveUrl() {
        ClassificationResult result = classifier.classify(post("Interactive", "https://example.neocities.org/cyoa", "", List.of()));

        assertEquals("interactive", result.detectedType());
        assertFalse(result.needsReview());
    }

    @Test
    void classifiesIgnoredFlair() {
        ClassificationResult result = classifier.classify(post("Discussion", "", "", List.of()));

        assertEquals("ignored", result.detectedType());
        assertFalse(result.needsReview());
    }

    @Test
    void classifiesUnknownWhenSignalIsUnclear() {
        ClassificationResult result = classifier.classify(post("", "https://example.com/post", "", List.of()));

        assertEquals("unknown", result.detectedType());
        assertTrue(result.needsReview());
    }

    @Test
    void classifiesUrlInsideSelftext() {
        ClassificationResult result = classifier.classify(post("CYOA", "", "Try https://example.github.io/intcyoa", List.of()));

        assertEquals("interactive", result.detectedType());
    }

    private static RedditPost post(String flair, String sourceUrl, String selftext, List<String> galleryUrls) {
        return new RedditPost(
                "abc",
                "t3_abc",
                "makeyourchoice",
                "Title",
                "author",
                1L,
                "1970-01-01T00:00:01Z",
                flair,
                "/r/makeyourchoice/comments/abc/title/",
                "https://www.reddit.com/r/makeyourchoice/comments/abc/title/",
                sourceUrl,
                "",
                selftext,
                "",
                galleryUrls,
                false,
                false
        );
    }
}
