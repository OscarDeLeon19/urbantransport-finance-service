package com.urbantransport.finance.dto;

import com.urbantransport.finance.domain.WorkshopExpenseEntity;
import com.urbantransport.finance.domain.WorkshopExpenseLineEntity;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Value
public class WorkshopExpenseResponse {
    UUID id;
    UUID busId;
    UUID branchId;
    LocalDate serviceDate;
    BigDecimal total;
    UUID createdBy;
    Instant createdAt;
    List<WorkshopExpenseLineResponse> lines;

    public static WorkshopExpenseResponse fromEntity(WorkshopExpenseEntity expense,
                                                     List<WorkshopExpenseLineEntity> lines,
                                                     Map<UUID, String> categoryNames) {
        return new WorkshopExpenseResponse(
                expense.getId(),
                expense.getBusId(),
                expense.getBranchId(),
                expense.getServiceDate(),
                expense.getTotal(),
                expense.getCreatedBy(),
                expense.getCreatedAt(),
                lines.stream()
                        .map(line -> WorkshopExpenseLineResponse.fromEntity(line,
                                categoryNames.get(line.getCategoryId())))
                        .toList());
    }
}
