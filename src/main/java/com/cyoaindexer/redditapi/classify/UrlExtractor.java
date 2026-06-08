package com.cyoaindexer.redditapi.classify;

import com.cyoaindexer.redditapi.model.RedditPost;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UrlExtractor {
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s\\]\\)<>\"']+");

    public List<String> fromText(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        Set<String> urls = new LinkedHashSet<>();
        Matcher matcher = URL_PATTERN.matcher(text);
        while (matcher.find()) {
            urls.add(trimTrailingPunctuation(matcher.group()));
        }
        return new ArrayList<>(urls);
    }

    public List<String> candidateUrls(RedditPost post) {
        Set<String> urls = new LinkedHashSet<>();
        add(urls, post.sourceUrl());
        urls.addAll(fromText(post.selftext()));
        post.galleryImageUrls().forEach(url -> add(urls, url));
        return new ArrayList<>(urls);
    }

    private static void add(Set<String> urls, String value) {
        if (value != null && !value.isBlank()) {
            urls.add(trimTrailingPunctuation(value.trim()));
        }
    }

    private static String trimTrailingPunctuation(String url) {
        String result = url;
        while (!result.isEmpty() && ".,;:!?".indexOf(result.charAt(result.length() - 1)) >= 0) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
