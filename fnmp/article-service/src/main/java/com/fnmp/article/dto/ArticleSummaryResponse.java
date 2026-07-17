package com.fnmp.article.dto;

import com.fnmp.common.domain.ArticleCategory;
import com.fnmp.common.domain.ArticleStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ArticleSummaryResponse(
        UUID id,
        String headline,
        String summary,
        String source,
        ArticleStatus status,
        ArticleCategory category,
        Instant publicationTimestamp,
        List<String> tags) {
}