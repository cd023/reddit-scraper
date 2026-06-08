package com.cyoaindexer.redditapi.pipeline;

import com.cyoaindexer.redditapi.classify.CyoaClassifier;
import com.cyoaindexer.redditapi.config.AppConfig;
import com.cyoaindexer.redditapi.model.CyoaRecord;
import com.cyoaindexer.redditapi.model.RedditPost;
import com.cyoaindexer.redditapi.reddit.RedditApiClient;
import com.cyoaindexer.redditapi.reddit.RedditApiException;
import com.cyoaindexer.redditapi.reddit.RedditListingPage;
import com.cyoaindexer.redditapi.sheets.GoogleSheetsClient;
import com.cyoaindexer.redditapi.sheets.SheetSchema;
import com.cyoaindexer.redditapi.state.StateStore;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LivePipeline {
    private final AppConfig config;
    private final RedditApiClient reddit;
    private final GoogleSheetsClient sheets;
    private final StateStore state;
    private final CyoaClassifier classifier;

    public LivePipeline(AppConfig config, RedditApiClient reddit, GoogleSheetsClient sheets, StateStore state, CyoaClassifier classifier) {
        this.config = config;
        this.reddit = reddit;
        this.sheets = sheets;
        this.state = state;
        this.classifier = classifier;
    }

    public void run() {
        sheets.ensureHeader(SheetSchema.LIVE_RAW, SheetSchema.RAW_COLUMNS);
        for (String subreddit : config.subreddits()) {
            try {
                RedditListingPage page = reddit.fetchNew(subreddit, null, 100);
                appendPosts(page.posts(), SheetSchema.LIVE_RAW);
                state.set(StateStore.key("live_last_run", subreddit), Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
            } catch (RedditApiException exception) {
                state.set(StateStore.key("live_error", subreddit), exception.statusCode() + ": " + exception.getMessage());
            }
            sleepBetweenRequests();
        }
        runPendingRechecks();
    }

    public void runPendingRechecks() {
        List<CyoaRecord> records = SheetSchema.recordsFromRows(sheets.readRows(SheetSchema.LIVE_RAW), SheetSchema.LIVE_RAW);
        Instant cutoff = Instant.now().minus(3, ChronoUnit.DAYS);
        Set<String> fullnames = new LinkedHashSet<>();
        Map<String, Instant> firstSeenByFullname = new LinkedHashMap<>();
        for (CyoaRecord record : records) {
            Instant firstSeen = parsedInstant(record.firstSeenIso());
            if (!record.fullname().isBlank() && firstSeen.isAfter(cutoff)) {
                fullnames.add(record.fullname());
                firstSeenByFullname.merge(record.fullname(), firstSeen, (left, right) -> left.isBefore(right) ? left : right);
            }
        }
        for (List<String> batch : batches(fullnames.stream().toList(), 100)) {
            try {
                List<List<String>> rows = reddit.lookupByFullnames(batch).stream()
                        .map(post -> CyoaRecord.fromPost(
                                post,
                                classifier.classify(post),
                                firstSeenByFullname.get(post.fullname()),
                                "ok",
                                "",
                                SheetSchema.LIVE_RAW
                        ))
                        .map(SheetSchema::rowFor)
                        .toList();
                sheets.appendRows(SheetSchema.LIVE_RAW, rows);
            } catch (RedditApiException exception) {
                state.set("live_recheck_error", exception.statusCode() + ": " + exception.getMessage());
            }
            sleepBetweenRequests();
        }
    }

    private void appendPosts(List<RedditPost> posts, String tab) {
        List<List<String>> rows = posts.stream()
                .map(post -> CyoaRecord.fromPost(post, classifier.classify(post), null, "ok", "", tab))
                .map(SheetSchema::rowFor)
                .toList();
        sheets.appendRows(tab, rows);
    }

    private void sleepBetweenRequests() {
        try {
            Thread.sleep(config.requestDelay().toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static Instant parsedInstant(String value) {
        try {
            return value == null || value.isBlank() ? Instant.EPOCH : Instant.parse(value);
        } catch (RuntimeException exception) {
            return Instant.EPOCH;
        }
    }

    private static List<List<String>> batches(List<String> values, int size) {
        return java.util.stream.IntStream.range(0, (values.size() + size - 1) / size)
                .mapToObj(i -> values.subList(i * size, Math.min(values.size(), (i + 1) * size)))
                .toList();
    }
}
