package com.urbantransport.finance.web;

import com.jayway.jsonpath.JsonPath;
import com.urbantransport.finance.AbstractIntegrationTest;
import com.urbantransport.finance.domain.ExpenseCategoryEntity;
import com.urbantransport.finance.exception.ErrorCodes;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExpenseCategoryApiIT extends AbstractIntegrationTest {

    private static final UUID BRANCH_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void findAll_asSystemAdmin_returnsSeededAndCreatedSortedByName() throws Exception {
        saveCategory("Zeta inactiva", false);

        mockMvc.perform(get("/expense-categories").with(systemAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(SEEDED_CATEGORIES.size() + 1))
                .andExpect(jsonPath("$[0].name").value("Cambio de aceite"))
                .andExpect(jsonPath("$[%d].name".formatted(SEEDED_CATEGORIES.size())).value("Zeta inactiva"));

        mockMvc.perform(get("/expense-categories").param("active", "false").with(systemAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].active").value(false));
    }

    @Test
    void findAll_asBranchAdminAskingInactive_onlyGetsActive() throws Exception {
        saveCategory("Zeta inactiva", false);

        mockMvc.perform(get("/expense-categories").param("active", "false").with(branchAdmin(BRANCH_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(SEEDED_CATEGORIES.size()))
                .andExpect(jsonPath("$[?(@.active == false)]").isEmpty());
    }

    @Test
    void findAll_asCashier_returnsAccessDenied() throws Exception {
        mockMvc.perform(get("/expense-categories").with(userWithRole("CASHIER", BRANCH_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCodes.ACCESS_DENIED));
    }

    @Test
    void create_persistsTrimmedActiveCategoryWithoutEvent() throws Exception {
        MvcResult result = mockMvc.perform(post("/expense-categories").with(systemAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  Transmision  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Transmision"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.createdAt", notNullValue()))
                .andExpect(jsonPath("$.updatedAt", notNullValue()))
                .andReturn();

        UUID categoryId = idAt(result, "$.id");
        ExpenseCategoryEntity saved = expenseCategoryRepository.findById(categoryId).orElseThrow();
        assertEquals("Transmision", saved.getName());
        assertTrue(saved.isActive());
        assertEquals(0, count("SELECT COUNT(*) FROM outbox_events"));
    }

    @Test
    void create_withSeededNameInOtherCase_returnsConflict() throws Exception {
        mockMvc.perform(post("/expense-categories").with(systemAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"LLANTAS\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCodes.CATEGORY_ALREADY_EXISTS));

        assertEquals(SEEDED_CATEGORIES.size(), expenseCategoryRepository.count());
    }

    @Test
    void create_withNameOfInactiveCategory_returnsConflict() throws Exception {
        saveCategory("Pintura", false);

        mockMvc.perform(post("/expense-categories").with(systemAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"pintura\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCodes.CATEGORY_ALREADY_EXISTS));
    }

    @Test
    void create_withBlankName_returnsValidationError() throws Exception {
        mockMvc.perform(post("/expense-categories").with(systemAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.VALIDATION_ERROR))
                .andExpect(jsonPath("$.message", containsString("name")))
                .andExpect(jsonPath("$.path").value("/expense-categories"));

        assertEquals(SEEDED_CATEGORIES.size(), expenseCategoryRepository.count());
    }

    @Test
    void create_asBranchAdmin_returnsAccessDenied() throws Exception {
        mockMvc.perform(post("/expense-categories").with(branchAdmin(BRANCH_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Transmision\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCodes.ACCESS_DENIED));

        assertEquals(SEEDED_CATEGORIES.size(), expenseCategoryRepository.count());
    }

    @Test
    void create_withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/expense-categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Transmision\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void update_renamesCategory() throws Exception {
        UUID categoryId = saveCategory("Pintura", true).getId();

        mockMvc.perform(put("/expense-categories/{id}", categoryId).with(systemAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" Pintura y enderezado \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Pintura y enderezado"));

        assertEquals("Pintura y enderezado", expenseCategoryRepository.findById(categoryId).orElseThrow().getName());
    }

    @Test
    void update_returnsUpdatedAtOfTheChange() throws Exception {
        ExpenseCategoryEntity category = saveCategory("Pintura", true);
        Instant previousUpdatedAt = category.getUpdatedAt();

        MvcResult result = mockMvc.perform(put("/expense-categories/{id}", category.getId()).with(systemAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Pintura general\"}"))
                .andExpect(status().isOk())
                .andReturn();

        Instant responseUpdatedAt = Instant.parse(JsonPath.read(result.getResponse().getContentAsString(),
                "$.updatedAt"));
        assertTrue(responseUpdatedAt.isAfter(previousUpdatedAt),
                "updatedAt de la respuesta (" + responseUpdatedAt + ") no cambió respecto a " + previousUpdatedAt);
    }

    @Test
    void update_sameNameInOtherCase_isAllowed() throws Exception {
        UUID categoryId = saveCategory("pintura", true).getId();

        mockMvc.perform(put("/expense-categories/{id}", categoryId).with(systemAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Pintura\"}"))
                .andExpect(status().isOk());

        assertEquals("Pintura", expenseCategoryRepository.findById(categoryId).orElseThrow().getName());
    }

    @Test
    void update_withNameOfOtherCategory_returnsConflictAndKeepsName() throws Exception {
        UUID categoryId = saveCategory("Pintura", true).getId();

        mockMvc.perform(put("/expense-categories/{id}", categoryId).with(systemAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"motor\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCodes.CATEGORY_ALREADY_EXISTS));

        assertEquals("Pintura", expenseCategoryRepository.findById(categoryId).orElseThrow().getName());
    }

    @Test
    void update_unknownCategory_returnsNotFound() throws Exception {
        mockMvc.perform(put("/expense-categories/{id}", UUID.randomUUID()).with(systemAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Pintura\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCodes.CATEGORY_NOT_FOUND));
    }

    @Test
    void deactivate_marksInactiveAndSecondCallIsRejected() throws Exception {
        UUID categoryId = saveCategory("Pintura", true).getId();

        mockMvc.perform(patch("/expense-categories/{id}/deactivate", categoryId).with(systemAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        assertFalse(expenseCategoryRepository.findById(categoryId).orElseThrow().isActive());

        mockMvc.perform(patch("/expense-categories/{id}/deactivate", categoryId).with(systemAdmin()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value(ErrorCodes.CATEGORY_INACTIVE));
    }

    @Test
    void delete_categoryWithoutExpenses_removesIt() throws Exception {
        UUID categoryId = saveCategory("Pintura", false).getId();

        mockMvc.perform(delete("/expense-categories/{id}", categoryId).with(systemAdmin()))
                .andExpect(status().isNoContent());

        assertFalse(expenseCategoryRepository.existsById(categoryId));
    }

    @Test
    void delete_categoryWithExpenses_returnsInUseAndKeepsIt() throws Exception {
        UUID categoryId = saveCategory("Pintura", true).getId();
        saveExpense(BRANCH_ID, UUID.randomUUID(), today(), categoryId, "250.00");

        mockMvc.perform(delete("/expense-categories/{id}", categoryId).with(systemAdmin()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value(ErrorCodes.CATEGORY_IN_USE));

        assertTrue(expenseCategoryRepository.existsById(categoryId));
    }

    @Test
    void delete_unknownCategory_returnsNotFound() throws Exception {
        mockMvc.perform(delete("/expense-categories/{id}", UUID.randomUUID()).with(systemAdmin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCodes.CATEGORY_NOT_FOUND));

        assertEquals(SEEDED_CATEGORIES.size(), expenseCategoryRepository.count());
    }
}
