package com.fnmp.search.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.Request;
import org.opensearch.client.RestClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OpenSearchIndexInitializer {

    private static final String INDEX_NAME = "articles";
    private static final String INDEX_MAPPING = """
            {
              "settings": {
                "number_of_shards": 3,
                "number_of_replicas": 1,
                "analysis": {
                  "analyzer": {
                    "english_analyzer": {
                      "type": "english"
                    }
                  }
                }
              },
              "mappings": {
                "properties": {
                  "headline": { "type": "text", "analyzer": "english", "boost": 3.0 },
                  "summary": { "type": "text", "analyzer": "english", "boost": 2.0 },
                  "body": { "type": "text", "analyzer": "english" },
                  "source": { "type": "keyword" },
                  "category": { "type": "keyword" },
                  "tags": { "type": "keyword" },
                  "publicationTimestamp": { "type": "date" },
                  "createdAt": { "type": "date" }
                }
              }
            }
            """;

    private final RestClient restClient;

    @PostConstruct
    public void init() {
        try {
            var request = new Request("HEAD", "/" + INDEX_NAME);
            var existsResponse = restClient.performRequest(request);
            if (existsResponse.getStatusLine().getStatusCode() == 200) {
                log.info("OpenSearch index already exists: {}", INDEX_NAME);
                return;
            }
        } catch (Exception e) {
            log.info("OpenSearch index not found, creating: {}", INDEX_NAME);
        }

        try {
            var request = new Request("PUT", "/" + INDEX_NAME);
            request.setJsonEntity(INDEX_MAPPING);
            var response = restClient.performRequest(request);
            log.info("Created OpenSearch index: {}, status={}", INDEX_NAME, response.getStatusLine().getStatusCode());
        } catch (Exception e) {
            log.warn("Failed to initialize OpenSearch index (will retry later): {}", e.getMessage());
        }
    }
}