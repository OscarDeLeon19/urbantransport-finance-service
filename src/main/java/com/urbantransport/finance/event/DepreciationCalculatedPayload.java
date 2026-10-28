package com.urbantransport.finance.event;

import com.urbantransport.finance.domain.TripDepreciationEntity;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Value
public class DepreciationCalculatedPayload {
    UUID tripId;
    UUID busId;
    UUID branchId;
    BigDecimal km;
    UUID rateId;
    BigDecimal rateApplied;
    BigDecimal amount;
    Instant arrivedAt;

    public static DepreciationCalculatedPayload from(TripDepreciationEntity depreciation, UUID rateId,
                                                     Instant arrivedAt) {
        return new DepreciationCalculatedPayload(
                depreciation.getTripId(),
                depreciation.getBusId(),
                depreciation.getBranchId(),
                depreciation.getKm(),
                rateId,
                depreciation.getRateApplied(),
                depreciation.getAmount(),
                arrivedAt);
    }
}
