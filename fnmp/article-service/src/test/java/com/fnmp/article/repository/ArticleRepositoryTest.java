package com.fnmp.article.repository;

import com.fnmp.article.domain.*;
import com.fnmp.common.domain.ArticleCategory;
import com.fnmp.common.domain.ArticleStatus;
import com.fnmp.common.domain.ReliabilityTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ActiveProfiles("test")
class ArticleRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("fnmp")
            .withUsername("fnmp")
            .withPassword("fnmp");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
    }

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private ArticleAuditRepository auditRepository;

    private Source source;
    private Tag tag;
    private Article article;

    @BeforeEach
    void setUp() {
        source = sourceRepository.save(Source.builder()
                .name("Reuters")
                .reliabilityTier(ReliabilityTier.VERIFIED)
                .build());

        tag = tagRepository.save(Tag.builder()
                .name("interest-rates")
                .build());

        ArticleId articleId = new ArticleId(UUID.randomUUID(), Instant.now().truncatedTo(ChronoUnit.SECONDS));
        article = Article.builder()
                .id(articleId)
                .headline("Fed holds rates steady in July meeting")
                .summary("The Federal Reserve kept interest rates unchanged...")
                .body("Detailed body content about the Fed decision...")
                .source(source)
                .status(ArticleStatus.PUBLISHED)
                .category(ArticleCategory.MONETARY_POLICY)
                .build();
    }

    @Test
    @Transactional
    void shouldSaveAndFindArticle() {
        Article saved = articleRepository.save(article);
        assertThat(saved).isNotNull();

        Optional<Article> found = articleRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getHeadline()).isEqualTo("Fed holds rates steady in July meeting");
    }

    @Test
    @Transactional
    void shouldFindByArticleUuid() {
        Article saved = articleRepository.save(article);

        Optional<Article> found = articleRepository.findByArticleUuid(saved.getUuid());
        assertThat(found).isPresent();
    }

    @Test
    @Transactional
    void shouldFindByDateRange() {
        articleRepository.save(article);

        Instant from = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant to = Instant.now().plus(1, ChronoUnit.DAYS);

        List<Article> articles = articleRepository.findByDateRange(from, to);
        assertThat(articles).isNotEmpty();
    }

    @Test
    @Transactional
    void shouldPerformFullTextSearch() {
        articleRepository.save(article);

        List<Article> results = articleRepository.fullTextSearch("Federal Reserve", 10);
        assertThat(results).isNotEmpty();
    }

    @Test
    @Transactional
    void shouldSaveAndFindSource() {
        Optional<Source> found = sourceRepository.findByName("Reuters");
        assertThat(found).isPresent();
        assertThat(found.get().getReliabilityTier()).isEqualTo(ReliabilityTier.VERIFIED);
    }

    @Test
    @Transactional
    void shouldSaveAndFindTag() {
        Optional<Tag> found = tagRepository.findByName("interest-rates");
        assertThat(found).isPresent();
    }

    @Test
    @Transactional
    void shouldSaveArticleAudit() {
        Article saved = articleRepository.save(article);

        ArticleAudit audit = ArticleAudit.builder()
                .articleId(saved.getUuid())
                .action("CREATED")
                .diff("{\"headline\":\"Fed holds rates steady in July meeting\"}")
                .build();
        ArticleAudit savedAudit = auditRepository.save(audit);

        assertThat(savedAudit.getId()).isNotNull();
        assertThat(savedAudit.getAction()).isEqualTo("CREATED");

        List<ArticleAudit> audits = auditRepository.findByArticleIdOrderByOccurredAtDesc(saved.getUuid());
        assertThat(audits).hasSize(1);
    }
}