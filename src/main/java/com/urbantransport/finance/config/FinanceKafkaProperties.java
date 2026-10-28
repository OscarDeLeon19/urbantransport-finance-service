package com.urbantransport.finance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.kafka.topic")
public record FinanceKafkaProperties(String financeEvents, String tripEvents) {
}
