package com.urbantransport.finance.dto;

import com.urbantransport.finance.domain.ExpenseLineType;
import com.urbantransport.finance.domain.WorkshopExpenseLineEntity;
import lombok.Value;

import java.math.BigDecimal;
import java.util.UUID;

@Value
public class WorkshopExpenseLineResponse {
    UUID id;
    UUID categoryId;
    String categoryName;
    String description;
    ExpenseLineType type;
    BigDecimal amount;

    public static WorkshopExpenseLineResponse fromEntity(WorkshopExpenseLineEntity line, String categoryName) {
        return new WorkshopExpenseLineResponse(
                line.getId(),
                line.getCategoryId(),
                categoryName,
                line.getDescription(),
                line.getType(),
                line.getAmount());
    }
}
