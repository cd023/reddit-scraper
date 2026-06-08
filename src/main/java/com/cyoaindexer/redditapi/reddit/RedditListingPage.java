package com.cyoaindexer.redditapi.reddit;

import com.cyoaindexer.redditapi.model.RedditPost;

import java.util.List;

public record RedditListingPage(List<RedditPost> posts, String after) {
}
