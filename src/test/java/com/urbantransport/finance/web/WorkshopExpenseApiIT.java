package com.urbantransport.finance.web;

import com.urbantransport.finance.AbstractIntegrationTest;
import com.urbantransport.finance.domain.ExpenseLineType;
import com.urbantransport.finance.domain.WorkshopExpenseEntity;
import com.urbantransport.finance.domain.WorkshopExpenseLineEntity;
import com.urbantransport.finance.exception.ApiException;
import com.urbantransport.finance.exception.ErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkshopExpenseApiIT extends AbstractIntegrationTest {

    private static final UUID BRANCH_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_BRANCH_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID BUS_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID OTHER_BUS_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Autowired
    private JsonMapper jsonMapper;

    private UUID tiresId;
    private UUID oilId;

    @BeforeEach
    void loadCategories() {
        tiresId = categoryId("Llantas");
        oilId = categoryId("Cambio de aceite");
    }

    @Test
    void register_persistsHeaderLinesTotalAndEvent() throws Exception {
        givenBus(BUS_ID, BRANCH_ID);

        MvcResult result = mockMvc.perform(post("/workshop-expenses").with(branchAdmin(BRANCH_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"busId":"%s","lines":[
                                  {"categoryId":"%s","description":" Cambio de llantas ","type":"PART","amount":1200.00},
                                  {"categoryId":"%s","description":"  ","type":"LABOR","amount":150}]}
                                """.formatted(BUS_ID, tiresId, oilId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.busId").value(BUS_ID.toString()))
                .andExpect(jsonPath("$.branchId").value(BRANCH_ID.toString()))
                .andExpect(jsonPath("$.serviceDate").value(today().toString()))
                .andExpect(jsonPath("$.total").value(1350.0))
                .andExpect(jsonPath("$.createdBy").value(USER_ID.toString()))
                .andExpect(jsonPath("$.createdAt", notNullValue()))
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.lines[0].categoryName").value("Llantas"))
                .andExpect(jsonPath("$.lines[0].description").value("Cambio de llantas"))
                .andExpect(jsonPath("$.lines[1].description").doesNotExist())
                .andReturn();

        UUID expenseId = idAt(result, "$.id");
        WorkshopExpenseEntity saved = workshopExpenseRepository.findById(expenseId).orElseThrow();
        assertEquals(0, new BigDecimal("1350.00").compareTo(saved.getTotal()));
        assertEquals(BRANCH_ID, saved.getBranchId());
        assertEquals(today(), saved.getServiceDate());
        List<WorkshopExpenseLineEntity> lines = workshopExpenseLineRepository.findByExpenseId(expenseId);
        assertEquals(2, lines.size());
        assertTrue(lines.stream().anyMatch(line -> line.getDescription() == null));
        assertEquals(List.of("WorkshopExpenseRegistered"), outboxEventTypes(expenseId));

        JsonNode payload = jsonMapper.readTree(outboxPayload(expenseId)).get("payload");
        assertEquals(expenseId.toString(), payload.get("expenseId").asString());
        assertEquals(2, payload.get("lines").size());
        assertEquals("Llantas", payload.get("lines").get(0).get("categoryName").asString());
        assertEquals("PART", payload.get("lines").get(0).get("type").asString());
    }

    @Test
    void register_withExplicitServiceDate_keepsIt() throws Exception {
        givenBus(BUS_ID, BRANCH_ID);

        mockMvc.perform(post("/workshop-expenses").with(branchAdmin(BRANCH_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(today().minusDays(3).toString(), tiresId, "100")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serviceDate").value(today().minusDays(3).toString()));
    }

    @Test
    void register_withoutLines_returnsValidationErrorWithoutCallingFleet() throws Exception {
        mockMvc.perform(post("/workshop-expenses").with(branchAdmin(BRANCH_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"busId\":\"%s\",\"lines\":[]}".formatted(BUS_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_ERROR))
                .andExpect(jsonPath("$.message", containsString("lines")));

        verifyNoInteractions(fleetClient);
        assertEquals(0, workshopExpenseRepository.count());
    }

    @Test
    void register_withInvalidLine_returnsFieldErrorsWithIndex() throws Exception {
        mockMvc.perform(post("/workshop-expenses").with(branchAdmin(BRANCH_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"busId":"%s","lines":[{"categoryId":"%s","type":"PART","amount":10},
                                  {"type":"LABOR","amount":0}]}
                                """.formatted(BUS_ID, tiresId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_ERROR))
                .andExpect(jsonPath("$.message", containsString("lines[1].categoryId")))
                .andExpect(jsonPath("$.message", containsString("lines[1].amount")));

        verifyNoInteractions(fleetClient);
        assertEquals(0, workshopExpenseRepository.count());
    }

    @Test
    void register_withInactiveCategory_returnsBadRequestAndSavesNothing() throws Exception {
        UUID inactiveId = saveCategory("Pintura", false).getId();

        mockMvc.perform(post("/workshop-expenses").with(branchAdmin(BRANCH_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(null, inactiveId, "100")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.CATEGORY_INACTIVE));

        verifyNoInteractions(fleetClient);
        assertEquals(0, workshopExpenseRepository.count());
    }

    @Test
    void register_withUnknownCategory_returnsNotFound() throws Exception {
        mockMvc.perform(post("/workshop-expenses").with(branchAdmin(BRANCH_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(null, UUID.randomUUID(), "100")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCodes.CATEGORY_NOT_FOUND));

        verifyNoInteractions(fleetClient);
    }

    @Test
    void register_withBusOfOtherBranch_returnsForbiddenAndSavesNothing() throws Exception {
        givenBus(BUS_ID, OTHER_BRANCH_ID);

        mockMvc.perform(post("/workshop-expenses").with(branchAdmin(BRANCH_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(null, tiresId, "100")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCodes.BUS_NOT_IN_BRANCH));

        assertEquals(0, workshopExpenseRepository.count());
        assertEquals(0, count("SELECT COUNT(*) FROM outbox_events"));
    }

    @Test
    void register_whenFleetIsUnavailable_returnsServiceUnavailableAndSavesNothing() throws Exception {
        when(fleetClient.getBus(BUS_ID)).thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                ErrorCodes.FLEET_UNAVAILABLE, "El servicio de flota no esta disponible"));

        mockMvc.perform(post("/workshop-expenses").with(branchAdmin(BRANCH_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(null, tiresId, "100")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(ErrorCodes.FLEET_UNAVAILABLE));

        assertEquals(0, workshopExpenseRepository.count());
        assertEquals(0, workshopExpenseLineRepository.count());
        assertEquals(0, count("SELECT COUNT(*) FROM outbox_events"));
    }

    @Test
    void register_withUnknownBus_returnsNotFound() throws Exception {
        when(fleetClient.getBus(BUS_ID)).thenThrow(new ApiException(HttpStatus.NOT_FOUND, ErrorCodes.BUS_NOT_FOUND,
                "Bus no encontrado"));

        mockMvc.perform(post("/workshop-expenses").with(branchAdmin(BRANCH_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(null, tiresId, "100")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCodes.BUS_NOT_FOUND));

        assertEquals(0, workshopExpenseRepository.count());
    }

    @Test
    void register_asSystemAdmin_returnsAccessDenied() throws Exception {
        mockMvc.perform(post("/workshop-expenses").with(systemAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(null, tiresId, "100")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCodes.ACCESS_DENIED));

        verifyNoInteractions(fleetClient);
    }

    @Test
    void register_withTokenWithoutBranch_returnsBranchRequired() throws Exception {
        mockMvc.perform(post("/workshop-expenses").with(userWithRole("BRANCH_ADMIN", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(null, tiresId, "100")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCodes.BRANCH_REQUIRED));

        verifyNoInteractions(fleetClient);
    }

    @Test
    void register_withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/workshop-expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(null, tiresId, "100")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void search_asBranchAdmin_returnsOwnBranchNewestFirstWithFilters() throws Exception {
        UUID older = saveExpense(BRANCH_ID, BUS_ID, today().minusDays(10), tiresId, "1200.00").getId();
        UUID newer = saveExpense(BRANCH_ID, OTHER_BUS_ID, today().minusDays(1), oilId, "150.00").getId();
        saveExpense(OTHER_BRANCH_ID, BUS_ID, today(), tiresId, "999.00");

        mockMvc.perform(get("/workshop-expenses").with(branchAdmin(BRANCH_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(newer.toString()))
                .andExpect(jsonPath("$.content[1].id").value(older.toString()))
                .andExpect(jsonPath("$.content[0].lines").doesNotExist());

        mockMvc.perform(get("/workshop-expenses").param("busId", BUS_ID.toString()).with(branchAdmin(BRANCH_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(older.toString()));

        mockMvc.perform(get("/workshop-expenses").param("categoryId", oilId.toString()).with(branchAdmin(BRANCH_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(newer.toString()));

        mockMvc.perform(get("/workshop-expenses").with(branchAdmin(BRANCH_ID))
                        .param("from", today().minusDays(10).toString())
                        .param("to", today().minusDays(2).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(older.toString()));
    }

    @Test
    void search_withCategoryInSeveralLines_returnsExpenseOnce() throws Exception {
        WorkshopExpenseEntity expense = saveExpense(BRANCH_ID, BUS_ID, today(), tiresId, "100.00");
        workshopExpenseLineRepository.save(WorkshopExpenseLineEntity.builder()
                .expenseId(expense.getId())
                .categoryId(tiresId)
                .type(ExpenseLineType.LABOR)
                .amount(new BigDecimal("50.00"))
                .build());

        mockMvc.perform(get("/workshop-expenses").param("categoryId", tiresId.toString()).with(branchAdmin(BRANCH_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void search_asBranchAdminForOtherBranch_returnsBranchForbidden() throws Exception {
        mockMvc.perform(get("/workshop-expenses").param("branchId", OTHER_BRANCH_ID.toString())
                        .with(branchAdmin(BRANCH_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCodes.BRANCH_FORBIDDEN));
    }

    @Test
    void search_asSystemAdmin_seesAllBranchesOrFilters() throws Exception {
        saveExpense(BRANCH_ID, BUS_ID, today(), tiresId, "100.00");
        UUID other = saveExpense(OTHER_BRANCH_ID, OTHER_BUS_ID, today(), tiresId, "200.00").getId();

        mockMvc.perform(get("/workshop-expenses").with(systemAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/workshop-expenses").param("branchId", OTHER_BRANCH_ID.toString()).with(systemAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(other.toString()));
    }

    @Test
    void search_withToBeforeFrom_returnsInvalidDateRange() throws Exception {
        mockMvc.perform(get("/workshop-expenses").with(branchAdmin(BRANCH_ID))
                        .param("from", today().toString())
                        .param("to", today().minusDays(1).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.INVALID_DATE_RANGE));
    }

    @Test
    void search_paging_splitsResults() throws Exception {
        saveExpense(BRANCH_ID, BUS_ID, today(), tiresId, "100.00");
        saveExpense(BRANCH_ID, BUS_ID, today(), tiresId, "200.00");
        saveExpense(BRANCH_ID, BUS_ID, today(), tiresId, "300.00");

        mockMvc.perform(get("/workshop-expenses").param("page", "1").param("size", "2").with(branchAdmin(BRANCH_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].total").value(100.0));
    }

    @Test
    void findById_returnsLinesAndIsForbiddenForOtherBranch() throws Exception {
        UUID expenseId = saveExpense(OTHER_BRANCH_ID, BUS_ID, today(), tiresId, "1200.00").getId();

        mockMvc.perform(get("/workshop-expenses/{id}", expenseId).with(branchAdmin(BRANCH_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCodes.EXPENSE_NOT_IN_BRANCH));

        mockMvc.perform(get("/workshop-expenses/{id}", expenseId).with(branchAdmin(OTHER_BRANCH_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.lines[0].categoryName").value("Llantas"));

        mockMvc.perform(get("/workshop-expenses/{id}", expenseId).with(systemAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1200.0));
    }

    @Test
    void findById_afterCategoryRename_showsNewName() throws Exception {
        UUID categoryId = saveCategory("Pintura", true).getId();
        UUID expenseId = saveExpense(BRANCH_ID, BUS_ID, today(), categoryId, "80.00").getId();
        mockMvc.perform(put("/expense-categories/{id}", categoryId).with(systemAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Pintura general\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/workshop-expenses/{id}", expenseId).with(branchAdmin(BRANCH_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].categoryId").value(categoryId.toString()))
                .andExpect(jsonPath("$.lines[0].categoryName").value("Pintura general"));
    }

    @Test
    void findById_unknownExpense_returnsNotFound() throws Exception {
        mockMvc.perform(get("/workshop-expenses/{id}", UUID.randomUUID()).with(systemAdmin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCodes.EXPENSE_NOT_FOUND));
    }

    private static String body(String serviceDate, UUID categoryId, String amount) {
        String date = serviceDate == null ? "" : "\"serviceDate\":\"%s\",".formatted(serviceDate);
        return """
                {"busId":"%s",%s"lines":[{"categoryId":"%s","type":"PART","amount":%s}]}
                """.formatted(BUS_ID, date, categoryId, amount);
    }
}
