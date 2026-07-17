package com.fnmp.search.controller;

import com.fnmp.search.service.OpenSearchService.ArticleDoc;
import com.fnmp.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/articles")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping("/search")
    public ResponseEntity<List<ArticleDoc>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "20") int limit) {
        int cappedLimit = Math.min(limit, 100);
        List<ArticleDoc> results = searchService.search(q, cappedLimit);
        return ResponseEntity.ok(results);
    }
}