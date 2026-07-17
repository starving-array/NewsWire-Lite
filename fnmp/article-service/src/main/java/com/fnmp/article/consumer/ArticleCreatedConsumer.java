package com.fnmp.article.consumer;

import com.fnmp.article.domain.*;
import com.fnmp.article.repository.ArticleAuditRepository;
import com.fnmp.article.repository.ArticleRepository;
import com.fnmp.article.repository.SourceRepository;
import com.fnmp.article.repository.TagRepository;
import com.fnmp.common.domain.ArticleCategory;
import com.fnmp.common.domain.ArticleStatus;
import com.fnmp.common.domain.ReliabilityTier;
import com.fnmp.common.event.ArticleCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ArticleCreatedConsumer {

    private final ArticleRepository articleRepository;
    private final SourceRepository sourceRepository;
    private final TagRepository tagRepository;
    private final ArticleAuditRepository auditRepository;

    @RetryableTopic(
            attempts = "3",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE)
    @KafkaListener(topics = "article.created", groupId = "article-service-persister")
    @Transactional
    public void onArticleCreated(ArticleCreatedEvent event,
                                  @Header(KafkaHeaders.RECEIVED_KEY) String key) {
        log.info("Received ArticleCreatedEvent: id={}, headline={}", event.id(), event.headline());

        if (articleRepository.findByArticleUuid(event.id()).isPresent()) {
            log.warn("Article already exists, skipping: id={}", event.id());
            return;
        }

        Source source = sourceRepository.findByName(event.source())
                .orElseGet(() -> sourceRepository.save(Source.builder()
                        .name(event.source())
                        .reliabilityTier(ReliabilityTier.UNVERIFIED)
                        .build()));

        Article article = Article.builder()
                .id(new ArticleId(event.id(), event.publicationTimestamp()))
                .headline(event.headline())
                .summary(event.summary())
                .body(event.body())
                .source(source)
                .category(resolveCategory(event.category()))
                .status(ArticleStatus.PUBLISHED)
                .build();

        if (event.tags() != null) {
            for (String tagName : event.tags()) {
                Tag tag = tagRepository.findByName(tagName)
                        .orElseGet(() -> tagRepository.save(Tag.builder().name(tagName).build()));
                article.addTag(tag);
            }
        }

        articleRepository.save(article);

        auditRepository.save(ArticleAudit.builder()
                .articleId(event.id())
                .action("CREATED")
                .build());

        log.info("Article persisted from event: id={}", event.id());
    }

    @DltHandler
    public void onDlt(ArticleCreatedEvent event, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("Article event routed to DLQ: id={}, topic={}", event.id(), topic);
    }

    private ArticleCategory resolveCategory(String category) {
        if (category == null || category.isBlank()) return null;
        try {
            return ArticleCategory.valueOf(category.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}