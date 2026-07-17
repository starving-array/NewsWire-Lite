package com.fnmp.article.service;

import com.fnmp.article.cache.CacheService;
import com.fnmp.article.domain.*;
import com.fnmp.common.dto.CreateArticleRequest;
import com.fnmp.article.exception.ArticleNotFoundException;
import com.fnmp.article.mapper.ArticleMapper;
import com.fnmp.article.dto.ArticleResponse;
import com.fnmp.article.dto.ArticleSummaryResponse;
import com.fnmp.article.dto.PagedResponse;
import com.fnmp.article.publisher.ArticleEventPublisher;
import com.fnmp.article.repository.ArticleAuditRepository;
import com.fnmp.article.repository.ArticleRepository;
import com.fnmp.article.repository.SourceRepository;
import com.fnmp.article.repository.TagRepository;
import com.fnmp.common.domain.ArticleStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArticleService {

    private static final int MAX_CURSOR_LIMIT = 100;

    private final ArticleRepository articleRepository;
    private final SourceRepository sourceRepository;
    private final TagRepository tagRepository;
    private final ArticleAuditRepository auditRepository;
    private final ArticleMapper articleMapper;
    private final CacheService cacheService;
    private final Optional<ArticleEventPublisher> eventPublisher;

    @Transactional
    public Article create(CreateArticleRequest request) {
        Source source = sourceRepository.findByName(request.source())
                .orElseGet(() -> sourceRepository.save(Source.builder()
                        .name(request.source())
                        .build()));

        Article article = Article.builder()
                .id(new ArticleId(UUID.randomUUID(), request.publicationTimestamp()))
                .headline(request.headline())
                .summary(request.summary())
                .body(request.body())
                .source(source)
                .category(request.category())
                .status(ArticleStatus.PUBLISHED)
                .build();

        if (request.tags() != null) {
            for (String tagName : request.tags()) {
                Tag tag = tagRepository.findByName(tagName)
                        .orElseGet(() -> tagRepository.save(Tag.builder().name(tagName).build()));
                article.addTag(tag);
            }
        }

        Article saved = articleRepository.save(article);

        auditRepository.save(ArticleAudit.builder()
                .articleId(saved.getUuid())
                .action("CREATED")
                .build());

        cacheService.evictByPrefix("list:");
        cacheService.put(cacheService.articleKey(saved.getUuid()), articleMapper.toResponse(saved), CacheService.ARTICLE_TTL);

        eventPublisher.ifPresent(pub -> pub.articleCreated(saved));

        log.info("Article created: id={}, headline={}", saved.getUuid(), saved.getHeadline());
        return saved;
    }

    @Transactional(readOnly = true)
    public ArticleResponse getById(UUID articleId) {
        String cacheKey = cacheService.articleKey(articleId);
        return cacheService.getOrComputeWithStale(cacheKey, ArticleResponse.class, () -> {
            Article article = articleRepository.findByArticleUuid(articleId)
                    .orElseThrow(() -> new ArticleNotFoundException(articleId));
            return articleMapper.toResponse(article);
        }, CacheService.ARTICLE_TTL);
    }

    @Transactional(readOnly = true)
    public Page<ArticleSummaryResponse> list(Specification<Article> spec, Pageable pageable) {
        return articleRepository.findAll(spec, pageable).map(articleMapper::toSummaryResponse);
    }

    public record CursorResult<T>(List<T> items, UUID nextId, Instant nextPubTs, boolean hasMore) {
    }

    @Transactional(readOnly = true)
    public CursorResult<ArticleSummaryResponse> listByCursor(
            UUID afterId,
            Instant afterPubTs,
            int limit) {
        int cappedLimit = Math.min(limit, MAX_CURSOR_LIMIT);
        List<Article> articles = articleRepository.findByCursor(afterId, afterPubTs, cappedLimit + 1);

        boolean hasMore = articles.size() > cappedLimit;
        if (hasMore) {
            articles = articles.subList(0, cappedLimit);
        }

        List<ArticleSummaryResponse> items = articleMapper.toSummaryResponses(articles);

        UUID nextId = null;
        Instant nextPubTs = null;
        if (!items.isEmpty()) {
            ArticleSummaryResponse last = items.get(items.size() - 1);
            nextId = last.id();
            nextPubTs = last.publicationTimestamp();
        }

        return new CursorResult<>(items, nextId, nextPubTs, hasMore);
    }

    @Transactional(readOnly = true)
    public List<ArticleSummaryResponse> search(String query, int limit) {
        return articleMapper.toSummaryResponses(articleRepository.fullTextSearch(query, limit));
    }

    @Transactional
    public void softDelete(UUID articleId) {
        Article article = articleRepository.findByArticleUuid(articleId)
                .orElseThrow(() -> new ArticleNotFoundException(articleId));

        article.setStatus(ArticleStatus.RETRACTED);
        articleRepository.save(article);

        auditRepository.save(ArticleAudit.builder()
                .articleId(articleId)
                .action("RETRACTED")
                .build());

        cacheService.evict(cacheService.articleKey(articleId));
        cacheService.evictByPrefix("list:");

        eventPublisher.ifPresent(pub -> pub.articleDeleted(article, "RETRACTED"));

        log.info("Article retracted: id={}", articleId);
    }

    @Transactional(readOnly = true)
    public Page<Article> findByDateRange(Instant from, Instant to, Pageable pageable) {
        Specification<Article> spec = (root, query, cb) -> cb.between(
                root.get("id").get("publicationTimestamp"), from, to);
        return articleRepository.findAll(spec, pageable);
    }
}