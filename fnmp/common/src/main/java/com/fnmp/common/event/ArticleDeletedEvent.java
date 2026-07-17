package com.fnmp.common.event;

import java.util.UUID;

public record ArticleDeletedEvent(
        UUID id,
        String reason) {

    public static final String TOPIC = "article.deleted";
}