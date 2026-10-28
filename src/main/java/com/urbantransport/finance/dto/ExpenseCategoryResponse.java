package com.urbantransport.finance.dto;

import com.urbantransport.finance.domain.ExpenseCategoryEntity;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

@Value
public class ExpenseCategoryResponse {
    UUID id;
    String name;
    boolean active;
    Instant createdAt;
    Instant updatedAt;

    public static ExpenseCategoryResponse fromEntity(ExpenseCategoryEntity category) {
        return new ExpenseCategoryResponse(
                category.getId(),
                category.getName(),
                category.isActive(),
                category.getCreatedAt(),
                category.getUpdatedAt());
    }
}
