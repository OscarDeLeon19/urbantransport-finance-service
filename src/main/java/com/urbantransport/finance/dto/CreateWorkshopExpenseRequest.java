package com.urbantransport.finance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateWorkshopExpenseRequest(
        @NotNull UUID busId,
        LocalDate serviceDate,
        @NotEmpty List<@NotNull @Valid WorkshopExpenseLineRequest> lines) {
}
