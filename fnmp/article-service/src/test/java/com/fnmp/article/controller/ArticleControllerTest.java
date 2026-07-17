package com.fnmp.article.controller;

import com.fnmp.common.dto.CreateArticleRequest;
import com.fnmp.article.dto.ArticleResponse;
import com.fnmp.article.dto.PagedResponse;
import com.fnmp.article.dto.ArticleSummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.fnmp.article.config.TestSecurityConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestSecurityConfig.class)
@Testcontainers
@ActiveProfiles("test")
class ArticleControllerTest {

    @MockBean
    private StringRedisTemplate redisTemplate;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("fnmp")
            .withUsername("fnmp")
            .withPassword("fnmp");

    @Autowired
    private TestRestTemplate restTemplate;

    private CreateArticleRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = new CreateArticleRequest(
                "Fed holds rates steady in July meeting",
                "The Federal Reserve kept interest rates unchanged...",
                "Detailed body content about the Fed decision...",
                "Reuters",
                Instant.now(),
                com.fnmp.common.domain.ArticleCategory.MONETARY_POLICY,
                List.of("fed", "interest-rates")
        );
    }

    @Test
    void shouldCreateArticle() {
        ResponseEntity<ArticleResponse> response = restTemplate.postForEntity(
                "/api/v1/articles", validRequest, ArticleResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isNotNull();
        assertThat(response.getBody().headline()).isEqualTo("Fed holds rates steady in July meeting");
        assertThat(response.getBody().source()).isEqualTo("Reuters");
        assertThat(response.getBody().status()).isEqualTo(com.fnmp.common.domain.ArticleStatus.PUBLISHED);
        assertThat(response.getBody().tags()).containsExactlyInAnyOrder("fed", "interest-rates");
    }

    @Test
    void shouldGetArticleById() {
        ResponseEntity<ArticleResponse> created = restTemplate.postForEntity(
                "/api/v1/articles", validRequest, ArticleResponse.class);
        UUID id = created.getBody().id();

        ResponseEntity<ArticleResponse> response = restTemplate.getForEntity(
                "/api/v1/articles/" + id, ArticleResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().id()).isEqualTo(id);
        assertThat(response.getBody().body()).isEqualTo("Detailed body content about the Fed decision...");
    }

    @Test
    void shouldReturn404ForNonExistentArticle() {
        ResponseEntity<ProblemDetail> response = restTemplate.getForEntity(
                "/api/v1/articles/" + UUID.randomUUID(), ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void shouldListArticles() {
        restTemplate.postForEntity("/api/v1/articles", validRequest, ArticleResponse.class);

        ResponseEntity<PagedResponse> response = restTemplate.getForEntity(
                "/api/v1/articles?size=10&sort=id.publicationTimestamp,desc", PagedResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void shouldSearchArticles() {
        restTemplate.postForEntity("/api/v1/articles", validRequest, ArticleResponse.class);

        ResponseEntity<ArticleSummaryResponse[]> response = restTemplate.getForEntity(
                "/api/v1/articles/search?q=Federal+Reserve&limit=10", ArticleSummaryResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        if (response.getBody() != null) {
            assertThat(response.getBody()).isNotEmpty();
        }
    }

    @Test
    void shouldSoftDeleteArticle() {
        ResponseEntity<ArticleResponse> created = restTemplate.postForEntity(
                "/api/v1/articles", validRequest, ArticleResponse.class);
        UUID id = created.getBody().id();

        restTemplate.delete("/api/v1/articles/" + id);

        ResponseEntity<ArticleResponse> after = restTemplate.getForEntity(
                "/api/v1/articles/" + id, ArticleResponse.class);

        assertThat(after.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(after.getBody().status()).isEqualTo(com.fnmp.common.domain.ArticleStatus.RETRACTED);
    }

    @Test
    void shouldRejectInvalidRequest() {
        CreateArticleRequest invalid = new CreateArticleRequest(
                "", null, null, "Reuters", Instant.now(), null, null);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/articles", invalid, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}