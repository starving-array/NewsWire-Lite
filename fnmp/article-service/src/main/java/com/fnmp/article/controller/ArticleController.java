package com.fnmp.article.controller;

import com.fnmp.article.domain.Article;
import com.fnmp.article.dto.ArticleResponse;
import com.fnmp.article.dto.ArticleSummaryResponse;
import com.fnmp.common.dto.CreateArticleRequest;
import com.fnmp.article.dto.PagedResponse;
import com.fnmp.article.mapper.ArticleMapper;
import com.fnmp.article.service.ArticleService;
import com.fnmp.article.service.ArticleService.CursorResult;
import com.fnmp.article.specification.ArticleSpecifications;
import com.fnmp.common.domain.ArticleCategory;
import com.fnmp.common.domain.ArticleStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/articles")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;
    private final ArticleMapper articleMapper;

    @PostMapping
    public ResponseEntity<ArticleResponse> create(@Valid @RequestBody CreateArticleRequest request) {
        Article article = articleService.create(request);
        ArticleResponse response = articleMapper.toResponse(article);
        return ResponseEntity
                .created(URI.create("/api/v1/articles/" + article.getUuid()))
                .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ArticleResponse> getById(@PathVariable UUID id) {
        ArticleResponse response = articleService.getById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<PagedResponse<ArticleSummaryResponse>> list(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) ArticleCategory category,
            @RequestParam(required = false) ArticleStatus status,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) Instant dateFrom,
            @RequestParam(required = false) Instant dateTo,
            @RequestParam(required = false) UUID afterId,
            @RequestParam(required = false) Instant afterPubTs,
            @PageableDefault(size = 20, sort = "id.publicationTimestamp", direction = Sort.Direction.DESC) Pageable pageable) {

        var spec = ArticleSpecifications.build(source, category, status, tag, dateFrom, dateTo);

        if (afterId != null && afterPubTs != null) {
            CursorResult<ArticleSummaryResponse> cursor = articleService.listByCursor(afterId, afterPubTs, pageable.getPageSize());
            return ResponseEntity.ok(new PagedResponse<>(
                    cursor.items(), 0, cursor.items().size(), -1, -1,
                    "cursor:" + afterId + "," + afterPubTs));
        }

        Page<ArticleSummaryResponse> page = articleService.list(spec, pageable);
        return ResponseEntity.ok(PagedResponse.from(page));
    }

    @GetMapping("/search")
    public ResponseEntity<List<ArticleSummaryResponse>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "20") int limit) {
        int cappedLimit = Math.min(limit, 100);
        List<ArticleSummaryResponse> results = articleService.search(q, cappedLimit);
        return ResponseEntity.ok(results);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        articleService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

}