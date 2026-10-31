package com.urbantransport.finance.web;

import com.urbantransport.finance.AbstractIntegrationTest;
import com.urbantransport.finance.domain.DepreciationRateEntity;
import com.urbantransport.finance.exception.ErrorCodes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DepreciationRateApiIT extends AbstractIntegrationTest {

    private static final UUID BRANCH_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void create_withoutDate_isEffectiveNowAndRecordsEvent() throws Exception {
        Instant before = Instant.now();

        MvcResult result = mockMvc.perform(post("/depreciation-rates").with(systemAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountPerKm\":0.75}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amountPerKm").value(0.75))
                .andExpect(jsonPath("$.createdBy").value(USER_ID.toString()))
                .andReturn();

        UUID rateId = idAt(result, "$.id");
        DepreciationRateEntity saved = depreciationRateRepository.findById(rateId).orElseThrow();
        assertEquals(0, new BigDecimal("0.7500").compareTo(saved.getAmountPerKm()));
        assertFalse(saved.getEffectiveFrom().isBefore(before.minusSeconds(1)));
        assertFalse(saved.getEffectiveFrom().isAfter(Instant.now()));
        assertEquals(List.of("DepreciationRateChanged"), outboxEventTypes(rateId));
        JsonNode payload = jsonMapper.readTree(outboxPayload(rateId)).get("payload");
        assertEquals(rateId.toString(), payload.get("rateId").asString());
        assertEquals(0, new BigDecimal("0.75").compareTo(payload.get("amountPerKm").decimalValue()));
    }

    @Test
    void create_withFutureDate_startsAtMidnightInGuatemala() throws Exception {
        LocalDate effectiveDate = today().plusDays(10);

        MvcResult result = mockMvc.perform(post("/depreciation-rates").with(systemAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountPerKm\":1.10,\"effectiveFrom\":\"%s\"}".formatted(effectiveDate)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.effectiveFrom").value(effectiveDate.atStartOfDay(ZONE).toInstant().toString()))
                .andReturn();

        DepreciationRateEntity saved = depreciationRateRepository.findById(idAt(result, "$.id")).orElseThrow();
        assertEquals(effectiveDate.atStartOfDay(ZONE).toInstant(), saved.getEffectiveFrom());
    }

    @Test
    void create_withZeroAmount_isAccepted() throws Exception {
        mockMvc.perform(post("/depreciation-rates").with(systemAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountPerKm\":0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amountPerKm").value(0.0));
    }

    @Test
    void create_withNegativeAmount_returnsValidationErrorAndSavesNothing() throws Exception {
        mockMvc.perform(post("/depreciation-rates").with(systemAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountPerKm\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_ERROR))
                .andExpect(jsonPath("$.message", containsString("amountPerKm")));

        assertEquals(0, depreciationRateRepository.count());
        assertEquals(0, count("SELECT COUNT(*) FROM outbox_events"));
    }

    @Test
    void create_withMoreThanFourDecimals_returnsValidationError() throws Exception {
        mockMvc.perform(post("/depreciation-rates").with(systemAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountPerKm\":0.12345}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_ERROR));

        assertEquals(0, depreciationRateRepository.count());
    }

    @Test
    void create_asBranchAdmin_returnsAccessDenied() throws Exception {
        mockMvc.perform(post("/depreciation-rates").with(branchAdmin(BRANCH_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountPerKm\":0.75}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCodes.ACCESS_DENIED));

        assertEquals(0, depreciationRateRepository.count());
    }

    @Test
    void create_withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/depreciation-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountPerKm\":0.75}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void current_withoutRates_returnsNotFound() throws Exception {
        mockMvc.perform(get("/depreciation-rates/current").with(systemAdmin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCodes.DEPRECIATION_RATE_NOT_FOUND));
    }

    @Test
    void current_returnsLatestStartedRateIgnoringScheduledOnes() throws Exception {
        Instant now = Instant.now();
        saveRate("0.5000", now.minus(Duration.ofDays(10)));
        UUID currentId = saveRate("0.7500", now.minus(Duration.ofDays(1))).getId();
        saveRate("1.0000", now.plus(Duration.ofDays(5)));

        mockMvc.perform(get("/depreciation-rates/current").with(systemAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(currentId.toString()))
                .andExpect(jsonPath("$.amountPerKm").value(0.75));
    }

    @Test
    void current_withOnlyScheduledRate_returnsNotFound() throws Exception {
        saveRate("1.0000", Instant.now().plus(Duration.ofDays(5)));

        mockMvc.perform(get("/depreciation-rates/current").with(systemAdmin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCodes.DEPRECIATION_RATE_NOT_FOUND));
    }

    @Test
    void search_returnsHistoryByEffectiveDateDescendingWithPaging() throws Exception {
        Instant now = Instant.now();
        UUID past = saveRate("0.5000", now.minus(Duration.ofDays(10))).getId();
        UUID current = saveRate("0.7500", now.minus(Duration.ofDays(1))).getId();
        UUID scheduled = saveRate("1.0000", now.plus(Duration.ofDays(5))).getId();

        mockMvc.perform(get("/depreciation-rates").with(systemAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].id").value(scheduled.toString()))
                .andExpect(jsonPath("$.content[1].id").value(current.toString()))
                .andExpect(jsonPath("$.content[2].id").value(past.toString()));

        mockMvc.perform(get("/depreciation-rates").param("page", "1").param("size", "2").with(systemAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(past.toString()));
    }

    @Test
    void search_asBranchAdmin_returnsAccessDenied() throws Exception {
        mockMvc.perform(get("/depreciation-rates").with(branchAdmin(BRANCH_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCodes.ACCESS_DENIED));
    }
}
