package com.urbantransport.finance.dto;

import com.urbantransport.finance.domain.ExpenseLineType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record WorkshopExpenseLineRequest(
        @NotNull UUID categoryId,
        @Size(max = 255) String description,
        @NotNull ExpenseLineType type,
        @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal amount) {
}
