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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;

public class BackfillPipeline {
    private final AppConfig config;
    private final RedditApiClient reddit;
    private final GoogleSheetsClient sheets;
    private final StateStore state;
    private final CyoaClassifier classifier;

    public BackfillPipeline(AppConfig config, RedditApiClient reddit, GoogleSheetsClient sheets, StateStore state, CyoaClassifier classifier) {
        this.config = config;
        this.reddit = reddit;
        this.sheets = sheets;
        this.state = state;
        this.classifier = classifier;
    }

    public void run() {
        sheets.ensureHeader(SheetSchema.BACKFILL_RAW, SheetSchema.RAW_COLUMNS);
        long cutoffUtc = config.backfillStartDate().atStartOfDay().toEpochSecond(ZoneOffset.UTC);
        for (String subreddit : config.subreddits()) {
            if (state.get(StateStore.key("reddit_backfill_done", subreddit)).isPresent()) {
                continue;
            }
            runSubreddit(subreddit, cutoffUtc);
        }
    }

    private void runSubreddit(String subreddit, long cutoffUtc) {
        String cursor = state.get(StateStore.key("reddit_backfill_cursor", subreddit)).orElse("");
        int pages = 0;

        while (true) {
            try {
                RedditListingPage page = reddit.fetchNew(subreddit, cursor, 100);
                if (page.posts().isEmpty()) {
                    state.set(StateStore.key("reddit_backfill_done", subreddit), "listing_stopped");
                    return;
                }

                List<RedditPost> inRange = page.posts().stream()
                        .filter(post -> post.createdUtc() >= cutoffUtc)
                        .toList();
                appendPosts(inRange);

                page.posts().stream()
                        .min(Comparator.comparingLong(RedditPost::createdUtc))
                        .ifPresent(oldest -> state.set(
                                StateStore.key("reddit_backfill_oldest_seen_utc", subreddit),
                                Long.toString(oldest.createdUtc())
                        ));

                boolean reachedTargetDate = page.posts().stream().anyMatch(post -> post.createdUtc() < cutoffUtc);
                if (reachedTargetDate) {
                    state.set(StateStore.key("reddit_backfill_done", subreddit), "target_date_reached:" + config.backfillStartDate());
                    return;
                }

                cursor = page.after();
                if (cursor == null || cursor.isBlank()) {
                    state.set(StateStore.key("reddit_backfill_done", subreddit), "listing_stopped");
                    return;
                }
                state.set(StateStore.key("reddit_backfill_cursor", subreddit), cursor);

                pages++;
                if (config.backfillMaxPages() > 0 && pages >= config.backfillMaxPages()) {
                    state.set(StateStore.key("reddit_backfill_last_pause", subreddit), Instant.now().toString());
                    return;
                }
                sleepBetweenRequests();
            } catch (RedditApiException exception) {
                state.set(StateStore.key("reddit_backfill_error", subreddit), exception.statusCode() + ": " + exception.getMessage());
                return;
            }
        }
    }

    private void appendPosts(List<RedditPost> posts) {
        List<List<String>> rows = posts.stream()
                .map(post -> CyoaRecord.fromPost(post, classifier.classify(post), null, "ok", "", SheetSchema.BACKFILL_RAW))
                .map(SheetSchema::rowFor)
                .toList();
        sheets.appendRows(SheetSchema.BACKFILL_RAW, rows);
    }

    private void sleepBetweenRequests() {
        try {
            Thread.sleep(config.requestDelay().toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
