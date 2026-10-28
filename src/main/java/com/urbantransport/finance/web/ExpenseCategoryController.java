package com.urbantransport.finance.web;

import com.urbantransport.finance.dto.CreateExpenseCategoryRequest;
import com.urbantransport.finance.dto.ExpenseCategoryResponse;
import com.urbantransport.finance.dto.UpdateExpenseCategoryRequest;
import com.urbantransport.finance.service.ExpenseCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/expense-categories")
@RequiredArgsConstructor
public class ExpenseCategoryController {

    private final ExpenseCategoryService expenseCategoryService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ExpenseCategoryResponse create(@Valid @RequestBody CreateExpenseCategoryRequest request) {
        return expenseCategoryService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ExpenseCategoryResponse update(@PathVariable UUID id,
                                          @Valid @RequestBody UpdateExpenseCategoryRequest request) {
        return expenseCategoryService.update(id, request);
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ExpenseCategoryResponse deactivate(@PathVariable UUID id) {
        return expenseCategoryService.deactivate(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public void delete(@PathVariable UUID id) {
        expenseCategoryService.delete(id);
    }
}
