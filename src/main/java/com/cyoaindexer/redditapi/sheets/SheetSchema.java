package com.cyoaindexer.redditapi.sheets;

import com.cyoaindexer.redditapi.model.CyoaRecord;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SheetSchema {
    public static final String LIVE_RAW = "reddit_live_raw";
    public static final String BACKFILL_RAW = "reddit_backfill_raw";
    public static final String VERIFIED_RAW = "reddit_verified_raw";
    public static final String COMPARE = "compare";
    public static final String CYOA_INDEX = "cyoa_index";
    public static final String STATE = "state";

    public static final List<String> RAW_COLUMNS = List.of(
            "reddit_post_id",
            "fullname",
            "subreddit",
            "title",
            "author",
            "created_utc",
            "created_iso",
            "flair",
            "permalink",
            "reddit_post_url",
            "source_url",
            "domain",
            "selftext",
            "gallery_metadata",
            "gallery_image_urls",
            "over_18",
            "spoiler",
            "detected_type",
            "detected_final_url",
            "classification_reason",
            "first_seen_iso",
            "last_checked_iso",
            "status",
            "needs_review",
            "error_message",
            "source_sheet"
    );

    public static final List<String> STATE_COLUMNS = List.of("key", "value", "updated_iso");

    private SheetSchema() {
    }

    public static List<String> rowFor(CyoaRecord record) {
        return List.of(
                value(record.redditPostId()),
                value(record.fullname()),
                value(record.subreddit()),
                value(record.title()),
                value(record.author()),
                value(record.createdUtc()),
                value(record.createdIso()),
                value(record.flair()),
                value(record.permalink()),
                value(record.redditPostUrl()),
                value(record.sourceUrl()),
                value(record.domain()),
                value(record.selftext()),
                value(record.galleryMetadata()),
                String.join("\n", record.galleryImageUrls()),
                Boolean.toString(record.over18()),
                Boolean.toString(record.spoiler()),
                value(record.detectedType()),
                value(record.detectedFinalUrl()),
                value(record.classificationReason()),
                value(record.firstSeenIso()),
                value(record.lastCheckedIso()),
                value(record.status()),
                Boolean.toString(record.needsReview()),
                value(record.errorMessage()),
                value(record.sourceSheet())
        );
    }

    public static List<CyoaRecord> recordsFromRows(List<List<String>> rows, String fallbackSourceSheet) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        int start = looksLikeHeader(rows.get(0)) ? 1 : 0;
        Map<String, Integer> indexes = indexMap(looksLikeHeader(rows.get(0)) ? rows.get(0) : RAW_COLUMNS);
        List<CyoaRecord> records = new ArrayList<>();
        for (int i = start; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (row.isEmpty() || cell(row, indexes, "reddit_post_id").isBlank()) {
                continue;
            }
            String sourceSheet = cell(row, indexes, "source_sheet");
            records.add(new CyoaRecord(
                    cell(row, indexes, "reddit_post_id"),
                    cell(row, indexes, "fullname"),
                    cell(row, indexes, "subreddit"),
                    cell(row, indexes, "title"),
                    cell(row, indexes, "author"),
                    cell(row, indexes, "created_utc"),
                    cell(row, indexes, "created_iso"),
                    cell(row, indexes, "flair"),
                    cell(row, indexes, "permalink"),
                    cell(row, indexes, "reddit_post_url"),
                    cell(row, indexes, "source_url"),
                    cell(row, indexes, "domain"),
                    cell(row, indexes, "selftext"),
                    cell(row, indexes, "gallery_metadata"),
                    splitLines(cell(row, indexes, "gallery_image_urls")),
                    Boolean.parseBoolean(cell(row, indexes, "over_18")),
                    Boolean.parseBoolean(cell(row, indexes, "spoiler")),
                    cell(row, indexes, "detected_type"),
                    cell(row, indexes, "detected_final_url"),
                    cell(row, indexes, "classification_reason"),
                    cell(row, indexes, "first_seen_iso"),
                    cell(row, indexes, "last_checked_iso"),
                    cell(row, indexes, "status"),
                    Boolean.parseBoolean(cell(row, indexes, "needs_review")),
                    cell(row, indexes, "error_message"),
                    sourceSheet.isBlank() ? fallbackSourceSheet : sourceSheet
            ));
        }
        return records;
    }

    private static boolean looksLikeHeader(List<String> row) {
        return !row.isEmpty() && "reddit_post_id".equals(row.get(0));
    }

    private static Map<String, Integer> indexMap(List<String> header) {
        Map<String, Integer> indexes = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            indexes.put(header.get(i), i);
        }
        return indexes;
    }

    private static String cell(List<String> row, Map<String, Integer> indexes, String name) {
        Integer index = indexes.get(name);
        if (index == null || index < 0 || index >= row.size()) {
            return "";
        }
        return row.get(index);
    }

    private static List<String> splitLines(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return value.lines().map(String::trim).filter(line -> !line.isEmpty()).toList();
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
