package com.urbantransport.finance.service;

import com.urbantransport.finance.dto.CreateDepreciationRateRequest;
import com.urbantransport.finance.dto.DepreciationRateResponse;
import com.urbantransport.finance.dto.PageResponse;

import java.util.UUID;

public interface DepreciationRateService {

    DepreciationRateResponse create(CreateDepreciationRateRequest request, UUID createdBy);

    DepreciationRateResponse findCurrent();

    PageResponse<DepreciationRateResponse> search(int page, int size);
}
