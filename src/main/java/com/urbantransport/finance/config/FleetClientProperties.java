package com.urbantransport.finance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.fleet")
public record FleetClientProperties(
        String baseUrl,
        String apiKey,
        Duration connectTimeout,
        Duration readTimeout,
        CircuitBreakerProperties circuitBreaker) {

    public record CircuitBreakerProperties(
            int slidingWindowSize,
            int minimumCalls,
            float failureRateThreshold,
            Duration waitInOpenState) {
    }
}
