package com.cyoaindexer.redditapi.reddit;

import com.cyoaindexer.redditapi.model.RedditPost;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class RedditPostMapper {
    private static final String REDDIT_BASE = "https://www.reddit.com";
    private static final ObjectMapper JSON = new ObjectMapper();

    public RedditPost fromListingChild(JsonNode child) {
        return fromData(child.path("data"));
    }

    public RedditPost fromData(JsonNode data) {
        String id = text(data, "id");
        String fullname = text(data, "name");
        if (fullname.isBlank() && !id.isBlank()) {
            fullname = RedditApiClient.toFullname(id);
        }

        long createdUtc = Math.round(data.path("created_utc").asDouble(0));
        String createdIso = createdUtc <= 0 ? "" : Instant.ofEpochSecond(createdUtc).toString();
        String permalink = text(data, "permalink");
        String redditPostUrl = permalink.startsWith("http") ? permalink : REDDIT_BASE + permalink;

        List<String> galleryUrls = galleryUrls(data);
        return new RedditPost(
                id,
                fullname,
                text(data, "subreddit"),
                text(data, "title"),
                text(data, "author"),
                createdUtc,
                createdIso,
                text(data, "link_flair_text"),
                permalink,
                redditPostUrl,
                firstPresentText(data, "url_overridden_by_dest", "url"),
                text(data, "domain"),
                text(data, "selftext"),
                galleryMetadata(data),
                galleryUrls,
                data.path("over_18").asBoolean(false),
                data.path("spoiler").asBoolean(false)
        );
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("");
    }

    private static String firstPresentText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static List<String> galleryUrls(JsonNode data) {
        List<String> urls = new ArrayList<>();
        JsonNode metadata = data.path("media_metadata");
        if (!metadata.isObject()) {
            return urls;
        }

        Iterator<Map.Entry<String, JsonNode>> fields = metadata.fields();
        while (fields.hasNext()) {
            JsonNode item = fields.next().getValue();
            String url = text(item.path("s"), "u");
            if (url.isBlank() && item.path("p").isArray() && item.path("p").size() > 0) {
                url = text(item.path("p").get(0), "u");
            }
            if (!url.isBlank()) {
                urls.add(url.replace("&amp;", "&"));
            }
        }
        return urls;
    }

    private static String galleryMetadata(JsonNode data) {
        ObjectNode metadata = JSON.createObjectNode();
        if (data.has("gallery_data")) {
            metadata.set("gallery_data", data.path("gallery_data"));
        }
        if (data.has("media_metadata")) {
            metadata.set("media_metadata", data.path("media_metadata"));
        }
        return metadata.isEmpty() ? "" : metadata.toString();
    }
}
