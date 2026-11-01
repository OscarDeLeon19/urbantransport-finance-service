package com.urbantransport.finance.event;

import com.urbantransport.finance.domain.DepreciationRateEntity;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Value
public class DepreciationRateChangedPayload {
    UUID rateId;
    BigDecimal amountPerKm;
    Instant effectiveFrom;
    UUID createdBy;

    public static DepreciationRateChangedPayload from(DepreciationRateEntity rate) {
        return new DepreciationRateChangedPayload(
                rate.getId(),
                rate.getAmountPerKm(),
                rate.getEffectiveFrom(),
                rate.getCreatedBy());
    }
}
