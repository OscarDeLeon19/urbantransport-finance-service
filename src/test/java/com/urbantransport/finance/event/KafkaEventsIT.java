package com.urbantransport.finance.event;

import com.urbantransport.finance.AbstractIntegrationTest;
import com.urbantransport.finance.domain.TripDepreciationEntity;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class KafkaEventsIT extends AbstractIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ConsumerFactory<String, String> consumerFactory;

    @Autowired
    private JsonMapper jsonMapper;

    @Value("${app.kafka.topic.trip-events}")
    private String tripTopic;

    @Value("${app.kafka.topic.finance-events}")
    private String financeTopic;

    @Test
    void tripCompleted_usesRateInForceAtArrivalAndRecordsEvent() throws Exception {
        Instant arrival = Instant.now().minus(Duration.ofHours(2));
        saveRate("0.5000", arrival.minus(Duration.ofDays(5)));
        UUID rateId = saveRate("0.7500", arrival.minus(Duration.ofDays(1))).getId();
        saveRate("1.0000", arrival.plus(Duration.ofHours(1)));
        UUID tripId = UUID.randomUUID();
        UUID busId = UUID.randomUUID();
        UUID originBranchId = UUID.randomUUID();

        send(tripTopic, tripId, envelope(UUID.randomUUID(), EventTypes.TRIP_COMPLETED, tripId,
                completedPayload(tripId, busId, originBranchId, arrival, 120.5)));

        await().atMost(TIMEOUT).untilAsserted(() -> {
            Optional<TripDepreciationEntity> depreciation = tripDepreciationRepository.findById(tripId);
            assertTrue(depreciation.isPresent());
            assertEquals(busId, depreciation.get().getBusId());
            assertEquals(originBranchId, depreciation.get().getBranchId());
            assertEquals(0, new BigDecimal("120.50").compareTo(depreciation.get().getKm()));
            assertEquals(0, new BigDecimal("0.7500").compareTo(depreciation.get().getRateApplied()));
            assertEquals(0, new BigDecimal("90.38").compareTo(depreciation.get().getAmount()));
            assertNotNull(depreciation.get().getCalculatedAt());
        });
        assertEquals(List.of("DepreciationCalculated"), outboxEventTypes(tripId));
        JsonNode payload = jsonMapper.readTree(outboxPayload(tripId)).get("payload");
        assertEquals(rateId.toString(), payload.get("rateId").asString());
        assertEquals(originBranchId.toString(), payload.get("branchId").asString());
    }

    @Test
    void tripCompletedWithoutRate_isRegisteredWithZero() throws Exception {
        UUID tripId = UUID.randomUUID();

        send(tripTopic, tripId, envelope(UUID.randomUUID(), EventTypes.TRIP_COMPLETED, tripId,
                completedPayload(tripId, UUID.randomUUID(), UUID.randomUUID(), Instant.now(), 80)));

        await().atMost(TIMEOUT).untilAsserted(() -> {
            Optional<TripDepreciationEntity> depreciation = tripDepreciationRepository.findById(tripId);
            assertTrue(depreciation.isPresent());
            assertEquals(0, BigDecimal.ZERO.compareTo(depreciation.get().getRateApplied()));
            assertEquals(0, BigDecimal.ZERO.compareTo(depreciation.get().getAmount()));
        });
        JsonNode payload = jsonMapper.readTree(outboxPayload(tripId)).get("payload");
        assertTrue(payload.get("rateId").isNull());
    }

    @Test
    void duplicatedEventAndSecondEventOfSameTrip_areCalculatedOnce() throws Exception {
        saveRate("0.7500", Instant.now().minus(Duration.ofDays(1)));
        UUID tripId = UUID.randomUUID();
        UUID busId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        UUID firstEventId = UUID.randomUUID();
        UUID secondEventId = UUID.randomUUID();
        String first = envelope(firstEventId, EventTypes.TRIP_COMPLETED, tripId,
                completedPayload(tripId, busId, branchId, Instant.now(), 100));

        send(tripTopic, tripId, first);
        send(tripTopic, tripId, first);
        send(tripTopic, tripId, envelope(secondEventId, EventTypes.TRIP_COMPLETED, tripId,
                completedPayload(tripId, busId, branchId, Instant.now(), 999)));

        await().atMost(TIMEOUT).untilAsserted(() ->
                assertEquals(1, count("SELECT COUNT(*) FROM processed_events WHERE event_id = ?", secondEventId)));
        assertEquals(1, count("SELECT COUNT(*) FROM processed_events WHERE event_id = ?", firstEventId));
        assertEquals(1, count("SELECT COUNT(*) FROM trip_depreciations WHERE trip_id = ?", tripId));
        assertEquals(0, new BigDecimal("75.00").compareTo(tripDepreciationRepository.findById(tripId).orElseThrow()
                .getAmount()));
        assertEquals(List.of("DepreciationCalculated"), outboxEventTypes(tripId));
    }

    @Test
    void otherTripEvents_areIgnored() throws Exception {
        UUID tripId = UUID.randomUUID();
        UUID startedEventId = UUID.randomUUID();
        UUID markerTripId = UUID.randomUUID();

        send(tripTopic, tripId, envelope(startedEventId, "TripStarted", tripId,
                Map.of("tripId", tripId.toString(), "busId", UUID.randomUUID().toString())));
        send(tripTopic, tripId, envelope(UUID.randomUUID(), EventTypes.TRIP_COMPLETED, markerTripId,
                completedPayload(markerTripId, UUID.randomUUID(), UUID.randomUUID(), Instant.now(), 10)));

        await().atMost(TIMEOUT).untilAsserted(() -> assertTrue(tripDepreciationRepository.existsById(markerTripId)));
        assertEquals(0, count("SELECT COUNT(*) FROM processed_events WHERE event_id = ?", startedEventId));
        assertEquals(1, tripDepreciationRepository.count());
    }

    @Test
    void depreciationRateChanged_isPublishedToFinanceEventsWithAggregateAsKey() throws Exception {
        MvcResult result = mockMvc.perform(post("/depreciation-rates").with(systemAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountPerKm\":0.85}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID rateId = idAt(result, "$.id");

        ConsumerRecord<String, String> record = awaitRecord(financeTopic, rateId.toString());

        JsonNode envelope = jsonMapper.readTree(record.value());
        assertEquals(EventTypes.DEPRECIATION_RATE_CHANGED, envelope.get("eventType").asString());
        assertEquals(rateId.toString(), envelope.get("aggregateId").asString());
        assertEquals(0, new BigDecimal("0.85").compareTo(envelope.get("payload").get("amountPerKm").decimalValue()));
        await().atMost(TIMEOUT).untilAsserted(() -> assertNotNull(jdbcTemplate.queryForObject(
                "SELECT published_at FROM outbox_events WHERE aggregate_id = ?", Instant.class, rateId)));
    }

    @Test
    void workshopExpenseRegistered_isPublishedWithAllLines() throws Exception {
        UUID branchId = UUID.randomUUID();
        UUID busId = UUID.randomUUID();
        givenBus(busId, branchId);
        MvcResult result = mockMvc.perform(post("/workshop-expenses").with(branchAdmin(branchId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"busId":"%s","lines":[
                                  {"categoryId":"%s","type":"PART","amount":1200},
                                  {"categoryId":"%s","type":"LABOR","amount":150}]}
                                """.formatted(busId, categoryId("Llantas"), categoryId("Cambio de aceite"))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID expenseId = idAt(result, "$.id");

        ConsumerRecord<String, String> record = awaitRecord(financeTopic, expenseId.toString());

        JsonNode envelope = jsonMapper.readTree(record.value());
        assertEquals(EventTypes.WORKSHOP_EXPENSE_REGISTERED, envelope.get("eventType").asString());
        JsonNode payload = envelope.get("payload");
        assertEquals(branchId.toString(), payload.get("branchId").asString());
        assertEquals(0, new BigDecimal("1350").compareTo(payload.get("total").decimalValue()));
        assertEquals(2, payload.get("lines").size());
        assertEquals("Cambio de aceite", payload.get("lines").get(1).get("categoryName").asString());
        assertEquals("LABOR", payload.get("lines").get(1).get("type").asString());
    }

    @Test
    void malformedTripEvent_endsInDeadLetterTopic() throws Exception {
        UUID key = UUID.randomUUID();

        send(tripTopic, key, "{no es json");

        ConsumerRecord<String, String> record = awaitRecord(tripTopic + ".DLT", key.toString());
        assertEquals("{no es json", record.value());
    }

    private void send(String topic, UUID key, String value) throws Exception {
        kafkaTemplate.send(topic, key.toString(), value).get(10, TimeUnit.SECONDS);
    }

    private static Map<String, Object> completedPayload(UUID tripId, UUID busId, UUID originBranchId, Instant arrival,
                                                        Number km) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("tripId", tripId.toString());
        payload.put("type", "REGULAR");
        payload.put("rentalId", null);
        payload.put("originBranchId", originBranchId.toString());
        payload.put("busId", busId.toString());
        payload.put("actualArrivalAt", arrival.toString());
        payload.put("kmTraveled", km);
        payload.put("driverPay", 150.0);
        return payload;
    }

    private String envelope(UUID eventId, String eventType, UUID aggregateId, Map<String, Object> payload) {
        return jsonMapper.writeValueAsString(Map.of(
                "eventId", eventId.toString(),
                "eventType", eventType,
                "occurredAt", Instant.now().toString(),
                "aggregateId", aggregateId.toString(),
                "version", 1,
                "correlationId", UUID.randomUUID().toString(),
                "payload", payload));
    }

    private ConsumerRecord<String, String> awaitRecord(String topic, String key) {
        List<ConsumerRecord<String, String>> received = new ArrayList<>();
        try (Consumer<String, String> consumer = consumerFactory.createConsumer("it-" + UUID.randomUUID(), "it")) {
            consumer.subscribe(List.of(topic));
            await().atMost(Duration.ofSeconds(60)).pollInSameThread().untilAsserted(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(received::add);
                assertTrue(received.stream().anyMatch(record -> key.equals(record.key())),
                        "no llegó el mensaje con key " + key + " a " + topic);
            });
        }
        return received.stream().filter(record -> key.equals(record.key())).findFirst().orElseThrow();
    }
}
