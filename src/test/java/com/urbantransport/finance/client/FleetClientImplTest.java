package com.urbantransport.finance.client;

import com.urbantransport.finance.config.FleetClientProperties;
import com.urbantransport.finance.exception.ApiException;
import com.urbantransport.finance.exception.ErrorCodes;
import com.urbantransport.finance.exception.FleetUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FleetClientImplTest {

    private static final String BASE_URL = "http://fleet.test";
    private static final String API_KEY = "clave-interna";
    private static final UUID BUS_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BRANCH_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String BUS_URL = BASE_URL + "/internal/buses/" + BUS_ID;
    private static final String BUS_JSON = """
            {"id":"%s","plate":"P-123ABC","brand":"Volvo","capacity":40,"status":"DISPONIBLE",
             "odometerKm":1000.50,"branchId":"%s","createdAt":"2026-10-01T15:00:00Z"}
            """.formatted(BUS_ID, BRANCH_ID);

    private MockRestServiceServer server;
    private RestClient restClient;
    private CircuitBreaker circuitBreaker;
    private FleetClientImpl fleetClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
        circuitBreaker = CircuitBreaker.of("fleet", CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(2)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50)
                .recordExceptions(FleetUnavailableException.class)
                .build());
        fleetClient = clientWithKey(API_KEY);
    }

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    @Test
    void getBus_sendsInternalKeyAndMapsOnlyNeededFields() {
        server.expect(requestTo(BUS_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Key", API_KEY))
                .andRespond(withSuccess(BUS_JSON, MediaType.APPLICATION_JSON));

        FleetBusResponse bus = fleetClient.getBus(BUS_ID);

        assertEquals(new FleetBusResponse(BUS_ID, "P-123ABC", "DISPONIBLE", BRANCH_ID), bus);
    }

    @Test
    void missingApiKey_sendsEmptyHeader() {
        server.expect(requestTo(BUS_URL))
                .andExpect(header("X-Internal-Key", ""))
                .andRespond(withSuccess(BUS_JSON, MediaType.APPLICATION_JSON));

        assertEquals(BUS_ID, clientWithKey(null).getBus(BUS_ID).id());
    }

    @Test
    void unknownBus_throwsBusNotFound() {
        server.expect(requestTo(BUS_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertApiError(HttpStatus.NOT_FOUND, ErrorCodes.BUS_NOT_FOUND, () -> fleetClient.getBus(BUS_ID));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 409})
    void otherClientError_throwsBadGateway(int status) {
        server.expect(requestTo(BUS_URL)).andRespond(withStatus(HttpStatus.valueOf(status)));

        assertApiError(HttpStatus.BAD_GATEWAY, ErrorCodes.FLEET_ERROR, () -> fleetClient.getBus(BUS_ID));
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503})
    void serverError_throwsServiceUnavailable(int status) {
        server.expect(requestTo(BUS_URL)).andRespond(withStatus(HttpStatus.valueOf(status)));

        assertApiError(HttpStatus.SERVICE_UNAVAILABLE, ErrorCodes.FLEET_UNAVAILABLE, () -> fleetClient.getBus(BUS_ID));
    }

    @Test
    void connectionFailure_throwsServiceUnavailable() {
        server.expect(requestTo(BUS_URL)).andRespond(withException(new ConnectException("Connection refused")));

        assertApiError(HttpStatus.SERVICE_UNAVAILABLE, ErrorCodes.FLEET_UNAVAILABLE, () -> fleetClient.getBus(BUS_ID));
    }

    @Test
    void readTimeout_throwsServiceUnavailable() {
        server.expect(requestTo(BUS_URL)).andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertApiError(HttpStatus.SERVICE_UNAVAILABLE, ErrorCodes.FLEET_UNAVAILABLE, () -> fleetClient.getBus(BUS_ID));
    }

    @Test
    void openCircuit_failsFastWithoutCallingFleet() {
        circuitBreaker.transitionToOpenState();

        assertApiError(HttpStatus.SERVICE_UNAVAILABLE, ErrorCodes.FLEET_UNAVAILABLE, () -> fleetClient.getBus(BUS_ID));
    }

    @Test
    void repeatedServerErrors_openCircuit() {
        server.expect(ExpectedCount.times(2), requestTo(BUS_URL)).andRespond(withServerError());

        assertApiError(HttpStatus.SERVICE_UNAVAILABLE, ErrorCodes.FLEET_UNAVAILABLE, () -> fleetClient.getBus(BUS_ID));
        assertApiError(HttpStatus.SERVICE_UNAVAILABLE, ErrorCodes.FLEET_UNAVAILABLE, () -> fleetClient.getBus(BUS_ID));

        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
        assertApiError(HttpStatus.SERVICE_UNAVAILABLE, ErrorCodes.FLEET_UNAVAILABLE, () -> fleetClient.getBus(BUS_ID));
    }

    @Test
    void repeatedClientErrors_doNotOpenCircuit() {
        server.expect(ExpectedCount.times(2), requestTo(BUS_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertApiError(HttpStatus.BAD_GATEWAY, ErrorCodes.FLEET_ERROR, () -> fleetClient.getBus(BUS_ID));
        assertApiError(HttpStatus.BAD_GATEWAY, ErrorCodes.FLEET_ERROR, () -> fleetClient.getBus(BUS_ID));

        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
    }

    @Test
    void repeatedNotFound_doesNotOpenCircuit() {
        server.expect(ExpectedCount.times(2), requestTo(BUS_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertApiError(HttpStatus.NOT_FOUND, ErrorCodes.BUS_NOT_FOUND, () -> fleetClient.getBus(BUS_ID));
        assertApiError(HttpStatus.NOT_FOUND, ErrorCodes.BUS_NOT_FOUND, () -> fleetClient.getBus(BUS_ID));

        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
    }

    private FleetClientImpl clientWithKey(String apiKey) {
        FleetClientProperties properties = new FleetClientProperties(BASE_URL, apiKey, Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                new FleetClientProperties.CircuitBreakerProperties(2, 2, 50f, Duration.ofSeconds(30)));
        return new FleetClientImpl(restClient, circuitBreaker, properties);
    }

    private static void assertApiError(HttpStatus status, String code, Executable action) {
        ApiException exception = assertThrows(ApiException.class, action);
        assertEquals(status, exception.getStatus());
        assertEquals(code, exception.getCode());
    }
}
