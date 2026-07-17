package com.fnmp.search.consumer;

import com.fnmp.common.event.ArticleCreatedEvent;
import com.fnmp.common.event.ArticleDeletedEvent;
import com.fnmp.search.service.OpenSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ArticleIndexerConsumer {

    private final OpenSearchService openSearchService;

    @KafkaListener(topics = "article.created", groupId = "search-service-indexer")
    public void onArticleCreated(ArticleCreatedEvent event) {
        log.info("Indexing article in OpenSearch: id={}, headline={}", event.id(), event.headline());
        try {
            openSearchService.indexArticle(event);
        } catch (Exception e) {
            log.error("Failed to index article, will retry via DLQ: id={}", event.id(), e);
            throw e;
        }
    }

    @KafkaListener(topics = "article.deleted", groupId = "search-service-indexer")
    public void onArticleDeleted(ArticleDeletedEvent event) {
        log.info("Removing article from OpenSearch: id={}", event.id());
        openSearchService.deleteArticle(event.id());
    }
}