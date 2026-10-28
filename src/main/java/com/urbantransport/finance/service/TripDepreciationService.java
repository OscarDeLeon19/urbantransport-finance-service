package com.urbantransport.finance.service;

import com.urbantransport.finance.dto.PageResponse;
import com.urbantransport.finance.dto.TripDepreciationResponse;
import com.urbantransport.finance.event.TripCompletedPayload;

import java.time.LocalDate;
import java.util.UUID;

public interface TripDepreciationService {

    void applyTripCompleted(UUID eventId, String eventType, TripCompletedPayload payload);

    PageResponse<TripDepreciationResponse> search(UUID branchId, UUID busId, LocalDate from, LocalDate to,
                                                  int page, int size);

    TripDepreciationResponse findByTripId(UUID tripId, UUID scopeBranchId);
}
