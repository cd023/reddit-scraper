package com.cyoaindexer.redditapi.sheets;

import com.cyoaindexer.redditapi.classify.CyoaClassifier;
import com.cyoaindexer.redditapi.model.CyoaRecord;
import com.cyoaindexer.redditapi.model.RedditPost;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SheetSchemaTest {
    @Test
    void generatesSampleSheetRowFromFakePost() {
        RedditPost post = new RedditPost(
                "abc",
                "t3_abc",
                "makeyourchoice",
                "Sample",
                "author",
                1L,
                "1970-01-01T00:00:01Z",
                "CYOA",
                "/r/makeyourchoice/comments/abc/sample/",
                "https://www.reddit.com/r/makeyourchoice/comments/abc/sample/",
                "https://i.redd.it/sample.png",
                "i.redd.it",
                "",
                "",
                List.of(),
                false,
                false
        );

        CyoaRecord record = CyoaRecord.fromPost(post, new CyoaClassifier().classify(post), Instant.EPOCH, "ok", "", SheetSchema.LIVE_RAW);

        assertEquals(SheetSchema.RAW_COLUMNS.size(), SheetSchema.rowFor(record).size());
        assertEquals("static", SheetSchema.rowFor(record).get(17));
    }
}
