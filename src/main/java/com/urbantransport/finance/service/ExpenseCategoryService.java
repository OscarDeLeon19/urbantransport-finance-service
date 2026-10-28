package com.urbantransport.finance.service;

import com.urbantransport.finance.dto.CreateExpenseCategoryRequest;
import com.urbantransport.finance.dto.ExpenseCategoryResponse;
import com.urbantransport.finance.dto.UpdateExpenseCategoryRequest;

import java.util.List;
import java.util.UUID;

public interface ExpenseCategoryService {

    List<ExpenseCategoryResponse> findAll(Boolean active);

    ExpenseCategoryResponse create(CreateExpenseCategoryRequest request);

    ExpenseCategoryResponse update(UUID id, UpdateExpenseCategoryRequest request);

    ExpenseCategoryResponse deactivate(UUID id);

    void delete(UUID id);
}
