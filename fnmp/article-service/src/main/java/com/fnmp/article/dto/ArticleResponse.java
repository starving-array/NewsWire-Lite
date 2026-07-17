package com.fnmp.article.dto;

import com.fnmp.common.domain.ArticleCategory;
import com.fnmp.common.domain.ArticleStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ArticleResponse(
        UUID id,
        String headline,
        String summary,
        String body,
        String source,
        Instant publicationTimestamp,
        Instant createdAt,
        Instant updatedAt,
        ArticleStatus status,
        ArticleCategory category,
        List<String> tags) {
}