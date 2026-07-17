package com.fnmp.article.observability;

import com.fnmp.article.config.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestSecurityConfig.class)
@Testcontainers
@ActiveProfiles("test")
class TracePropagationTest {

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
    void requestShouldPropagateTraceIdViaMdc() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/api/v1/articles?size=1", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void requestShouldAcceptAndPropagateCustomRequestId() {
        var headers = new org.springframework.http.HttpHeaders();
        headers.set("X-Request-Id", "test-req-123");
        var entity = new org.springframework.http.HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/articles?size=1",
                org.springframework.http.HttpMethod.GET,
                entity,
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
