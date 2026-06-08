package com.cyoaindexer.redditapi.model;

public record ClassificationResult(
        String detectedType,
        String detectedFinalUrl,
        String reason,
        boolean needsReview
) {
    public static ClassificationResult of(String type, String finalUrl, String reason, boolean needsReview) {
        return new ClassificationResult(type, finalUrl == null ? "" : finalUrl, reason, needsReview);
    }
}
