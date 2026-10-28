package com.urbantransport.finance.dto;

import com.urbantransport.finance.domain.DepreciationRateEntity;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Value
public class DepreciationRateResponse {
    UUID id;
    BigDecimal amountPerKm;
    Instant effectiveFrom;
    UUID createdBy;

    public static DepreciationRateResponse fromEntity(DepreciationRateEntity rate) {
        return new DepreciationRateResponse(
                rate.getId(),
                rate.getAmountPerKm(),
                rate.getEffectiveFrom(),
                rate.getCreatedBy());
    }
}
