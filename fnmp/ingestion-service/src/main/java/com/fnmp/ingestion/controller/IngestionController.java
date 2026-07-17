package com.fnmp.ingestion.controller;

import com.fnmp.common.dto.CreateArticleRequest;
import com.fnmp.common.event.ArticleCreatedEvent;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/articles")
@RequiredArgsConstructor
@Slf4j
public class IngestionController {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @PostMapping
    public ResponseEntity<Map<String, String>> ingest(@Valid @RequestBody CreateArticleRequest request) {
        UUID articleId = UUID.randomUUID();
        Instant now = Instant.now();

        var event = new ArticleCreatedEvent(
                articleId,
                request.publicationTimestamp(),
                request.headline(),
                request.summary(),
                request.body(),
                request.source(),
                request.category() != null ? request.category().name() : null,
                request.tags(),
                now);

        kafkaTemplate.send(ArticleCreatedEvent.TOPIC, articleId.toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish article event: id={}", articleId, ex);
                    } else {
                        log.info("Published article event: id={}, offset={}",
                                articleId, result.getRecordMetadata().offset());
                    }
                });

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(Map.of(
                        "id", articleId.toString(),
                        "status", "ACCEPTED",
                        "message", "Article accepted for processing"));
    }
}