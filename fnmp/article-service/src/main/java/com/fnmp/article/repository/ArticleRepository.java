package com.fnmp.article.repository;

import com.fnmp.article.domain.Article;
import com.fnmp.article.domain.ArticleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ArticleRepository extends JpaRepository<Article, ArticleId>, JpaSpecificationExecutor<Article> {

    @Query("SELECT a FROM Article a WHERE a.id.id = :articleId")
    Optional<Article> findByArticleUuid(@Param("articleId") UUID articleId);

    @Query("SELECT a FROM Article a WHERE a.id.id = :articleId AND a.id.publicationTimestamp = :pubTs")
    Optional<Article> findByArticleId(@Param("articleId") UUID articleId, @Param("pubTs") Instant pubTs);

    @Query("SELECT a FROM Article a WHERE a.id.id IN :ids")
    List<Article> findByArticleUuids(@Param("ids") List<UUID> ids);

    @Query(value = """
        SELECT a.* FROM article a
        WHERE a.publication_timestamp >= :from AND a.publication_timestamp < :to
        ORDER BY a.publication_timestamp DESC
        """,
        countQuery = """
        SELECT count(*) FROM article a
        WHERE a.publication_timestamp >= :from AND a.publication_timestamp < :to
        """,
        nativeQuery = true)
    List<Article> findByDateRange(@Param("from") Instant from, @Param("to") Instant to);

    @Query(value = """
        SELECT a.* FROM article a
        WHERE a.search_vector @@ plainto_tsquery('english', :query)
        ORDER BY ts_rank(a.search_vector, plainto_tsquery('english', :query)) DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Article> fullTextSearch(@Param("query") String query, @Param("limit") int limit);

    @Query(value = """
        SELECT a.* FROM article a
        WHERE (:afterId IS NULL OR
              a.publication_timestamp < :afterPubTs OR
              (a.publication_timestamp = :afterPubTs AND a.id < CAST(:afterId AS uuid)))
        ORDER BY a.publication_timestamp DESC, a.id DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Article> findByCursor(
            @Param("afterId") UUID afterId,
            @Param("afterPubTs") Instant afterPubTs,
            @Param("limit") int limit);
}