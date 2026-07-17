package com.fnmp.common.config;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(Tracer.class)
@RequiredArgsConstructor
public class TracingConfig {

    private final Tracer tracer;

    @Bean
    public Filter mdcTracingFilter() {
        return (request, response, chain) -> {
            try {
                var span = tracer.currentSpan();
                if (span != null) {
                    var context = span.context();
                    MDC.put("traceId", context.traceId());
                    MDC.put("spanId", context.spanId());
                }
                if (request instanceof HttpServletRequest httpReq) {
                    String userId = httpReq.getHeader("X-User-Id");
                    if (userId != null) {
                        MDC.put("userId", userId);
                    }
                    String requestId = httpReq.getHeader("X-Request-Id");
                    if (requestId != null) {
                        MDC.put("requestId", requestId);
                    }
                }
                chain.doFilter(request, response);
            } finally {
                MDC.clear();
            }
        };
    }
}