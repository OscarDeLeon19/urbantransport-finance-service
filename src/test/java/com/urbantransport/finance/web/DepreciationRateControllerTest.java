package com.urbantransport.finance.web;

import com.urbantransport.finance.config.SecurityConfig;
import com.urbantransport.finance.dto.CreateDepreciationRateRequest;
import com.urbantransport.finance.dto.DepreciationRateResponse;
import com.urbantransport.finance.dto.PageResponse;
import com.urbantransport.finance.exception.ApiException;
import com.urbantransport.finance.exception.ErrorCodes;
import com.urbantransport.finance.service.DepreciationRateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DepreciationRateController.class)
@Import(SecurityConfig.class)
class DepreciationRateControllerTest {

    private static final UUID RATE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID BRANCH_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final LocalDate TOMORROW = LocalDate.now(ZoneId.of("America/Guatemala")).plusDays(1);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DepreciationRateService depreciationRateService;

    @Test
    void create_asSystemAdmin_passesRequestAndUser() throws Exception {
        when(depreciationRateService.create(any(), eq(USER_ID))).thenReturn(rate());

        mockMvc.perform(post("/depreciation-rates").with(user("SYSTEM_ADMIN", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountPerKm\":0.75,\"effectiveFrom\":\"%s\"}".formatted(TOMORROW)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(RATE_ID.toString()))
                .andExpect(jsonPath("$.amountPerKm").value(0.75));

        ArgumentCaptor<CreateDepreciationRateRequest> captor = ArgumentCaptor.forClass(CreateDepreciationRateRequest.class);
        verify(depreciationRateService).create(captor.capture(), eq(USER_ID));
        assertEquals(0, new BigDecimal("0.75").compareTo(captor.getValue().amountPerKm()));
        assertEquals(TOMORROW, captor.getValue().effectiveFrom());
    }

    @Test
    void create_withoutDate_passesNullDate() throws Exception {
        when(depreciationRateService.create(any(), eq(USER_ID))).thenReturn(rate());

        mockMvc.perform(post("/depreciation-rates").with(user("SYSTEM_ADMIN", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountPerKm\":0}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateDepreciationRateRequest> captor = ArgumentCaptor.forClass(CreateDepreciationRateRequest.class);
        verify(depreciationRateService).create(captor.capture(), eq(USER_ID));
        assertNull(captor.getValue().effectiveFrom());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"amountPerKm\":-0.01}", "{\"amountPerKm\":0.12345}", "{\"amountPerKm\":1234567}"})
    void create_withInvalidAmount_returnsValidationError(String body) throws Exception {
        mockMvc.perform(post("/depreciation-rates").with(user("SYSTEM_ADMIN", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_ERROR))
                .andExpect(jsonPath("$.message", containsString("amountPerKm")));

        verifyNoInteractions(depreciationRateService);
    }

    @Test
    void create_withMalformedDate_returnsValidationError() throws Exception {
        mockMvc.perform(post("/depreciation-rates").with(user("SYSTEM_ADMIN", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountPerKm\":0.75,\"effectiveFrom\":\"01/11/2026\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_ERROR));

        verifyNoInteractions(depreciationRateService);
    }

    @Test
    void create_asBranchAdmin_returnsAccessDenied() throws Exception {
        mockMvc.perform(post("/depreciation-rates").with(user("BRANCH_ADMIN", BRANCH_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountPerKm\":0.75}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCodes.ACCESS_DENIED));

        verifyNoInteractions(depreciationRateService);
    }

    @Test
    void findCurrent_asSystemAdmin_returnsRate() throws Exception {
        when(depreciationRateService.findCurrent()).thenReturn(rate());

        mockMvc.perform(get("/depreciation-rates/current").with(user("SYSTEM_ADMIN", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(RATE_ID.toString()));
    }

    @Test
    void findCurrent_withoutRate_returnsNotFound() throws Exception {
        when(depreciationRateService.findCurrent()).thenThrow(new ApiException(HttpStatus.NOT_FOUND,
                ErrorCodes.DEPRECIATION_RATE_NOT_FOUND, "Sin tasa"));

        mockMvc.perform(get("/depreciation-rates/current").with(user("SYSTEM_ADMIN", null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCodes.DEPRECIATION_RATE_NOT_FOUND));
    }

    @Test
    void findCurrent_asBranchAdmin_returnsAccessDenied() throws Exception {
        mockMvc.perform(get("/depreciation-rates/current").with(user("BRANCH_ADMIN", BRANCH_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCodes.ACCESS_DENIED));

        verifyNoInteractions(depreciationRateService);
    }

    @Test
    void search_withoutPaging_usesDefaults() throws Exception {
        when(depreciationRateService.search(0, 20)).thenReturn(new PageResponse<>(List.of(rate()), 0, 20, 1, 1));

        mockMvc.perform(get("/depreciation-rates").with(user("SYSTEM_ADMIN", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(RATE_ID.toString()))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void search_withPaging_passesPageAndSize() throws Exception {
        when(depreciationRateService.search(2, 5)).thenReturn(new PageResponse<>(List.of(), 2, 5, 0, 0));

        mockMvc.perform(get("/depreciation-rates").param("page", "2").param("size", "5")
                        .with(user("SYSTEM_ADMIN", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2));
    }

    @Test
    void search_withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/depreciation-rates"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(depreciationRateService);
    }

    private static DepreciationRateResponse rate() {
        return new DepreciationRateResponse(RATE_ID, new BigDecimal("0.7500"), Instant.parse("2026-10-01T06:00:00Z"),
                USER_ID);
    }

    private static RequestPostProcessor user(String role, UUID branchId) {
        return jwt()
                .jwt(token -> {
                    token.subject(USER_ID.toString()).claim("roles", List.of(role));
                    if (branchId != null) {
                        token.claim("branchId", branchId.toString());
                    }
                })
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
