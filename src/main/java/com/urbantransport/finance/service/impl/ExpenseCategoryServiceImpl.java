package com.urbantransport.finance.service.impl;

import com.urbantransport.finance.domain.ExpenseCategoryEntity;
import com.urbantransport.finance.dto.CreateExpenseCategoryRequest;
import com.urbantransport.finance.dto.ExpenseCategoryResponse;
import com.urbantransport.finance.dto.UpdateExpenseCategoryRequest;
import com.urbantransport.finance.exception.ApiException;
import com.urbantransport.finance.exception.ErrorCodes;
import com.urbantransport.finance.repository.ExpenseCategoryRepository;
import com.urbantransport.finance.repository.WorkshopExpenseLineRepository;
import com.urbantransport.finance.service.ExpenseCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExpenseCategoryServiceImpl implements ExpenseCategoryService {

    private final ExpenseCategoryRepository expenseCategoryRepository;
    private final WorkshopExpenseLineRepository workshopExpenseLineRepository;

    @Override
    @Transactional
    public ExpenseCategoryResponse create(CreateExpenseCategoryRequest request) {
        String name = request.name().trim();
        if (expenseCategoryRepository.existsByNameIgnoreCase(name)) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCodes.CATEGORY_ALREADY_EXISTS,
                    "Ya existe una categoría con el nombre " + name);
        }

        ExpenseCategoryEntity category = expenseCategoryRepository.save(ExpenseCategoryEntity.builder()
                .name(name)
                .build());
        return ExpenseCategoryResponse.fromEntity(category);
    }

    @Override
    @Transactional
    public ExpenseCategoryResponse update(UUID id, UpdateExpenseCategoryRequest request) {
        ExpenseCategoryEntity category = getOrThrow(id);

        String name = request.name().trim();
        if (expenseCategoryRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCodes.CATEGORY_ALREADY_EXISTS,
                    "Ya existe una categoría con el nombre " + name);
        }

        category.setName(name);
        return ExpenseCategoryResponse.fromEntity(expenseCategoryRepository.save(category));
    }

    @Override
    @Transactional
    public ExpenseCategoryResponse deactivate(UUID id) {
        ExpenseCategoryEntity category = getOrThrow(id);
        if (!category.isActive()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCodes.CATEGORY_INACTIVE,
                    "La categoría ya fue desactivada");
        }

        category.setActive(false);
        return ExpenseCategoryResponse.fromEntity(expenseCategoryRepository.save(category));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        ExpenseCategoryEntity category = getOrThrow(id);
        if (workshopExpenseLineRepository.existsByCategoryId(id)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCodes.CATEGORY_IN_USE,
                    "La categoría tiene gastos asociados, solo puede desactivarse");
        }

        expenseCategoryRepository.delete(category);
    }

    private ExpenseCategoryEntity getOrThrow(UUID id) {
        return expenseCategoryRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCodes.CATEGORY_NOT_FOUND,
                        "Categoría no encontrada"));
    }
}
