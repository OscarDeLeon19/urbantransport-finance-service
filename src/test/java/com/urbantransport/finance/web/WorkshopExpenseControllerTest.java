package com.urbantransport.finance.web;

import com.urbantransport.finance.config.SecurityConfig;
import com.urbantransport.finance.domain.ExpenseLineType;
import com.urbantransport.finance.dto.CreateWorkshopExpenseRequest;
import com.urbantransport.finance.dto.PageResponse;
import com.urbantransport.finance.dto.WorkshopExpenseLineResponse;
import com.urbantransport.finance.dto.WorkshopExpenseResponse;
import com.urbantransport.finance.dto.WorkshopExpenseSummaryResponse;
import com.urbantransport.finance.exception.ErrorCodes;
import com.urbantransport.finance.service.WorkshopExpenseService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
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

@WebMvcTest(WorkshopExpenseController.class)
@Import(SecurityConfig.class)
class WorkshopExpenseControllerTest {

    private static final UUID EXPENSE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BRANCH_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OTHER_BRANCH_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID BUS_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID CATEGORY_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID USER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Guatemala"));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WorkshopExpenseService workshopExpenseService;

    @Test
    void register_asBranchAdmin_usesBranchAndUserFromToken() throws Exception {
        when(workshopExpenseService.register(any(), eq(BRANCH_ID), eq(USER_ID))).thenReturn(expense());

        mockMvc.perform(post("/workshop-expenses").with(user("BRANCH_ADMIN", BRANCH_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"busId":"%s","serviceDate":"%s","lines":[
                                  {"categoryId":"%s","description":"Llanta","type":"PART","amount":1200.00}]}
                                """.formatted(BUS_ID, TODAY, CATEGORY_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(EXPENSE_ID.toString()))
                .andExpect(jsonPath("$.lines[0].categoryName").value("Llantas"));

        ArgumentCaptor<CreateWorkshopExpenseRequest> captor = ArgumentCaptor.forClass(CreateWorkshopExpenseRequest.class);
        verify(workshopExpenseService).register(captor.capture(), eq(BRANCH_ID), eq(USER_ID));
        CreateWorkshopExpenseRequest request = captor.getValue();
        assertEquals(BUS_ID, request.busId());
        assertEquals(TODAY, request.serviceDate());
        assertEquals(CATEGORY_ID, request.lines().getFirst().categoryId());
        assertEquals(ExpenseLineType.PART, request.lines().getFirst().type());
        assertEquals(0, new BigDecimal("1200").compareTo(request.lines().getFirst().amount()));
    }

    @Test
    void register_withoutLines_returnsValidationError() throws Exception {
        mockMvc.perform(post("/workshop-expenses").with(user("BRANCH_ADMIN", BRANCH_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"busId\":\"%s\",\"lines\":[]}".formatted(BUS_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_ERROR))
                .andExpect(jsonPath("$.message", containsString("lines")));

        verifyNoInteractions(workshopExpenseService);
    }

    @Test
    void register_withInvalidLine_reportsEachFieldWithItsIndex() throws Exception {
        mockMvc.perform(post("/workshop-expenses").with(user("BRANCH_ADMIN", BRANCH_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lines":[{"description":"%s","amount":0}]}
                                """.formatted("x".repeat(256))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_ERROR))
                .andExpect(jsonPath("$.message", containsString("busId")))
                .andExpect(jsonPath("$.message", containsString("lines[0].categoryId")))
                .andExpect(jsonPath("$.message", containsString("lines[0].type")))
                .andExpect(jsonPath("$.message", containsString("lines[0].amount")))
                .andExpect(jsonPath("$.message", containsString("lines[0].description")));

        verifyNoInteractions(workshopExpenseService);
    }

    @Test
    void register_withAmountWithThreeDecimals_returnsValidationError() throws Exception {
        mockMvc.perform(post("/workshop-expenses").with(user("BRANCH_ADMIN", BRANCH_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"busId":"%s","lines":[{"categoryId":"%s","type":"LABOR","amount":10.555}]}
                                """.formatted(BUS_ID, CATEGORY_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("lines[0].amount")));

        verifyNoInteractions(workshopExpenseService);
    }

    @Test
    void register_withUnknownLineType_returnsUnreadableBody() throws Exception {
        mockMvc.perform(post("/workshop-expenses").with(user("BRANCH_ADMIN", BRANCH_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"busId":"%s","lines":[{"categoryId":"%s","type":"OTRO","amount":10}]}
                                """.formatted(BUS_ID, CATEGORY_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_ERROR));

        verifyNoInteractions(workshopExpenseService);
    }

    @Test
    void register_asSystemAdmin_returnsAccessDenied() throws Exception {
        mockMvc.perform(post("/workshop-expenses").with(user("SYSTEM_ADMIN", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCodes.ACCESS_DENIED));

        verifyNoInteractions(workshopExpenseService);
    }

    @Test
    void register_withTokenWithoutBranch_returnsBranchRequired() throws Exception {
        mockMvc.perform(post("/workshop-expenses").with(user("BRANCH_ADMIN", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCodes.BRANCH_REQUIRED));

        verifyNoInteractions(workshopExpenseService);
    }

    @Test
    void register_withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/workshop-expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(workshopExpenseService);
    }

    @Test
    void search_asBranchAdmin_forcesOwnBranchAndPassesFilters() throws Exception {
        when(workshopExpenseService.search(BRANCH_ID, BUS_ID, CATEGORY_ID, TODAY.minusDays(7), TODAY, 1, 5))
                .thenReturn(page());

        mockMvc.perform(get("/workshop-expenses").with(user("BRANCH_ADMIN", BRANCH_ID))
                        .param("busId", BUS_ID.toString())
                        .param("categoryId", CATEGORY_ID.toString())
                        .param("from", TODAY.minusDays(7).toString())
                        .param("to", TODAY.toString())
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(EXPENSE_ID.toString()));
    }

    @Test
    void search_asBranchAdminWithOwnBranch_isAccepted() throws Exception {
        when(workshopExpenseService.search(BRANCH_ID, null, null, null, null, 0, 20)).thenReturn(page());

        mockMvc.perform(get("/workshop-expenses").param("branchId", BRANCH_ID.toString())
                        .with(user("BRANCH_ADMIN", BRANCH_ID)))
                .andExpect(status().isOk());
    }

    @Test
    void search_asBranchAdminForOtherBranch_returnsBranchForbidden() throws Exception {
        mockMvc.perform(get("/workshop-expenses").param("branchId", OTHER_BRANCH_ID.toString())
                        .with(user("BRANCH_ADMIN", BRANCH_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCodes.BRANCH_FORBIDDEN));

        verifyNoInteractions(workshopExpenseService);
    }

    @Test
    void search_asSystemAdmin_passesRequestedBranchOrAll() throws Exception {
        when(workshopExpenseService.search(OTHER_BRANCH_ID, null, null, null, null, 0, 20)).thenReturn(page());
        when(workshopExpenseService.search(null, null, null, null, null, 0, 20)).thenReturn(page());

        mockMvc.perform(get("/workshop-expenses").param("branchId", OTHER_BRANCH_ID.toString())
                        .with(user("SYSTEM_ADMIN", null)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/workshop-expenses").with(user("SYSTEM_ADMIN", null)))
                .andExpect(status().isOk());

        verify(workshopExpenseService).search(OTHER_BRANCH_ID, null, null, null, null, 0, 20);
        verify(workshopExpenseService).search(null, null, null, null, null, 0, 20);
    }

    @Test
    void search_asBranchAdminWithoutBranch_returnsBranchRequired() throws Exception {
        mockMvc.perform(get("/workshop-expenses").with(user("BRANCH_ADMIN", null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCodes.BRANCH_REQUIRED));

        verifyNoInteractions(workshopExpenseService);
    }

    @Test
    void findById_asBranchAdmin_isScopedToOwnBranch() throws Exception {
        when(workshopExpenseService.findById(EXPENSE_ID, BRANCH_ID)).thenReturn(expense());

        mockMvc.perform(get("/workshop-expenses/{id}", EXPENSE_ID).with(user("BRANCH_ADMIN", BRANCH_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1200.0));
    }

    @Test
    void findById_asSystemAdmin_isNotScoped() throws Exception {
        when(workshopExpenseService.findById(EXPENSE_ID, null)).thenReturn(expense());

        mockMvc.perform(get("/workshop-expenses/{id}", EXPENSE_ID).with(user("SYSTEM_ADMIN", null)))
                .andExpect(status().isOk());

        verify(workshopExpenseService).findById(EXPENSE_ID, null);
    }

    @Test
    void findById_asCustomer_returnsAccessDenied() throws Exception {
        mockMvc.perform(get("/workshop-expenses/{id}", EXPENSE_ID).with(user("CUSTOMER", null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCodes.ACCESS_DENIED));

        verifyNoInteractions(workshopExpenseService);
    }

    private static String validBody() {
        return """
                {"busId":"%s","lines":[{"categoryId":"%s","type":"PART","amount":100}]}
                """.formatted(BUS_ID, CATEGORY_ID);
    }

    private static WorkshopExpenseResponse expense() {
        return new WorkshopExpenseResponse(EXPENSE_ID, BUS_ID, BRANCH_ID, TODAY, new BigDecimal("1200.00"), USER_ID,
                Instant.parse("2026-10-01T15:00:00Z"), List.of(new WorkshopExpenseLineResponse(
                UUID.fromString("66666666-6666-6666-6666-666666666666"), CATEGORY_ID, "Llantas", "Llanta",
                ExpenseLineType.PART, new BigDecimal("1200.00"))));
    }

    private static PageResponse<WorkshopExpenseSummaryResponse> page() {
        return new PageResponse<>(List.of(new WorkshopExpenseSummaryResponse(EXPENSE_ID, BUS_ID, BRANCH_ID, TODAY,
                new BigDecimal("1200.00"), USER_ID, Instant.parse("2026-10-01T15:00:00Z"))), 0, 20, 1, 1);
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
