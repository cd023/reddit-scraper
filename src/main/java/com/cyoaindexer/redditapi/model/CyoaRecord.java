package com.cyoaindexer.redditapi.model;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

public record CyoaRecord(
        String redditPostId,
        String fullname,
        String subreddit,
        String title,
        String author,
        String createdUtc,
        String createdIso,
        String flair,
        String permalink,
        String redditPostUrl,
        String sourceUrl,
        String domain,
        String selftext,
        String galleryMetadata,
        List<String> galleryImageUrls,
        boolean over18,
        boolean spoiler,
        String detectedType,
        String detectedFinalUrl,
        String classificationReason,
        String firstSeenIso,
        String lastCheckedIso,
        String status,
        boolean needsReview,
        String errorMessage,
        String sourceSheet
) {
    public static CyoaRecord fromPost(
            RedditPost post,
            ClassificationResult classification,
            Instant firstSeen,
            String status,
            String errorMessage,
            String sourceSheet
    ) {
        Instant checked = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant firstSeenValue = firstSeen == null ? checked : firstSeen.truncatedTo(ChronoUnit.SECONDS);
        return new CyoaRecord(
                post.id(),
                post.fullname(),
                post.subreddit(),
                post.title(),
                post.author(),
                Long.toString(post.createdUtc()),
                post.createdIso(),
                post.flair(),
                post.permalink(),
                post.redditPostUrl(),
                post.sourceUrl(),
                post.domain(),
                post.selftext(),
                post.galleryMetadata(),
                post.galleryImageUrls(),
                post.over18(),
                post.spoiler(),
                classification.detectedType(),
                classification.detectedFinalUrl(),
                classification.reason(),
                firstSeenValue.toString(),
                checked.toString(),
                status,
                classification.needsReview(),
                errorMessage == null ? "" : errorMessage,
                sourceSheet
        );
    }

    public static CyoaRecord errorForId(String id, String errorMessage, String sourceSheet) {
        String cleanId = id == null ? "" : id.replaceFirst("^t3_", "");
        return new CyoaRecord(
                cleanId,
                cleanId.isBlank() ? "" : "t3_" + cleanId,
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                List.of(),
                false,
                false,
                "unknown",
                "",
                "Reddit API lookup did not return public metadata.",
                Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(),
                Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(),
                "error",
                true,
                errorMessage,
                sourceSheet
        );
    }

    public CyoaRecord withSourceSheet(String value) {
        return new CyoaRecord(
                redditPostId,
                fullname,
                subreddit,
                title,
                author,
                createdUtc,
                createdIso,
                flair,
                permalink,
                redditPostUrl,
                sourceUrl,
                domain,
                selftext,
                galleryMetadata,
                galleryImageUrls,
                over18,
                spoiler,
                detectedType,
                detectedFinalUrl,
                classificationReason,
                firstSeenIso,
                lastCheckedIso,
                status,
                needsReview,
                errorMessage,
                value
        );
    }
}
