package com.urbantransport.finance.service;

import com.urbantransport.finance.dto.CreateExpenseCategoryRequest;
import com.urbantransport.finance.dto.ExpenseCategoryResponse;
import com.urbantransport.finance.dto.UpdateExpenseCategoryRequest;

import java.util.UUID;

public interface ExpenseCategoryService {

    ExpenseCategoryResponse create(CreateExpenseCategoryRequest request);

    ExpenseCategoryResponse update(UUID id, UpdateExpenseCategoryRequest request);

    ExpenseCategoryResponse deactivate(UUID id);

    void delete(UUID id);
}
