package com.cyoaindexer.redditapi.classify;

import com.cyoaindexer.redditapi.model.ClassificationResult;
import com.cyoaindexer.redditapi.model.RedditPost;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class CyoaClassifier {
    private static final Set<String> EXCLUDED_FLAIRS = Set.of(
            "discussion",
            "request",
            "meta",
            "looking for",
            "question"
    );

    private final UrlExtractor urlExtractor;

    public CyoaClassifier() {
        this(new UrlExtractor());
    }

    public CyoaClassifier(UrlExtractor urlExtractor) {
        this.urlExtractor = urlExtractor;
    }

    public ClassificationResult classify(RedditPost post) {
        String flair = normalized(post.flair());
        List<String> urls = urlExtractor.candidateUrls(post);

        for (String url : urls) {
            if (isStrongInteractiveUrl(url)) {
                return ClassificationResult.of("interactive", url, "Interactive URL indicator matched.", false);
            }
        }

        if (!post.galleryImageUrls().isEmpty()) {
            String finalUrl = urls.isEmpty() ? "" : urls.get(0);
            return ClassificationResult.of("static", finalUrl, "Reddit gallery metadata is present.", false);
        }

        for (String url : urls) {
            if (isStaticUrl(url)) {
                return ClassificationResult.of("static", url, "Static image URL indicator matched.", false);
            }
        }

        for (String url : urls) {
            if (isGenericInteractiveUrl(url)) {
                return ClassificationResult.of("interactive", url, "CYOA keyword URL indicator matched.", false);
            }
        }

        if (isExcludedFlair(flair)) {
            return ClassificationResult.of("ignored", "", "Flair is usually not an indexable CYOA post.", false);
        }

        if (isLikelyIncludedFlair(flair)) {
            return ClassificationResult.of("unknown", urls.isEmpty() ? "" : urls.get(0), "Likely CYOA flair but no recognized URL signal.", true);
        }

        return ClassificationResult.of("unknown", urls.isEmpty() ? "" : urls.get(0), "No clear CYOA classification signal.", true);
    }

    private static boolean isLikelyIncludedFlair(String flair) {
        return flair.contains("cyoa")
                || flair.equals("oc")
                || flair.equals("interactive")
                || flair.equals("static");
    }

    private static boolean isExcludedFlair(String flair) {
        return EXCLUDED_FLAIRS.stream().anyMatch(flair::contains);
    }

    private static boolean isStrongInteractiveUrl(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.contains("neocities.org")
                || lower.contains("itch.io")
                || lower.contains("github.io")
                || lower.contains("intcyoa")
                || lower.contains("interactive");
    }

    private static boolean isGenericInteractiveUrl(String url) {
        return url.toLowerCase(Locale.ROOT).contains("cyoa");
    }

    private static boolean isStaticUrl(String url) {
        String host = host(url);
        return host.equals("i.redd.it")
                || host.equals("preview.redd.it")
                || host.equals("imgur.com")
                || host.endsWith(".imgur.com")
                || hasImageExtension(url);
    }

    private static boolean hasImageExtension(String url) {
        String path = path(url).toLowerCase(Locale.ROOT);
        return path.endsWith(".jpg")
                || path.endsWith(".jpeg")
                || path.endsWith(".png")
                || path.endsWith(".webp");
    }

    private static String host(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    private static String path(String url) {
        try {
            String path = URI.create(url).getPath();
            return path == null ? url : path;
        } catch (IllegalArgumentException exception) {
            return url;
        }
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
