package com.urbantransport.finance.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TripCompletedPayload(
        UUID tripId,
        UUID busId,
        UUID originBranchId,
        Instant actualArrivalAt,
        BigDecimal kmTraveled) {
}
