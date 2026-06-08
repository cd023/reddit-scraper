package com.cyoaindexer.redditapi.pipeline;

import com.cyoaindexer.redditapi.model.CyoaRecord;
import com.cyoaindexer.redditapi.sheets.SheetSchema;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FinalizePipelineTest {
    @Test
    void deduplicatesByRedditId() {
        List<CyoaRecord> merged = FinalizePipeline.mergePreferNewest(List.of(
                record("abc", "Old title", "2025-01-01T00:00:00Z", SheetSchema.BACKFILL_RAW),
                record("abc", "New title", "2025-01-02T00:00:00Z", SheetSchema.LIVE_RAW)
        ));

        assertEquals(1, merged.size());
        assertEquals("New title", merged.get(0).title());
    }

    @Test
    void usesVerifiedRawWhenTimestampsTie() {
        List<CyoaRecord> merged = FinalizePipeline.mergePreferNewest(List.of(
                record("abc", "Live title", "2025-01-02T00:00:00Z", SheetSchema.LIVE_RAW),
                record("abc", "Verified title", "2025-01-02T00:00:00Z", SheetSchema.VERIFIED_RAW)
        ));

        assertEquals("Verified title", merged.get(0).title());
    }

    private static CyoaRecord record(String id, String title, String checkedIso, String sourceSheet) {
        return new CyoaRecord(
                id,
                "t3_" + id,
                "makeyourchoice",
                title,
                "author",
                "100",
                "1970-01-01T00:01:40Z",
                "CYOA",
                "/r/makeyourchoice/comments/" + id + "/title/",
                "https://www.reddit.com/r/makeyourchoice/comments/" + id + "/title/",
                "https://i.redd.it/" + id + ".png",
                "i.redd.it",
                "",
                "",
                List.of(),
                false,
                false,
                "static",
                "https://i.redd.it/" + id + ".png",
                "Static image URL indicator matched.",
                "2025-01-01T00:00:00Z",
                checkedIso,
                "ok",
                false,
                "",
                sourceSheet
        );
    }
}
