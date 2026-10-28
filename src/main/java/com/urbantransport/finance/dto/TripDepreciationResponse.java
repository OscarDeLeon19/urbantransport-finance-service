package com.urbantransport.finance.dto;

import com.urbantransport.finance.domain.TripDepreciationEntity;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Value
public class TripDepreciationResponse {
    UUID tripId;
    UUID busId;
    UUID branchId;
    BigDecimal km;
    BigDecimal rateApplied;
    BigDecimal amount;
    Instant calculatedAt;

    public static TripDepreciationResponse fromEntity(TripDepreciationEntity depreciation) {
        return new TripDepreciationResponse(
                depreciation.getTripId(),
                depreciation.getBusId(),
                depreciation.getBranchId(),
                depreciation.getKm(),
                depreciation.getRateApplied(),
                depreciation.getAmount(),
                depreciation.getCalculatedAt());
    }
}
