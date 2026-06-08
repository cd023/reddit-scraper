package com.cyoaindexer.redditapi.model;

import java.util.List;

public record RedditPost(
        String id,
        String fullname,
        String subreddit,
        String title,
        String author,
        long createdUtc,
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
        boolean spoiler
) {
}
