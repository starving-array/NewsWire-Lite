package com.fnmp.search.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fnmp.common.event.ArticleCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class OpenSearchService {

    private static final String INDEX_NAME = "articles";

    private final OpenSearchClient client;

    public OpenSearchService(OpenSearchClient client) {
        this.client = client;
    }

    public void indexArticle(ArticleCreatedEvent event) {
        try {
            var doc = new ArticleDoc(
                    event.id(),
                    event.headline(),
                    event.summary(),
                    event.body(),
                    event.source(),
                    event.category(),
                    event.tags(),
                    event.publicationTimestamp(),
                    event.createdAt());

            var request = IndexRequest.of(i -> i
                    .index(INDEX_NAME)
                    .id(event.id().toString())
                    .document(doc)
                    .refresh(Refresh.WaitFor));

            var response = client.index(request);
            log.debug("Indexed article in OpenSearch: id={}, result={}", event.id(), response.result());
        } catch (IOException e) {
            log.error("Failed to index article in OpenSearch: id={}", event.id(), e);
            throw new RuntimeException("OpenSearch indexing failed", e);
        }
    }

    public List<ArticleDoc> search(String query, int limit) {
        try {
            var searchRequest = SearchRequest.of(s -> s
                    .index(INDEX_NAME)
                    .size(limit)
                    .query(q -> q
                            .multiMatch(m -> m
                                    .fields("headline^3", "summary^2", "body")
                                    .query(query))));

            SearchResponse<ArticleDoc> response = client.search(searchRequest, ArticleDoc.class);

            return response.hits().hits().stream()
                    .map(Hit::source)
                    .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        } catch (IOException e) {
            log.error("OpenSearch search failed for query: {}", query, e);
            throw new RuntimeException("OpenSearch search failed", e);
        }
    }

    public void deleteArticle(UUID articleId) {
        try {
            client.delete(d -> d.index(INDEX_NAME).id(articleId.toString()));
        } catch (IOException e) {
            log.error("Failed to delete article from OpenSearch: id={}", articleId, e);
        }
    }

    public record ArticleDoc(
            UUID id,
            String headline,
            String summary,
            String body,
            String source,
            String category,
            List<String> tags,
            Instant publicationTimestamp,
            Instant createdAt) {
    }
}