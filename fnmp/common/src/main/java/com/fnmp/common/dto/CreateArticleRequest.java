package com.fnmp.common.dto;

import com.fnmp.common.domain.ArticleCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record CreateArticleRequest(
        @NotBlank @Size(max = 512) String headline,
        @Size(max = 2000) String summary,
        String body,
        @NotBlank String source,
        @NotNull Instant publicationTimestamp,
        ArticleCategory category,
        List<@NotBlank @Size(max = 100) String> tags) {
}