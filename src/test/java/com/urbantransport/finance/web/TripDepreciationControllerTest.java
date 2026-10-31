package com.urbantransport.finance.web;

import com.urbantransport.finance.config.SecurityConfig;
import com.urbantransport.finance.dto.PageResponse;
import com.urbantransport.finance.dto.TripDepreciationResponse;
import com.urbantransport.finance.exception.ApiException;
import com.urbantransport.finance.exception.ErrorCodes;
import com.urbantransport.finance.service.TripDepreciationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TripDepreciationController.class)
@Import(SecurityConfig.class)
class TripDepreciationControllerTest {

    private static final UUID TRIP_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BRANCH_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OTHER_BRANCH_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID BUS_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Guatemala"));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TripDepreciationService tripDepreciationService;

    @Test
    void search_asBranchAdmin_forcesOwnBranchAndPassesFilters() throws Exception {
        when(tripDepreciationService.search(BRANCH_ID, BUS_ID, TODAY.minusDays(30), TODAY, 0, 20)).thenReturn(page());

        mockMvc.perform(get("/depreciations").with(user("BRANCH_ADMIN", BRANCH_ID))
                        .param("busId", BUS_ID.toString())
                        .param("from", TODAY.minusDays(30).toString())
                        .param("to", TODAY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].tripId").value(TRIP_ID.toString()))
                .andExpect(jsonPath("$.content[0].amount").value(90.38));
    }

    @Test
    void search_asBranchAdminWithOwnBranch_isAccepted() throws Exception {
        when(tripDepreciationService.search(BRANCH_ID, null, null, null, 0, 20)).thenReturn(page());

        mockMvc.perform(get("/depreciations").param("branchId", BRANCH_ID.toString())
                        .with(user("BRANCH_ADMIN", BRANCH_ID)))
                .andExpect(status().isOk());
    }

    @Test
    void search_asBranchAdminForOtherBranch_returnsBranchForbidden() throws Exception {
        mockMvc.perform(get("/depreciations").param("branchId", OTHER_BRANCH_ID.toString())
                        .with(user("BRANCH_ADMIN", BRANCH_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCodes.BRANCH_FORBIDDEN));

        verifyNoInteractions(tripDepreciationService);
    }

    @Test
    void search_asSystemAdmin_passesRequestedBranchAndPaging() throws Exception {
        when(tripDepreciationService.search(OTHER_BRANCH_ID, null, null, null, 2, 50)).thenReturn(page());

        mockMvc.perform(get("/depreciations").param("branchId", OTHER_BRANCH_ID.toString())
                        .param("page", "2").param("size", "50")
                        .with(user("SYSTEM_ADMIN", null)))
                .andExpect(status().isOk());

        verify(tripDepreciationService).search(OTHER_BRANCH_ID, null, null, null, 2, 50);
    }

    @Test
    void search_withInvalidRangeFromService_returnsBadRequest() throws Exception {
        when(tripDepreciationService.search(null, null, TODAY, TODAY.minusDays(1), 0, 20))
                .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_DATE_RANGE, "Rango"));

        mockMvc.perform(get("/depreciations").param("from", TODAY.toString())
                        .param("to", TODAY.minusDays(1).toString())
                        .with(user("SYSTEM_ADMIN", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.INVALID_DATE_RANGE));
    }

    @Test
    void search_asCashier_returnsAccessDenied() throws Exception {
        mockMvc.perform(get("/depreciations").with(user("CASHIER", BRANCH_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCodes.ACCESS_DENIED));

        verifyNoInteractions(tripDepreciationService);
    }

    @Test
    void search_asBranchAdminWithoutBranch_returnsBranchRequired() throws Exception {
        mockMvc.perform(get("/depreciations").with(user("BRANCH_ADMIN", null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCodes.BRANCH_REQUIRED));

        verifyNoInteractions(tripDepreciationService);
    }

    @Test
    void findByTripId_asBranchAdmin_isScopedToOwnBranch() throws Exception {
        when(tripDepreciationService.findByTripId(TRIP_ID, BRANCH_ID)).thenReturn(depreciation());

        mockMvc.perform(get("/depreciations/{tripId}", TRIP_ID).with(user("BRANCH_ADMIN", BRANCH_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.km").value(120.5))
                .andExpect(jsonPath("$.rateApplied").value(0.75));
    }

    @Test
    void findByTripId_asSystemAdmin_isNotScoped() throws Exception {
        when(tripDepreciationService.findByTripId(TRIP_ID, null)).thenReturn(depreciation());

        mockMvc.perform(get("/depreciations/{tripId}", TRIP_ID).with(user("SYSTEM_ADMIN", null)))
                .andExpect(status().isOk());

        verify(tripDepreciationService).findByTripId(TRIP_ID, null);
    }

    @Test
    void findByTripId_notCalculated_returnsNotFound() throws Exception {
        when(tripDepreciationService.findByTripId(TRIP_ID, BRANCH_ID)).thenThrow(new ApiException(
                HttpStatus.NOT_FOUND, ErrorCodes.DEPRECIATION_NOT_FOUND, "Sin depreciacion"));

        mockMvc.perform(get("/depreciations/{tripId}", TRIP_ID).with(user("BRANCH_ADMIN", BRANCH_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCodes.DEPRECIATION_NOT_FOUND));
    }

    @Test
    void findByTripId_withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/depreciations/{tripId}", TRIP_ID))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(tripDepreciationService);
    }

    private static TripDepreciationResponse depreciation() {
        return new TripDepreciationResponse(TRIP_ID, BUS_ID, BRANCH_ID, new BigDecimal("120.50"),
                new BigDecimal("0.7500"), new BigDecimal("90.38"), Instant.parse("2026-10-20T18:31:00Z"));
    }

    private static PageResponse<TripDepreciationResponse> page() {
        return new PageResponse<>(List.of(depreciation()), 0, 20, 1, 1);
    }

    private static RequestPostProcessor user(String role, UUID branchId) {
        return jwt()
                .jwt(token -> {
                    token.subject("99999999-9999-9999-9999-999999999999").claim("roles", List.of(role));
                    if (branchId != null) {
                        token.claim("branchId", branchId.toString());
                    }
                })
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
