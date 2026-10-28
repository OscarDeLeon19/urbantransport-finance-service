package com.urbantransport.finance.service.impl;

import com.urbantransport.finance.config.FinanceKafkaProperties;
import com.urbantransport.finance.domain.OutboxEventEntity;
import com.urbantransport.finance.event.EventEnvelope;
import com.urbantransport.finance.repository.OutboxEventRepository;
import com.urbantransport.finance.service.OutboxService;
import com.urbantransport.finance.web.CorrelationIdFilter;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxServiceImpl implements OutboxService {

    private static final int EVENT_VERSION = 1;

    private final OutboxEventRepository outboxEventRepository;
    private final JsonMapper jsonMapper;
    private final FinanceKafkaProperties kafkaProperties;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String aggregateType, UUID aggregateId, String eventType, Object payload) {
        UUID eventId = UUID.randomUUID();
        EventEnvelope envelope = new EventEnvelope(eventId, eventType, Instant.now(), aggregateId,
                EVENT_VERSION, correlationId(), payload);

        outboxEventRepository.save(OutboxEventEntity.builder()
                .id(eventId)
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .topic(kafkaProperties.financeEvents())
                .payload(jsonMapper.writeValueAsString(envelope))
                .build());
    }

    private String correlationId() {
        String current = MDC.get(CorrelationIdFilter.MDC_KEY);
        return current != null ? current : UUID.randomUUID().toString();
    }
}
