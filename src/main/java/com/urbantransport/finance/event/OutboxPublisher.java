package com.urbantransport.finance.event;

import com.urbantransport.finance.domain.OutboxEventEntity;
import com.urbantransport.finance.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelayString = "${app.jobs.outbox.fixed-delay-ms:2000}")
    public void publishPending() {
        List<OutboxEventEntity> pending = outboxEventRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
        for (OutboxEventEntity event : pending) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getAggregateId().toString(), event.getPayload())
                        .get(5, TimeUnit.SECONDS);
                outboxEventRepository.markPublished(event.getId(), Instant.now());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception exception) {
                log.warn("Outbox: no se pudo publicar {} ({}), se reintenta", event.getId(), exception.toString());
                outboxEventRepository.markFailed(event.getId(), exception.toString());
                return;
            }
        }
    }
}
