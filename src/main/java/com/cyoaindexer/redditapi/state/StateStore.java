package com.cyoaindexer.redditapi.state;

import com.cyoaindexer.redditapi.sheets.GoogleSheetsClient;
import com.cyoaindexer.redditapi.sheets.SheetSchema;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class StateStore {
    private final GoogleSheetsClient sheets;

    public StateStore(GoogleSheetsClient sheets) {
        this.sheets = sheets;
    }

    public Optional<String> get(String key) {
        List<List<String>> rows = sheets.readRows(SheetSchema.STATE);
        for (int i = headerOffset(rows); i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (row.size() >= 2 && key.equals(row.get(0))) {
                return Optional.ofNullable(row.get(1)).filter(value -> !value.isBlank());
            }
        }
        return Optional.empty();
    }

    public void set(String key, String value) {
        sheets.ensureHeader(SheetSchema.STATE, SheetSchema.STATE_COLUMNS);
        List<List<String>> rows = new ArrayList<>(sheets.readRows(SheetSchema.STATE));
        if (rows.isEmpty()) {
            rows.add(SheetSchema.STATE_COLUMNS);
        }

        int offset = headerOffset(rows);
        List<String> updated = List.of(key, value == null ? "" : value, Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
        for (int i = offset; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (!row.isEmpty() && key.equals(row.get(0))) {
                rows.set(i, updated);
                sheets.replaceValues(SheetSchema.STATE, rows);
                return;
            }
        }
        rows.add(updated);
        sheets.replaceValues(SheetSchema.STATE, rows);
    }

    public static String key(String prefix, String subreddit) {
        return prefix + ":" + subreddit;
    }

    private static int headerOffset(List<List<String>> rows) {
        if (!rows.isEmpty() && !rows.get(0).isEmpty() && "key".equals(rows.get(0).get(0))) {
            return 1;
        }
        return 0;
    }
}
