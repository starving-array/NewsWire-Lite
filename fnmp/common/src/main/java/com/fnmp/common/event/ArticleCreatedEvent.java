package com.fnmp.common.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ArticleCreatedEvent(
        UUID id,
        Instant publicationTimestamp,
        String headline,
        String summary,
        String body,
        String source,
        String category,
        List<String> tags,
        Instant createdAt) {

    public static final String TOPIC = "article.created";
}