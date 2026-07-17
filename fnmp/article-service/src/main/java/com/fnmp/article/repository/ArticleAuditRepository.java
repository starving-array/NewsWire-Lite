package com.fnmp.article.repository;

import com.fnmp.article.domain.ArticleAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ArticleAuditRepository extends JpaRepository<ArticleAudit, Long> {

    List<ArticleAudit> findByArticleIdOrderByOccurredAtDesc(UUID articleId);
}