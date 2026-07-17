package com.fnmp.search.service;

import com.fnmp.common.domain.ArticleCategory;
import com.fnmp.common.domain.ArticleStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {

    private final OpenSearchService openSearchService;

    public List<OpenSearchService.ArticleDoc> search(String query, int limit) {
        try {
            log.debug("Searching OpenSearch: q={}, limit={}", query, limit);
            return openSearchService.search(query, limit);
        } catch (Exception e) {
            log.warn("OpenSearch search failed, will rely on PG fallback: {}", e.getMessage());
            return List.of();
        }
    }
}