package com.cyoaindexer.redditapi.cli;

import com.cyoaindexer.redditapi.classify.CyoaClassifier;
import com.cyoaindexer.redditapi.config.AppConfig;
import com.cyoaindexer.redditapi.model.CyoaRecord;
import com.cyoaindexer.redditapi.model.RedditPost;
import com.cyoaindexer.redditapi.pipeline.BackfillPipeline;
import com.cyoaindexer.redditapi.pipeline.FinalizePipeline;
import com.cyoaindexer.redditapi.pipeline.LivePipeline;
import com.cyoaindexer.redditapi.pipeline.VerifyPipeline;
import com.cyoaindexer.redditapi.reddit.RedditApiClient;
import com.cyoaindexer.redditapi.reddit.RedditAuthClient;
import com.cyoaindexer.redditapi.sheets.GoogleSheetsClient;
import com.cyoaindexer.redditapi.sheets.SheetSchema;
import com.cyoaindexer.redditapi.state.StateStore;

import java.time.Instant;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        String command = args.length == 0 ? "help" : args[0].trim();
        try {
            if ("smoke".equals(command)) {
                runSmoke();
                return;
            }
            if ("help".equals(command) || "-h".equals(command) || "--help".equals(command)) {
                printHelp();
                return;
            }

            AppConfig config = AppConfig.fromEnvironment();
            CyoaClassifier classifier = new CyoaClassifier();
            GoogleSheetsClient sheets = new GoogleSheetsClient(config);
            StateStore state = new StateStore(sheets);
            RedditAuthClient auth = new RedditAuthClient(config);
            RedditApiClient reddit = new RedditApiClient(config, auth);

            switch (command) {
                case "live" -> new LivePipeline(config, reddit, sheets, state, classifier).run();
                case "reddit-backfill" -> new BackfillPipeline(config, reddit, sheets, state, classifier).run();
                case "verify-ids" -> new VerifyPipeline(config, reddit, sheets, state, classifier).run();
                case "finalize" -> new FinalizePipeline(sheets).run();
                case "run-all" -> {
                    LivePipeline live = new LivePipeline(config, reddit, sheets, state, classifier);
                    live.run();
                    new FinalizePipeline(sheets).run();
                }
                default -> {
                    printHelp();
                    System.exit(2);
                }
            }
        } catch (RuntimeException exception) {
            System.err.println(exception.getMessage());
            System.exit(1);
        }
    }

    private static void runSmoke() {
        AppConfig config = AppConfig.smokeConfig();
        RedditPost sample = new RedditPost(
                "abc123",
                "t3_abc123",
                config.subreddits().get(0),
                "Sample CYOA",
                "sample_author",
                1_735_689_600L,
                Instant.ofEpochSecond(1_735_689_600L).toString(),
                "CYOA",
                "/r/makeyourchoice/comments/abc123/sample/",
                "https://www.reddit.com/r/makeyourchoice/comments/abc123/sample/",
                "https://i.redd.it/sample.png",
                "i.redd.it",
                "Backup link: https://example.neocities.org/cyoa",
                "",
                List.of(),
                false,
                false
        );
        CyoaRecord record = CyoaRecord.fromPost(sample, new CyoaClassifier().classify(sample), Instant.now(), "ok", "", SheetSchema.LIVE_RAW);
        List<String> row = SheetSchema.rowFor(record);
        if (!RedditApiClient.toFullname(sample.id()).equals(sample.fullname())) {
            throw new IllegalStateException("Fullname generation failed.");
        }
        if (row.size() != SheetSchema.RAW_COLUMNS.size()) {
            throw new IllegalStateException("Sheet row width mismatch.");
        }
        System.out.println("Smoke check passed for " + config.subreddits().size() + " subreddits and " + row.size() + " sheet columns.");
    }

    private static void printHelp() {
        System.out.println("""
                Usage:
                  java -jar target/app.jar live
                  java -jar target/app.jar reddit-backfill
                  java -jar target/app.jar verify-ids
                  java -jar target/app.jar finalize
                  java -jar target/app.jar run-all
                  java -jar target/app.jar smoke
                """);
    }
}
