package com.fnmp.common.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String ARTICLE_CREATED = "article.created";
    public static final String ARTICLE_DELETED = "article.deleted";
    public static final String ARTICLE_CREATED_DLQ = "article.created.DLQ";

    @Bean
    public NewTopic articleCreatedTopic() {
        return TopicBuilder.name(ARTICLE_CREATED)
                .partitions(12)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic articleDeletedTopic() {
        return TopicBuilder.name(ARTICLE_DELETED)
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic articleCreatedDlq() {
        return TopicBuilder.name(ARTICLE_CREATED_DLQ)
                .partitions(3)
                .replicas(1)
                .build();
    }
}