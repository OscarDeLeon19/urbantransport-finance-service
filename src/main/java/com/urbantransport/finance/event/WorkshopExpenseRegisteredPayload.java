package com.urbantransport.finance.event;

import com.urbantransport.finance.domain.ExpenseLineType;
import com.urbantransport.finance.domain.WorkshopExpenseEntity;
import com.urbantransport.finance.domain.WorkshopExpenseLineEntity;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Value
public class WorkshopExpenseRegisteredPayload {
    UUID expenseId;
    UUID busId;
    UUID branchId;
    LocalDate serviceDate;
    BigDecimal total;
    UUID createdBy;
    List<Line> lines;

    public static WorkshopExpenseRegisteredPayload from(WorkshopExpenseEntity expense,
                                                        List<WorkshopExpenseLineEntity> lines,
                                                        Map<UUID, String> categoryNames) {
        return new WorkshopExpenseRegisteredPayload(
                expense.getId(),
                expense.getBusId(),
                expense.getBranchId(),
                expense.getServiceDate(),
                expense.getTotal(),
                expense.getCreatedBy(),
                lines.stream()
                        .map(line -> new Line(
                                line.getId(),
                                line.getCategoryId(),
                                categoryNames.get(line.getCategoryId()),
                                line.getDescription(),
                                line.getType(),
                                line.getAmount()))
                        .toList());
    }

    @Value
    public static class Line {
        UUID lineId;
        UUID categoryId;
        String categoryName;
        String description;
        ExpenseLineType type;
        BigDecimal amount;
    }
}
