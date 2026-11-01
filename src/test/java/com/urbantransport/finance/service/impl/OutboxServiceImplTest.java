package com.urbantransport.finance.service.impl;

import com.urbantransport.finance.config.FinanceKafkaProperties;
import com.urbantransport.finance.domain.OutboxEventEntity;
import com.urbantransport.finance.event.EventTypes;
import com.urbantransport.finance.repository.OutboxEventRepository;
import com.urbantransport.finance.web.CorrelationIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxServiceImplTest {

    private static final UUID AGGREGATE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String TOPIC = "finance-events";
    private static final String CORRELATION_ID = "corr-123";

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Captor
    private ArgumentCaptor<OutboxEventEntity> outboxCaptor;

    private OutboxServiceImpl outboxService;

    @BeforeEach
    void setUp() {
        outboxService = new OutboxServiceImpl(outboxEventRepository, jsonMapper,
                new FinanceKafkaProperties(TOPIC, "trip-events"));
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void record_savesEnvelopeWithEventDataAndTopic() {
        MDC.put(CorrelationIdFilter.MDC_KEY, CORRELATION_ID);

        outboxService.record(EventTypes.AGGREGATE_WORKSHOP_EXPENSE, AGGREGATE_ID,
                EventTypes.WORKSHOP_EXPENSE_REGISTERED, Map.of("total", "1350.00"));

        verify(outboxEventRepository).save(outboxCaptor.capture());
        OutboxEventEntity saved = outboxCaptor.getValue();
        assertNotNull(saved.getId());
        assertEquals(EventTypes.AGGREGATE_WORKSHOP_EXPENSE, saved.getAggregateType());
        assertEquals(AGGREGATE_ID, saved.getAggregateId());
        assertEquals(EventTypes.WORKSHOP_EXPENSE_REGISTERED, saved.getEventType());
        assertEquals(TOPIC, saved.getTopic());
        assertNull(saved.getPublishedAt());
        assertEquals(0, saved.getAttempts());

        JsonNode envelope = jsonMapper.readTree(saved.getPayload());
        assertEquals(saved.getId().toString(), envelope.get("eventId").asString());
        assertEquals(EventTypes.WORKSHOP_EXPENSE_REGISTERED, envelope.get("eventType").asString());
        assertEquals(AGGREGATE_ID.toString(), envelope.get("aggregateId").asString());
        assertEquals(1, envelope.get("version").asInt());
        assertEquals(CORRELATION_ID, envelope.get("correlationId").asString());
        assertFalse(envelope.get("occurredAt").isNull());
        assertEquals("1350.00", envelope.get("payload").get("total").asString());
    }

    @Test
    void record_withoutCorrelationInContext_generatesOne() {
        outboxService.record(EventTypes.AGGREGATE_DEPRECIATION_RATE, AGGREGATE_ID,
                EventTypes.DEPRECIATION_RATE_CHANGED, Map.of());

        verify(outboxEventRepository).save(outboxCaptor.capture());
        JsonNode envelope = jsonMapper.readTree(outboxCaptor.getValue().getPayload());
        String correlationId = envelope.get("correlationId").asString();
        assertDoesNotThrow(() -> UUID.fromString(correlationId));
    }
}
