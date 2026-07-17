package com.fnmp.article.specification;

import com.fnmp.article.domain.Article;
import com.fnmp.article.domain.ArticleTag;
import com.fnmp.common.domain.ArticleCategory;
import com.fnmp.common.domain.ArticleStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class ArticleSpecifications {

    private ArticleSpecifications() {
    }

    public static Specification<Article> build(
            String source,
            ArticleCategory category,
            ArticleStatus status,
            String tag,
            Instant dateFrom,
            Instant dateTo) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (source != null && !source.isBlank()) {
                predicates.add(cb.equal(root.get("source").get("name"), source));
            }
            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (tag != null && !tag.isBlank()) {
                Join<Article, ArticleTag> tagJoin = root.join("tags");
                predicates.add(cb.equal(tagJoin.get("tag").get("name"), tag));
            }
            if (dateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("id").get("publicationTimestamp"), dateFrom));
            }
            if (dateTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("id").get("publicationTimestamp"), dateTo));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}