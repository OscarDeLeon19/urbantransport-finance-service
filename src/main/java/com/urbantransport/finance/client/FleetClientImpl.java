package com.urbantransport.finance.client;

import com.urbantransport.finance.config.FleetClientProperties;
import com.urbantransport.finance.exception.ApiException;
import com.urbantransport.finance.exception.ErrorCodes;
import com.urbantransport.finance.exception.FleetUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.UUID;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class FleetClientImpl implements FleetClient {

    private static final String BUS_PATH = "/internal/buses/{id}";
    private static final String INTERNAL_KEY_HEADER = "X-Internal-Key";
    private static final String UNAVAILABLE_MESSAGE =
            "El servicio de flota no está disponible, intenta más tarde";

    private final RestClient fleetRestClient;
    private final CircuitBreaker fleetCircuitBreaker;
    private final FleetClientProperties properties;

    @Override
    public FleetBusResponse getBus(UUID busId) {
        return execute(() -> get(BUS_PATH, busId, FleetBusResponse.class,
                ErrorCodes.BUS_NOT_FOUND, "Bus no encontrado"));
    }

    private <T> T execute(Supplier<T> call) {
        try {
            return fleetCircuitBreaker.executeSupplier(call);
        } catch (CallNotPermittedException | FleetUnavailableException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCodes.FLEET_UNAVAILABLE, UNAVAILABLE_MESSAGE);
        }
    }

    private <T> T get(String path, UUID id, Class<T> type, String notFoundCode, String notFoundMessage) {
        try {
            String apiKey = properties.apiKey();
            return fleetRestClient.get()
                    .uri(path, id)
                    .header(INTERNAL_KEY_HEADER, apiKey == null ? "" : apiKey)
                    .retrieve()
                    .onStatus(status -> status.is5xxServerError(), (req, res) -> {
                        throw new FleetUnavailableException("Fleet respondió " + res.getStatusCode());
                    })
                    .onStatus(status -> status.value() == HttpStatus.NOT_FOUND.value(), (req, res) -> {
                        throw new ApiException(HttpStatus.NOT_FOUND, notFoundCode, notFoundMessage);
                    })
                    .onStatus(status -> status.is4xxClientError(), (req, res) -> {
                        log.error("Fleet rechazó la consulta {} {}: {} (revise INTERNAL_API_KEY)", path, id, res.getStatusCode());
                        throw new ApiException(HttpStatus.BAD_GATEWAY, ErrorCodes.FLEET_ERROR,
                                "No se pudo validar con el servicio de flota, intenta más tarde");
                    })
                    .body(type);
        } catch (ResourceAccessException exception) {
            throw new FleetUnavailableException("No se pudo contactar a fleet", exception);
        }
    }
}
