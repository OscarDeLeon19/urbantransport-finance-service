package com.urbantransport.finance.web;

import com.urbantransport.finance.config.SecurityConfig;
import com.urbantransport.finance.dto.CreateExpenseCategoryRequest;
import com.urbantransport.finance.dto.ExpenseCategoryResponse;
import com.urbantransport.finance.dto.UpdateExpenseCategoryRequest;
import com.urbantransport.finance.exception.ApiException;
import com.urbantransport.finance.exception.ErrorCodes;
import com.urbantransport.finance.service.ExpenseCategoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExpenseCategoryController.class)
@Import(SecurityConfig.class)
class ExpenseCategoryControllerTest {

    private static final UUID CATEGORY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BRANCH_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant CREATED_AT = Instant.parse("2026-10-01T15:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExpenseCategoryService expenseCategoryService;

    @ParameterizedTest
    @ValueSource(strings = {"true", "false"})
    void findAll_asSystemAdmin_passesActiveFilter(String active) throws Exception {
        when(expenseCategoryService.findAll(Boolean.valueOf(active))).thenReturn(List.of(category("Llantas", true)));

        mockMvc.perform(get("/expense-categories").param("active", active).with(user("SYSTEM_ADMIN", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Llantas"));
    }

    @Test
    void findAll_asSystemAdminWithoutFilter_returnsAll() throws Exception {
        when(expenseCategoryService.findAll(null))
                .thenReturn(List.of(category("Llantas", true), category("Motor", false)));

        mockMvc.perform(get("/expense-categories").with(user("SYSTEM_ADMIN", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].active").value(false));
    }

    @Test
    void findAll_asBranchAdminAskingInactive_onlyGetsActive() throws Exception {
        when(expenseCategoryService.findAll(true)).thenReturn(List.of(category("Llantas", true)));

        mockMvc.perform(get("/expense-categories").param("active", "false").with(user("BRANCH_ADMIN", BRANCH_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].active").value(true));

        verify(expenseCategoryService).findAll(true);
    }

    @Test
    void findAll_withTokenWithoutRolesClaim_onlyGetsActive() throws Exception {
        when(expenseCategoryService.findAll(true)).thenReturn(List.of());

        mockMvc.perform(get("/expense-categories").with(jwt()
                        .jwt(token -> token.subject("99999999-9999-9999-9999-999999999999"))
                        .authorities(new SimpleGrantedAuthority("ROLE_BRANCH_ADMIN"))))
                .andExpect(status().isOk());

        verify(expenseCategoryService).findAll(true);
    }

    @Test
    void findAll_asCashier_returnsAccessDenied() throws Exception {
        mockMvc.perform(get("/expense-categories").with(user("CASHIER", BRANCH_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCodes.ACCESS_DENIED));

        verifyNoInteractions(expenseCategoryService);
    }

    @Test
    void findAll_withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/expense-categories"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(expenseCategoryService);
    }

    @Test
    void create_asSystemAdmin_returnsCreated() throws Exception {
        when(expenseCategoryService.create(new CreateExpenseCategoryRequest("Transmision")))
                .thenReturn(category("Transmision", true));

        mockMvc.perform(post("/expense-categories").with(user("SYSTEM_ADMIN", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Transmision\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(CATEGORY_ID.toString()))
                .andExpect(jsonPath("$.active").value(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"name\":\"   \"}"})
    void create_withBlankName_returnsValidationError(String body) throws Exception {
        mockMvc.perform(post("/expense-categories").with(user("SYSTEM_ADMIN", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_ERROR))
                .andExpect(jsonPath("$.message", containsString("name")));

        verifyNoInteractions(expenseCategoryService);
    }

    @Test
    void create_withNameLongerThan80_returnsValidationError() throws Exception {
        mockMvc.perform(post("/expense-categories").with(user("SYSTEM_ADMIN", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + "a".repeat(81) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_ERROR));

        verifyNoInteractions(expenseCategoryService);
    }

    @Test
    void create_asBranchAdmin_returnsAccessDenied() throws Exception {
        mockMvc.perform(post("/expense-categories").with(user("BRANCH_ADMIN", BRANCH_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Transmision\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCodes.ACCESS_DENIED));

        verifyNoInteractions(expenseCategoryService);
    }

    @Test
    void create_duplicatedName_returnsConflictFromService() throws Exception {
        when(expenseCategoryService.create(new CreateExpenseCategoryRequest("Llantas")))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, ErrorCodes.CATEGORY_ALREADY_EXISTS, "Duplicada"));

        mockMvc.perform(post("/expense-categories").with(user("SYSTEM_ADMIN", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Llantas\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCodes.CATEGORY_ALREADY_EXISTS))
                .andExpect(jsonPath("$.path").value("/expense-categories"));
    }

    @Test
    void update_asSystemAdmin_renamesCategory() throws Exception {
        when(expenseCategoryService.update(CATEGORY_ID, new UpdateExpenseCategoryRequest("Motor")))
                .thenReturn(category("Motor", true));

        mockMvc.perform(put("/expense-categories/{id}", CATEGORY_ID).with(user("SYSTEM_ADMIN", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Motor\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Motor"));
    }

    @Test
    void update_withBlankName_returnsValidationError() throws Exception {
        mockMvc.perform(put("/expense-categories/{id}", CATEGORY_ID).with(user("SYSTEM_ADMIN", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_ERROR));

        verifyNoInteractions(expenseCategoryService);
    }

    @Test
    void deactivate_asSystemAdmin_returnsInactiveCategory() throws Exception {
        when(expenseCategoryService.deactivate(CATEGORY_ID)).thenReturn(category("Llantas", false));

        mockMvc.perform(patch("/expense-categories/{id}/deactivate", CATEGORY_ID).with(user("SYSTEM_ADMIN", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void deactivate_asBranchAdmin_returnsAccessDenied() throws Exception {
        mockMvc.perform(patch("/expense-categories/{id}/deactivate", CATEGORY_ID).with(user("BRANCH_ADMIN", BRANCH_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCodes.ACCESS_DENIED));

        verifyNoInteractions(expenseCategoryService);
    }

    @Test
    void delete_asSystemAdmin_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/expense-categories/{id}", CATEGORY_ID).with(user("SYSTEM_ADMIN", null)))
                .andExpect(status().isNoContent());

        verify(expenseCategoryService).delete(CATEGORY_ID);
    }

    @Test
    void delete_categoryInUse_returnsUnprocessable() throws Exception {
        doThrow(new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCodes.CATEGORY_IN_USE, "En uso"))
                .when(expenseCategoryService).delete(CATEGORY_ID);

        mockMvc.perform(delete("/expense-categories/{id}", CATEGORY_ID).with(user("SYSTEM_ADMIN", null)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value(ErrorCodes.CATEGORY_IN_USE));
    }

    private static ExpenseCategoryResponse category(String name, boolean active) {
        return new ExpenseCategoryResponse(CATEGORY_ID, name, active, CREATED_AT, CREATED_AT);
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
