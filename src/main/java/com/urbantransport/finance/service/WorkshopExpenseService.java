package com.urbantransport.finance.service;

import com.urbantransport.finance.dto.CreateWorkshopExpenseRequest;
import com.urbantransport.finance.dto.PageResponse;
import com.urbantransport.finance.dto.WorkshopExpenseResponse;
import com.urbantransport.finance.dto.WorkshopExpenseSummaryResponse;

import java.time.LocalDate;
import java.util.UUID;

public interface WorkshopExpenseService {

    WorkshopExpenseResponse register(CreateWorkshopExpenseRequest request, UUID branchId, UUID createdBy);

    PageResponse<WorkshopExpenseSummaryResponse> search(UUID branchId, UUID busId, UUID categoryId,
                                                        LocalDate from, LocalDate to, int page, int size);

    WorkshopExpenseResponse findById(UUID id, UUID scopeBranchId);
}
