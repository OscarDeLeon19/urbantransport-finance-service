package com.urbantransport.finance.web;

import com.urbantransport.finance.AbstractIntegrationTest;
import com.urbantransport.finance.exception.ErrorCodes;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TripDepreciationApiIT extends AbstractIntegrationTest {

    private static final UUID BRANCH_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_BRANCH_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID BUS_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID OTHER_BUS_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID TRIP_OLD = UUID.fromString("55555555-5555-5555-5555-555555555551");
    private static final UUID TRIP_TODAY = UUID.fromString("55555555-5555-5555-5555-555555555552");
    private static final UUID TRIP_OTHER_BUS = UUID.fromString("55555555-5555-5555-5555-555555555553");
    private static final UUID TRIP_OTHER_BRANCH = UUID.fromString("55555555-5555-5555-5555-555555555554");

    @Test
    void search_asBranchAdmin_returnsOwnBranchNewestFirstWithFilters() throws Exception {
        seedDepreciations();

        mockMvc.perform(get("/depreciations").with(branchAdmin(BRANCH_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].tripId").value(TRIP_TODAY.toString()))
                .andExpect(jsonPath("$.content[0].km").value(120.5))
                .andExpect(jsonPath("$.content[0].rateApplied").value(0.75))
                .andExpect(jsonPath("$.content[0].amount").value(90.38));

        mockMvc.perform(get("/depreciations").param("busId", OTHER_BUS_ID.toString()).with(branchAdmin(BRANCH_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].tripId").value(TRIP_OTHER_BUS.toString()));
    }

    @Test
    void search_byDate_usesGuatemalaDayBoundaries() throws Exception {
        seedDepreciations();

        mockMvc.perform(get("/depreciations").with(branchAdmin(BRANCH_ID))
                        .param("from", today().toString())
                        .param("to", today().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].tripId").value(TRIP_TODAY.toString()));

        mockMvc.perform(get("/depreciations").with(branchAdmin(BRANCH_ID))
                        .param("from", today().minusDays(5).toString())
                        .param("to", today().minusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void search_asBranchAdminForOtherBranch_returnsBranchForbidden() throws Exception {
        mockMvc.perform(get("/depreciations").param("branchId", OTHER_BRANCH_ID.toString())
                        .with(branchAdmin(BRANCH_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCodes.BRANCH_FORBIDDEN));
    }

    @Test
    void search_asSystemAdmin_seesAllBranchesOrFilters() throws Exception {
        seedDepreciations();

        mockMvc.perform(get("/depreciations").with(systemAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4));

        mockMvc.perform(get("/depreciations").param("branchId", OTHER_BRANCH_ID.toString()).with(systemAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].tripId").value(TRIP_OTHER_BRANCH.toString()));
    }

    @Test
    void search_withToBeforeFrom_returnsInvalidDateRange() throws Exception {
        mockMvc.perform(get("/depreciations").with(systemAdmin())
                        .param("from", today().toString())
                        .param("to", today().minusDays(1).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.INVALID_DATE_RANGE));
    }

    @Test
    void search_asCashier_returnsAccessDenied() throws Exception {
        mockMvc.perform(get("/depreciations").with(userWithRole("CASHIER", BRANCH_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCodes.ACCESS_DENIED));
    }

    @Test
    void findByTripId_ownBranchAndSystemAdmin_seeItButOtherBranchIsForbidden() throws Exception {
        seedDepreciations();

        mockMvc.perform(get("/depreciations/{tripId}", TRIP_TODAY).with(branchAdmin(BRANCH_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.busId").value(BUS_ID.toString()))
                .andExpect(jsonPath("$.amount").value(90.38));

        mockMvc.perform(get("/depreciations/{tripId}", TRIP_TODAY).with(branchAdmin(OTHER_BRANCH_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCodes.DEPRECIATION_NOT_IN_BRANCH));

        mockMvc.perform(get("/depreciations/{tripId}", TRIP_OTHER_BRANCH).with(systemAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branchId").value(OTHER_BRANCH_ID.toString()));
    }

    @Test
    void findByTripId_withoutDepreciation_returnsNotFound() throws Exception {
        mockMvc.perform(get("/depreciations/{tripId}", UUID.randomUUID()).with(systemAdmin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCodes.DEPRECIATION_NOT_FOUND));
    }

    @Test
    void findByTripId_withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/depreciations/{tripId}", TRIP_TODAY))
                .andExpect(status().isUnauthorized());
    }

    private void seedDepreciations() {
        insertDepreciation(TRIP_OLD, BRANCH_ID, BUS_ID, "80.00", "0.5000", "40.00", startOf(today().minusDays(3))
                .plusSeconds(12 * 3600));
        insertDepreciation(TRIP_OTHER_BUS, BRANCH_ID, OTHER_BUS_ID, "50.00", "0.5000", "25.00",
                startOf(today()).minusSeconds(60));
        insertDepreciation(TRIP_TODAY, BRANCH_ID, BUS_ID, "120.50", "0.7500", "90.38",
                startOf(today()).plusSeconds(60));
        insertDepreciation(TRIP_OTHER_BRANCH, OTHER_BRANCH_ID, OTHER_BUS_ID, "10.00", "0.7500", "7.50",
                startOf(today()).plusSeconds(120));
    }

    private static Instant startOf(LocalDate date) {
        return date.atStartOfDay(ZONE).toInstant();
    }
}
