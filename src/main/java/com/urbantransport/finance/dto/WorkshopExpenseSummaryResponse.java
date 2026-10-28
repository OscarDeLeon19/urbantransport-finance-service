package com.urbantransport.finance.dto;

import com.urbantransport.finance.domain.WorkshopExpenseEntity;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Value
public class WorkshopExpenseSummaryResponse {
    UUID id;
    UUID busId;
    UUID branchId;
    LocalDate serviceDate;
    BigDecimal total;
    UUID createdBy;
    Instant createdAt;

    public static WorkshopExpenseSummaryResponse fromEntity(WorkshopExpenseEntity expense) {
        return new WorkshopExpenseSummaryResponse(
                expense.getId(),
                expense.getBusId(),
                expense.getBranchId(),
                expense.getServiceDate(),
                expense.getTotal(),
                expense.getCreatedBy(),
                expense.getCreatedAt());
    }
}
