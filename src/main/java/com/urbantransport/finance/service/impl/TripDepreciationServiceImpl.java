package com.urbantransport.finance.service.impl;

import com.urbantransport.finance.domain.DepreciationRateEntity;
import com.urbantransport.finance.domain.ProcessedEventEntity;
import com.urbantransport.finance.domain.TripDepreciationEntity;
import com.urbantransport.finance.dto.PageResponse;
import com.urbantransport.finance.dto.TripDepreciationResponse;
import com.urbantransport.finance.event.DepreciationCalculatedPayload;
import com.urbantransport.finance.event.EventTypes;
import com.urbantransport.finance.event.TripCompletedPayload;
import com.urbantransport.finance.exception.ApiException;
import com.urbantransport.finance.exception.ErrorCodes;
import com.urbantransport.finance.repository.DepreciationRateRepository;
import com.urbantransport.finance.repository.ProcessedEventRepository;
import com.urbantransport.finance.repository.TripDepreciationRepository;
import com.urbantransport.finance.repository.TripDepreciationSpecifications;
import com.urbantransport.finance.service.OutboxService;
import com.urbantransport.finance.service.TripDepreciationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TripDepreciationServiceImpl implements TripDepreciationService {

    private static final int MAX_PAGE_SIZE = 100;

    private final TripDepreciationRepository tripDepreciationRepository;
    private final DepreciationRateRepository depreciationRateRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final OutboxService outboxService;

    @Value("${app.zone:America/Guatemala}")
    private ZoneId zone;

    @Override
    @Transactional
    public void applyTripCompleted(UUID eventId, String eventType, TripCompletedPayload payload) {
        if (processedEventRepository.existsById(eventId)) {
            log.debug("Evento {} ya procesado, se ignora", eventId);
            return;
        }
        if (tripDepreciationRepository.existsById(payload.tripId())) {
            log.info("La depreciación del viaje {} ya fue calculada, evento {} ignorado", payload.tripId(), eventId);
            markProcessed(eventId, eventType);
            return;
        }

        DepreciationRateEntity rate = depreciationRateRepository
                .findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(payload.actualArrivalAt())
                .orElse(null);
        BigDecimal rateApplied = rate != null ? rate.getAmountPerKm() : BigDecimal.ZERO.setScale(4);
        if (rate == null) {
            log.warn("No hay tasa de depreciación vigente al {}; el viaje {} se registra con tasa 0",
                    payload.actualArrivalAt(), payload.tripId());
        }

        BigDecimal km = payload.kmTraveled().setScale(2, RoundingMode.HALF_UP);
        TripDepreciationEntity depreciation = tripDepreciationRepository.save(TripDepreciationEntity.builder()
                .tripId(payload.tripId())
                .busId(payload.busId())
                .branchId(payload.originBranchId())
                .km(km)
                .rateApplied(rateApplied)
                .amount(km.multiply(rateApplied).setScale(2, RoundingMode.HALF_UP))
                .build());

        outboxService.record(EventTypes.AGGREGATE_TRIP_DEPRECIATION, depreciation.getTripId(),
                EventTypes.DEPRECIATION_CALCULATED,
                DepreciationCalculatedPayload.from(depreciation, rate != null ? rate.getId() : null,
                        payload.actualArrivalAt()));

        markProcessed(eventId, eventType);
        log.info("Depreciación del viaje {}: {} km × {} = {}", payload.tripId(), km, rateApplied,
                depreciation.getAmount());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TripDepreciationResponse> search(UUID branchId, UUID busId, LocalDate from, LocalDate to,
                                                         int page, int size) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_DATE_RANGE,
                    "La fecha final no puede ser anterior a la inicial");
        }
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "calculatedAt"));
        Instant fromInstant = from == null ? null : from.atStartOfDay(zone).toInstant();
        Instant toExclusive = to == null ? null : to.plusDays(1).atStartOfDay(zone).toInstant();
        return PageResponse.of(tripDepreciationRepository
                .findAll(TripDepreciationSpecifications.filter(branchId, busId, fromInstant, toExclusive), pageable)
                .map(TripDepreciationResponse::fromEntity));
    }

    @Override
    @Transactional(readOnly = true)
    public TripDepreciationResponse findByTripId(UUID tripId, UUID scopeBranchId) {
        TripDepreciationEntity depreciation = tripDepreciationRepository.findById(tripId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCodes.DEPRECIATION_NOT_FOUND,
                        "El viaje no tiene depreciación calculada"));
        if (scopeBranchId != null && !depreciation.getBranchId().equals(scopeBranchId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCodes.DEPRECIATION_NOT_IN_BRANCH,
                    "La depreciación no pertenece a tu sucursal");
        }
        return TripDepreciationResponse.fromEntity(depreciation);
    }

    private void markProcessed(UUID eventId, String eventType) {
        processedEventRepository.save(ProcessedEventEntity.builder()
                .eventId(eventId)
                .eventType(eventType)
                .build());
    }
}
