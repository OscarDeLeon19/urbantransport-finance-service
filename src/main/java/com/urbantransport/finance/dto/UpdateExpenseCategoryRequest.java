package com.urbantransport.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateExpenseCategoryRequest(
        @NotBlank @Size(max = 80) String name) {
}
