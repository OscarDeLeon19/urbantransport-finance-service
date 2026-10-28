package com.urbantransport.finance.service.impl;

import com.urbantransport.finance.domain.DepreciationRateEntity;
import com.urbantransport.finance.dto.CreateDepreciationRateRequest;
import com.urbantransport.finance.dto.DepreciationRateResponse;
import com.urbantransport.finance.dto.PageResponse;
import com.urbantransport.finance.event.DepreciationRateChangedPayload;
import com.urbantransport.finance.event.EventTypes;
import com.urbantransport.finance.exception.ApiException;
import com.urbantransport.finance.exception.ErrorCodes;
import com.urbantransport.finance.repository.DepreciationRateRepository;
import com.urbantransport.finance.service.DepreciationRateService;
import com.urbantransport.finance.service.OutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DepreciationRateServiceImpl implements DepreciationRateService {

    private static final int MAX_PAGE_SIZE = 100;

    private final DepreciationRateRepository depreciationRateRepository;
    private final OutboxService outboxService;

    @Value("${app.zone:America/Guatemala}")
    private ZoneId zone;

    @Override
    @Transactional
    public DepreciationRateResponse create(CreateDepreciationRateRequest request, UUID createdBy) {
        DepreciationRateEntity rate = depreciationRateRepository.save(DepreciationRateEntity.builder()
                .amountPerKm(request.amountPerKm().setScale(4, RoundingMode.HALF_UP))
                .effectiveFrom(resolveEffectiveFrom(request.effectiveFrom()))
                .createdBy(createdBy)
                .build());

        outboxService.record(EventTypes.AGGREGATE_DEPRECIATION_RATE, rate.getId(),
                EventTypes.DEPRECIATION_RATE_CHANGED, DepreciationRateChangedPayload.from(rate));
        return DepreciationRateResponse.fromEntity(rate);
    }

    @Override
    @Transactional(readOnly = true)
    public DepreciationRateResponse findCurrent() {
        return depreciationRateRepository.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(Instant.now())
                .map(DepreciationRateResponse::fromEntity)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCodes.DEPRECIATION_RATE_NOT_FOUND,
                        "No hay una tasa de depreciación vigente"));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DepreciationRateResponse> search(int page, int size) {
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "effectiveFrom"));
        return PageResponse.of(depreciationRateRepository.findAll(pageable)
                .map(DepreciationRateResponse::fromEntity));
    }

    private Instant resolveEffectiveFrom(LocalDate effectiveFrom) {
        if (effectiveFrom == null || effectiveFrom.equals(LocalDate.now(zone))) {
            return Instant.now();
        }
        return effectiveFrom.atStartOfDay(zone).toInstant();
    }
}
