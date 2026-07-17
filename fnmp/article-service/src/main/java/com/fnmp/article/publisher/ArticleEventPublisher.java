package com.fnmp.article.publisher;

import com.fnmp.article.domain.Article;
import com.fnmp.common.event.ArticleCreatedEvent;
import com.fnmp.common.event.ArticleDeletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(KafkaTemplate.class)
@RequiredArgsConstructor
@Slf4j
public class ArticleEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void articleCreated(Article article) {
        var event = new ArticleCreatedEvent(
                article.getUuid(),
                article.getPublicationTimestamp(),
                article.getHeadline(),
                article.getSummary(),
                article.getBody(),
                article.getSource().getName(),
                article.getCategory() != null ? article.getCategory().name() : null,
                article.getTags().stream().map(at -> at.getTag().getName()).toList(),
                article.getCreatedAt());

        kafkaTemplate.send(ArticleCreatedEvent.TOPIC, article.getUuid().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish ArticleCreatedEvent: id={}", article.getUuid(), ex);
                    } else {
                        log.debug("Published ArticleCreatedEvent: id={}, offset={}",
                                article.getUuid(), result.getRecordMetadata().offset());
                    }
                });
    }

    public void articleDeleted(Article article, String reason) {
        var event = new ArticleDeletedEvent(article.getUuid(), reason);

        kafkaTemplate.send(ArticleDeletedEvent.TOPIC, article.getUuid().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish ArticleDeletedEvent: id={}", article.getUuid(), ex);
                    }
                });
    }
}