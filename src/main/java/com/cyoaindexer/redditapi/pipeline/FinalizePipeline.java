package com.cyoaindexer.redditapi.pipeline;

import com.cyoaindexer.redditapi.model.CyoaRecord;
import com.cyoaindexer.redditapi.sheets.GoogleSheetsClient;
import com.cyoaindexer.redditapi.sheets.SheetSchema;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FinalizePipeline {
    private final GoogleSheetsClient sheets;

    public FinalizePipeline(GoogleSheetsClient sheets) {
        this.sheets = sheets;
    }

    public void run() {
        sheets.ensureHeader(SheetSchema.CYOA_INDEX, SheetSchema.RAW_COLUMNS);

        List<CyoaRecord> records = new ArrayList<>();
        records.addAll(SheetSchema.recordsFromRows(sheets.readRows(SheetSchema.BACKFILL_RAW), SheetSchema.BACKFILL_RAW));
        records.addAll(SheetSchema.recordsFromRows(sheets.readRows(SheetSchema.LIVE_RAW), SheetSchema.LIVE_RAW));
        records.addAll(SheetSchema.recordsFromRows(sheets.readRows(SheetSchema.VERIFIED_RAW), SheetSchema.VERIFIED_RAW));

        List<List<String>> rows = new ArrayList<>();
        rows.add(SheetSchema.RAW_COLUMNS);
        mergePreferNewest(records).stream()
                .map(SheetSchema::rowFor)
                .forEach(rows::add);
        sheets.replaceValues(SheetSchema.CYOA_INDEX, rows);
    }

    public static List<CyoaRecord> mergePreferNewest(Collection<CyoaRecord> records) {
        Map<String, CyoaRecord> byId = new LinkedHashMap<>();
        for (CyoaRecord record : records) {
            if (record.redditPostId().isBlank()) {
                continue;
            }
            CyoaRecord existing = byId.get(record.redditPostId());
            if (existing == null || shouldReplace(record, existing)) {
                byId.put(record.redditPostId(), record);
            }
        }
        return byId.values().stream()
                .sorted(Comparator.comparingLong(FinalizePipeline::createdUtc).reversed())
                .toList();
    }

    private static boolean shouldReplace(CyoaRecord candidate, CyoaRecord existing) {
        int checkedCompare = checked(candidate).compareTo(checked(existing));
        if (checkedCompare != 0) {
            return checkedCompare > 0;
        }
        return sourcePriority(candidate.sourceSheet()) > sourcePriority(existing.sourceSheet());
    }

    private static Instant checked(CyoaRecord record) {
        try {
            return Instant.parse(record.lastCheckedIso());
        } catch (RuntimeException exception) {
            return Instant.EPOCH;
        }
    }

    private static long createdUtc(CyoaRecord record) {
        try {
            return Long.parseLong(record.createdUtc());
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private static int sourcePriority(String sourceSheet) {
        if (SheetSchema.VERIFIED_RAW.equals(sourceSheet)) {
            return 3;
        }
        if (SheetSchema.LIVE_RAW.equals(sourceSheet)) {
            return 2;
        }
        if (SheetSchema.BACKFILL_RAW.equals(sourceSheet)) {
            return 1;
        }
        return 0;
    }
}
