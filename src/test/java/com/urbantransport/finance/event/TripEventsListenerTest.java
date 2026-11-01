package com.urbantransport.finance.event;

import com.urbantransport.finance.service.TripDepreciationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TripEventsListenerTest {

    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TRIP_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID BUS_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID BRANCH_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final Instant ARRIVAL = Instant.parse("2026-10-20T18:30:00Z");

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock
    private TripDepreciationService tripDepreciationService;

    private TripEventsListener listener;

    @BeforeEach
    void setUp() {
        listener = new TripEventsListener(jsonMapper, tripDepreciationService);
    }

    @Test
    void tripCompleted_convertsPayloadIgnoringExtraFields() {
        Map<String, Object> payload = completedPayload(120.5);
        payload.put("type", "PRIVATE");
        payload.put("rentalId", null);
        payload.put("driverPay", 150.0);
        payload.put("destinationBranchId", "55555555-5555-5555-5555-555555555555");

        listener.onTripEvent(message(EventTypes.TRIP_COMPLETED, payload));

        verify(tripDepreciationService).applyTripCompleted(EVENT_ID, EventTypes.TRIP_COMPLETED,
                new TripCompletedPayload(TRIP_ID, BUS_ID, BRANCH_ID, ARRIVAL, new BigDecimal("120.5")));
    }

    @Test
    void tripCompletedWithIntegerKm_isConverted() {
        listener.onTripEvent(message(EventTypes.TRIP_COMPLETED, completedPayload(80)));

        verify(tripDepreciationService).applyTripCompleted(EVENT_ID, EventTypes.TRIP_COMPLETED,
                new TripCompletedPayload(TRIP_ID, BUS_ID, BRANCH_ID, ARRIVAL, new BigDecimal("80")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"TripScheduled", "TripUpdated", "TripDeleted", "TripStarted", "PrivateTripRejected"})
    void otherTripEvent_isIgnored(String eventType) {
        listener.onTripEvent(message(eventType, completedPayload(120.5)));

        verifyNoInteractions(tripDepreciationService);
    }

    @Test
    void eventWithoutType_isIgnored() {
        listener.onTripEvent("{\"eventId\":\"%s\",\"payload\":{}}".formatted(EVENT_ID));

        verifyNoInteractions(tripDepreciationService);
    }

    @Test
    void malformedMessage_throwsSoItGoesToRetryAndDlt() {
        assertThrows(JacksonException.class, () -> listener.onTripEvent("{no es json"));

        verifyNoInteractions(tripDepreciationService);
    }

    private static Map<String, Object> completedPayload(Number km) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("tripId", TRIP_ID.toString());
        payload.put("busId", BUS_ID.toString());
        payload.put("originBranchId", BRANCH_ID.toString());
        payload.put("actualArrivalAt", ARRIVAL.toString());
        payload.put("kmTraveled", km);
        return payload;
    }

    private String message(String eventType, Map<String, Object> payload) {
        return jsonMapper.writeValueAsString(Map.of(
                "eventId", EVENT_ID.toString(),
                "eventType", eventType,
                "occurredAt", "2026-10-29T12:00:00Z",
                "aggregateId", TRIP_ID.toString(),
                "version", 1,
                "payload", payload));
    }
}
