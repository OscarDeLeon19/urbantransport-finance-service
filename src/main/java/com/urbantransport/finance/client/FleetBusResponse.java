package com.urbantransport.finance.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FleetBusResponse(
        UUID id,
        String plate,
        String status,
        UUID branchId) {
}
