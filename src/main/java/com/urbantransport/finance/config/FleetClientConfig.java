package com.urbantransport.finance.config;

import com.urbantransport.finance.exception.FleetUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Slf4j
@Configuration
public class FleetClientConfig {

    @Bean
    public RestClient fleetRestClient(FleetClientProperties properties) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            log.warn("app.fleet.api-key vacío: fleet rechazará las llamadas internas (defina INTERNAL_API_KEY)");
        }

        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    public CircuitBreaker fleetCircuitBreaker(FleetClientProperties properties) {
        FleetClientProperties.CircuitBreakerProperties settings = properties.circuitBreaker();
        CircuitBreaker circuitBreaker = CircuitBreaker.of("fleet", CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(settings.slidingWindowSize())
                .minimumNumberOfCalls(settings.minimumCalls())
                .failureRateThreshold(settings.failureRateThreshold())
                .waitDurationInOpenState(settings.waitInOpenState())
                .recordExceptions(FleetUnavailableException.class)
                .build());
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> log.warn("Circuit breaker de fleet: {}", event.getStateTransition()));
        return circuitBreaker;
    }
}
