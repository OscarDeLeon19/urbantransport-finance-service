package com.urbantransport.finance.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateDepreciationRateRequest(
        @NotNull @PositiveOrZero @Digits(integer = 6, fraction = 4) BigDecimal amountPerKm,
        LocalDate effectiveFrom) {
}
