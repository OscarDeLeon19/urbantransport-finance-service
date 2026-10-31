package com.urbantransport.finance.event;

import com.urbantransport.finance.domain.OutboxEventEntity;
import com.urbantransport.finance.repository.OutboxEventRepository;
import org.apache.kafka.common.KafkaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    private static final String TOPIC = "finance-events";

    private final OutboxEventEntity first = event(1);
    private final OutboxEventEntity second = event(2);
    private final OutboxEventEntity third = event(3);

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private CompletableFuture<SendResult<String, String>> pendingSend;

    private OutboxPublisher outboxPublisher;

    @BeforeEach
    void setUp() {
        outboxPublisher = new OutboxPublisher(outboxEventRepository, kafkaTemplate);
    }

    @Test
    void pendingEvents_arePublishedInOrderWithAggregateAsKey() {
        when(outboxEventRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of(first, second));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));

        outboxPublisher.publishPending();

        InOrder inOrder = inOrder(kafkaTemplate, outboxEventRepository);
        inOrder.verify(kafkaTemplate).send(TOPIC, first.getAggregateId().toString(), first.getPayload());
        inOrder.verify(outboxEventRepository).markPublished(eq(first.getId()), any(Instant.class));
        inOrder.verify(kafkaTemplate).send(TOPIC, second.getAggregateId().toString(), second.getPayload());
        inOrder.verify(outboxEventRepository).markPublished(eq(second.getId()), any(Instant.class));
        verify(outboxEventRepository, never()).markFailed(any(), any());
    }

    @Test
    void noPendingEvents_sendsNothing() {
        when(outboxEventRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of());

        outboxPublisher.publishPending();

        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void failedSend_marksFailureAndStopsToKeepOrder() {
        when(outboxEventRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of(first, second, third));
        when(kafkaTemplate.send(TOPIC, first.getAggregateId().toString(), first.getPayload()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(kafkaTemplate.send(TOPIC, second.getAggregateId().toString(), second.getPayload()))
                .thenReturn(CompletableFuture.failedFuture(new KafkaException("broker caido")));

        outboxPublisher.publishPending();

        verify(outboxEventRepository).markPublished(eq(first.getId()), any(Instant.class));
        verify(outboxEventRepository).markFailed(eq(second.getId()), contains("broker caido"));
        verify(outboxEventRepository, never()).markPublished(eq(second.getId()), any(Instant.class));
        verify(kafkaTemplate, never()).send(TOPIC, third.getAggregateId().toString(), third.getPayload());
    }

    @Test
    void sendTimeout_marksFailure() throws Exception {
        when(outboxEventRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of(first));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(pendingSend);
        when(pendingSend.get(5, TimeUnit.SECONDS)).thenThrow(new TimeoutException());

        outboxPublisher.publishPending();

        verify(outboxEventRepository).markFailed(eq(first.getId()), contains("TimeoutException"));
        verify(outboxEventRepository, never()).markPublished(any(), any());
    }

    @Test
    void interruptedSend_stopsWithoutMarkingAndKeepsInterruptFlag() throws Exception {
        when(outboxEventRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of(first, second));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(pendingSend);
        when(pendingSend.get(5, TimeUnit.SECONDS)).thenThrow(new InterruptedException());

        outboxPublisher.publishPending();

        assertTrue(Thread.interrupted());
        verify(outboxEventRepository, never()).markPublished(any(), any());
        verify(outboxEventRepository, never()).markFailed(any(), any());
        verify(kafkaTemplate, never()).send(TOPIC, second.getAggregateId().toString(), second.getPayload());
    }

    private static OutboxEventEntity event(int number) {
        return OutboxEventEntity.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-00000000000" + number))
                .aggregateType(EventTypes.AGGREGATE_WORKSHOP_EXPENSE)
                .aggregateId(UUID.fromString("00000000-0000-0000-0000-00000000010" + number))
                .eventType(EventTypes.WORKSHOP_EXPENSE_REGISTERED)
                .topic(TOPIC)
                .payload("{\"eventType\":\"WorkshopExpenseRegistered\",\"n\":" + number + "}")
                .build();
    }
}
