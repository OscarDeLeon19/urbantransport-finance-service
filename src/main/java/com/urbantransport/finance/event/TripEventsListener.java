package com.urbantransport.finance.event;

import com.urbantransport.finance.service.TripDepreciationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class TripEventsListener {

    private final JsonMapper jsonMapper;
    private final TripDepreciationService tripDepreciationService;

    @KafkaListener(topics = "${app.kafka.topic.trip-events}")
    public void onTripEvent(String message) {
        IncomingEvent event = jsonMapper.readValue(message, IncomingEvent.class);
        String eventType = event.eventType() == null ? "" : event.eventType();

        switch (eventType) {
            case EventTypes.TRIP_COMPLETED -> tripDepreciationService.applyTripCompleted(event.eventId(), eventType,
                    jsonMapper.convertValue(event.payload(), TripCompletedPayload.class));
            default -> log.debug("Evento {} ignorado", eventType);
        }
    }
}
