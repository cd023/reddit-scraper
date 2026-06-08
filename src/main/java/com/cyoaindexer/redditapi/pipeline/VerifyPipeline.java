package com.cyoaindexer.redditapi.pipeline;

import com.cyoaindexer.redditapi.classify.CyoaClassifier;
import com.cyoaindexer.redditapi.config.AppConfig;
import com.cyoaindexer.redditapi.model.CyoaRecord;
import com.cyoaindexer.redditapi.model.RedditPost;
import com.cyoaindexer.redditapi.reddit.RedditApiClient;
import com.cyoaindexer.redditapi.reddit.RedditApiException;
import com.cyoaindexer.redditapi.sheets.GoogleSheetsClient;
import com.cyoaindexer.redditapi.sheets.SheetSchema;
import com.cyoaindexer.redditapi.state.StateStore;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class VerifyPipeline {
    private final AppConfig config;
    private final RedditApiClient reddit;
    private final GoogleSheetsClient sheets;
    private final StateStore state;
    private final CyoaClassifier classifier;

    public VerifyPipeline(AppConfig config, RedditApiClient reddit, GoogleSheetsClient sheets, StateStore state, CyoaClassifier classifier) {
        this.config = config;
        this.reddit = reddit;
        this.sheets = sheets;
        this.state = state;
        this.classifier = classifier;
    }

    public void run() {
        sheets.ensureHeader(SheetSchema.VERIFIED_RAW, SheetSchema.RAW_COLUMNS);
        List<String> ids = configuredIds();
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("No IDs to verify. Set VERIFY_IDS or state key verify_ids.");
        }

        for (List<String> batch : batches(ids, 100)) {
            List<String> fullnames = batch.stream().map(RedditApiClient::toFullname).toList();
            List<RedditPost> posts;
            try {
                posts = reddit.lookupByFullnames(fullnames);
            } catch (RedditApiException exception) {
                List<List<String>> errorRows = batch.stream()
                        .map(id -> CyoaRecord.errorForId(id, "Lookup failed: " + exception.statusCode() + " " + exception.getMessage(), SheetSchema.VERIFIED_RAW))
                        .map(SheetSchema::rowFor)
                        .toList();
                sheets.appendRows(SheetSchema.VERIFIED_RAW, errorRows);
                sleepBetweenRequests();
                continue;
            }
            Set<String> returnedIds = new HashSet<>();
            List<List<String>> okRows = posts.stream()
                    .peek(post -> returnedIds.add(post.id()))
                    .map(post -> CyoaRecord.fromPost(post, classifier.classify(post), null, "ok", "", SheetSchema.VERIFIED_RAW))
                    .map(SheetSchema::rowFor)
                    .toList();
            sheets.appendRows(SheetSchema.VERIFIED_RAW, okRows);

            List<List<String>> missingRows = batch.stream()
                    .map(id -> id.replaceFirst("^t3_", ""))
                    .filter(id -> !returnedIds.contains(id))
                    .map(id -> CyoaRecord.errorForId(id, "Post was not returned by /api/info.", SheetSchema.VERIFIED_RAW))
                    .map(SheetSchema::rowFor)
                    .toList();
            sheets.appendRows(SheetSchema.VERIFIED_RAW, missingRows);
            sleepBetweenRequests();
        }
    }

    private List<String> configuredIds() {
        if (!config.verifyIds().isEmpty()) {
            return config.verifyIds();
        }
        return state.get("verify_ids")
                .map(value -> java.util.Arrays.stream(value.split("[,\\n]"))
                        .map(String::trim)
                        .filter(id -> !id.isEmpty())
                        .toList())
                .orElse(List.of());
    }

    private void sleepBetweenRequests() {
        try {
            Thread.sleep(config.requestDelay().toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static List<List<String>> batches(List<String> values, int size) {
        return java.util.stream.IntStream.range(0, (values.size() + size - 1) / size)
                .mapToObj(i -> values.subList(i * size, Math.min(values.size(), (i + 1) * size)))
                .toList();
    }
}
