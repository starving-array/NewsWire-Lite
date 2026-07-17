package com.fnmp.article.exception;

import com.fnmp.article.config.TestSecurityConfig;
import com.fnmp.common.dto.CreateArticleRequest;
import com.fnmp.common.domain.ArticleCategory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestSecurityConfig.class)
@Testcontainers
@ActiveProfiles("test")
class ValidationExceptionTest {

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

    @Test
    void validation_shouldReturn400WithRfc7807_whenHeadlineIsBlank() {
        CreateArticleRequest invalid = new CreateArticleRequest(
                "", null, null, "Reuters", Instant.now(), null, null);

        var response = restTemplate.postForEntity("/api/v1/articles", invalid, ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getType()).isEqualTo(URI.create("https://fnmp.dev/errors/validation-error"));
        assertThat(body.getTitle()).isEqualTo("Validation Failed");
        assertThat(body.getDetail()).contains("headline");
        assertThat(body.getStatus()).isEqualTo(400);
    }

    @Test
    void validation_shouldReturn400WithRfc7807_whenSourceIsBlank() {
        CreateArticleRequest invalid = new CreateArticleRequest(
                "Headline", null, null, "", Instant.now(), null, null);

        var response = restTemplate.postForEntity("/api/v1/articles", invalid, ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getDetail()).contains("source");
    }

    @Test
    void validation_shouldReturn400WithRfc7807_whenPubTimestampIsNull() {
        CreateArticleRequest invalid = new CreateArticleRequest(
                "Headline", null, null, "Reuters", null, null, null);

        var response = restTemplate.postForEntity("/api/v1/articles", invalid, ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getDetail()).contains("publicationTimestamp");
    }

    @Test
    void validation_shouldReturn400WithRfc7807_whenHeadlineExceedsMaxLength() {
        String longHeadline = "x".repeat(513);
        CreateArticleRequest invalid = new CreateArticleRequest(
                longHeadline, null, null, "Reuters", Instant.now(), null, null);

        var response = restTemplate.postForEntity("/api/v1/articles", invalid, ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getDetail()).contains("headline");
    }

    @Test
    void notFound_shouldReturn404WithRfc7807() {
        UUID missingId = UUID.randomUUID();

        var response = restTemplate.getForEntity("/api/v1/articles/" + missingId, ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ProblemDetail body = response.getBody();
        assertThat(body.getType()).isEqualTo(URI.create("https://fnmp.dev/errors/article-not-found"));
        assertThat(body.getTitle()).isEqualTo("Article Not Found");
        assertThat(body.getDetail()).contains(missingId.toString());
    }

    @Test
    void notFound_shouldReturn404ForDelete_whenArticleMissing() {
        UUID missingId = UUID.randomUUID();

        var response = restTemplate.exchange(
                org.springframework.http.RequestEntity.delete(URI.create("/api/v1/articles/" + missingId)).build(),
                ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getType()).isEqualTo(URI.create("https://fnmp.dev/errors/article-not-found"));
    }

    @Test
    void invalidEnum_shouldReturn400WithRfc7807() {
        var response = restTemplate.getForEntity(
                "/api/v1/articles?category=INVALID_CATEGORY", ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getType()).isEqualTo(URI.create("https://fnmp.dev/errors/invalid-parameter"));
    }

    @Test
    void unknownRoute_shouldReturn404() {
        var response = restTemplate.getForEntity("/api/v1/nonexistent", ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void unknownRoute_shouldReturn404WithRfc7807Shape() {
        var response = restTemplate.getForEntity("/api/v1/nonexistent", ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getType()).isEqualTo(URI.create("https://fnmp.dev/errors/not-found"));
        assertThat(response.getBody().getTitle()).isEqualTo("Resource Not Found");
    }

    @Test
    void internalError_shouldReturn400WithRfc7807() {
        // Invalid UUID format triggers MethodArgumentTypeMismatchException -> 400
        var response = restTemplate.getForEntity("/api/v1/articles/not-a-uuid", ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getType()).isEqualTo(URI.create("https://fnmp.dev/errors/invalid-parameter"));
        assertThat(response.getBody().getTitle()).isEqualTo("Invalid Parameter");
    }

    @Test
    void validRequest_shouldReturn201() {
        CreateArticleRequest valid = new CreateArticleRequest(
                "Fed holds rates steady",
                "Summary text",
                null,
                "Bloomberg",
                Instant.now(),
                ArticleCategory.MONETARY_POLICY,
                List.of("fed"));

        var response = restTemplate.postForEntity("/api/v1/articles", valid, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("id")).isNotNull();
    }
}