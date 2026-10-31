package com.urbantransport.finance.service.impl;

import com.urbantransport.finance.domain.ExpenseCategoryEntity;
import com.urbantransport.finance.dto.CreateExpenseCategoryRequest;
import com.urbantransport.finance.dto.ExpenseCategoryResponse;
import com.urbantransport.finance.dto.UpdateExpenseCategoryRequest;
import com.urbantransport.finance.exception.ApiException;
import com.urbantransport.finance.exception.ErrorCodes;
import com.urbantransport.finance.repository.ExpenseCategoryRepository;
import com.urbantransport.finance.repository.WorkshopExpenseLineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseCategoryServiceImplTest {

    private static final UUID CATEGORY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_CATEGORY_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String NAME = "Llantas";
    private static final String NEW_NAME = "Llantas y rines";
    private static final Instant CREATED_AT = Instant.parse("2026-10-01T15:00:00Z");

    @Mock
    private ExpenseCategoryRepository expenseCategoryRepository;

    @Mock
    private WorkshopExpenseLineRepository workshopExpenseLineRepository;

    @Captor
    private ArgumentCaptor<ExpenseCategoryEntity> categoryCaptor;

    private ExpenseCategoryServiceImpl expenseCategoryService;

    @BeforeEach
    void setUp() {
        expenseCategoryService = new ExpenseCategoryServiceImpl(expenseCategoryRepository,
                workshopExpenseLineRepository);
    }

    @Nested
    class FindAll {

        @Test
        void withoutActiveFilter_returnsAllSortedByName() {
            when(expenseCategoryRepository.findAllByOrderByNameAsc())
                    .thenReturn(List.of(category(CATEGORY_ID, NAME, true), category(OTHER_CATEGORY_ID, "Motor", false)));

            List<ExpenseCategoryResponse> result = expenseCategoryService.findAll(null);

            assertEquals(2, result.size());
            assertEquals(NAME, result.getFirst().getName());
            assertTrue(result.getFirst().isActive());
            assertFalse(result.get(1).isActive());
            verify(expenseCategoryRepository, never()).findByActiveOrderByNameAsc(anyBoolean());
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void withActiveFilter_queriesOnlyThatState(boolean active) {
            when(expenseCategoryRepository.findByActiveOrderByNameAsc(active))
                    .thenReturn(List.of(category(CATEGORY_ID, NAME, active)));

            List<ExpenseCategoryResponse> result = expenseCategoryService.findAll(active);

            assertEquals(1, result.size());
            assertEquals(active, result.getFirst().isActive());
            verify(expenseCategoryRepository, never()).findAllByOrderByNameAsc();
        }
    }

    @Nested
    class Create {

        @Test
        void validName_savesTrimmedActiveCategory() {
            when(expenseCategoryRepository.save(any(ExpenseCategoryEntity.class))).thenAnswer(savedWithId());

            ExpenseCategoryResponse response = expenseCategoryService.create(new CreateExpenseCategoryRequest("  Llantas  "));

            verify(expenseCategoryRepository).existsByNameIgnoreCase(NAME);
            verify(expenseCategoryRepository).save(categoryCaptor.capture());
            assertEquals(NAME, categoryCaptor.getValue().getName());
            assertTrue(categoryCaptor.getValue().isActive());
            assertEquals(CATEGORY_ID, response.getId());
            assertEquals(NAME, response.getName());
            assertTrue(response.isActive());
        }

        @Test
        void duplicatedNameIgnoringCase_throwsConflict() {
            when(expenseCategoryRepository.existsByNameIgnoreCase("LLANTAS")).thenReturn(true);
            CreateExpenseCategoryRequest request = new CreateExpenseCategoryRequest(" LLANTAS ");

            assertApiError(HttpStatus.CONFLICT, ErrorCodes.CATEGORY_ALREADY_EXISTS,
                    () -> expenseCategoryService.create(request));

            verify(expenseCategoryRepository, never()).save(any());
        }
    }

    @Nested
    class Update {

        @Test
        void newName_renamesTrimmedCategory() {
            ExpenseCategoryEntity category = category(CATEGORY_ID, NAME, true);
            when(expenseCategoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category));
            when(expenseCategoryRepository.save(category)).thenReturn(category);

            ExpenseCategoryResponse response = expenseCategoryService.update(CATEGORY_ID,
                    new UpdateExpenseCategoryRequest("  Llantas y rines "));

            verify(expenseCategoryRepository).existsByNameIgnoreCaseAndIdNot(NEW_NAME, CATEGORY_ID);
            assertEquals(NEW_NAME, category.getName());
            assertEquals(NEW_NAME, response.getName());
            assertEquals(CREATED_AT, response.getCreatedAt());
        }

        @Test
        void nameOfAnotherCategory_throwsConflictAndKeepsName() {
            ExpenseCategoryEntity category = category(CATEGORY_ID, NAME, true);
            when(expenseCategoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category));
            when(expenseCategoryRepository.existsByNameIgnoreCaseAndIdNot("Motor", CATEGORY_ID)).thenReturn(true);
            UpdateExpenseCategoryRequest request = new UpdateExpenseCategoryRequest("Motor");

            assertApiError(HttpStatus.CONFLICT, ErrorCodes.CATEGORY_ALREADY_EXISTS,
                    () -> expenseCategoryService.update(CATEGORY_ID, request));

            assertEquals(NAME, category.getName());
            verify(expenseCategoryRepository, never()).save(any());
        }

        @Test
        void unknownCategory_throwsNotFound() {
            when(expenseCategoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.empty());
            UpdateExpenseCategoryRequest request = new UpdateExpenseCategoryRequest(NEW_NAME);

            assertApiError(HttpStatus.NOT_FOUND, ErrorCodes.CATEGORY_NOT_FOUND,
                    () -> expenseCategoryService.update(CATEGORY_ID, request));

            verify(expenseCategoryRepository, never()).existsByNameIgnoreCaseAndIdNot(any(), any());
            verify(expenseCategoryRepository, never()).save(any());
        }
    }

    @Nested
    class Deactivate {

        @Test
        void activeCategory_isDeactivated() {
            ExpenseCategoryEntity category = category(CATEGORY_ID, NAME, true);
            when(expenseCategoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category));
            when(expenseCategoryRepository.save(category)).thenReturn(category);

            ExpenseCategoryResponse response = expenseCategoryService.deactivate(CATEGORY_ID);

            assertFalse(category.isActive());
            assertFalse(response.isActive());
            assertEquals(CATEGORY_ID, response.getId());
        }

        @Test
        void alreadyInactiveCategory_throwsCategoryInactive() {
            when(expenseCategoryRepository.findById(CATEGORY_ID))
                    .thenReturn(Optional.of(category(CATEGORY_ID, NAME, false)));

            assertApiError(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCodes.CATEGORY_INACTIVE,
                    () -> expenseCategoryService.deactivate(CATEGORY_ID));

            verify(expenseCategoryRepository, never()).save(any());
        }

        @Test
        void unknownCategory_throwsNotFound() {
            when(expenseCategoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.empty());

            assertApiError(HttpStatus.NOT_FOUND, ErrorCodes.CATEGORY_NOT_FOUND,
                    () -> expenseCategoryService.deactivate(CATEGORY_ID));

            verify(expenseCategoryRepository, never()).save(any());
        }
    }

    @Nested
    class Delete {

        @Test
        void categoryWithoutExpenses_isDeleted() {
            ExpenseCategoryEntity category = category(CATEGORY_ID, NAME, false);
            when(expenseCategoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category));

            expenseCategoryService.delete(CATEGORY_ID);

            verify(workshopExpenseLineRepository).existsByCategoryId(CATEGORY_ID);
            verify(expenseCategoryRepository).delete(category);
        }

        @Test
        void categoryInUse_throwsCategoryInUseWithoutDeleting() {
            when(expenseCategoryRepository.findById(CATEGORY_ID))
                    .thenReturn(Optional.of(category(CATEGORY_ID, NAME, true)));
            when(workshopExpenseLineRepository.existsByCategoryId(CATEGORY_ID)).thenReturn(true);

            assertApiError(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCodes.CATEGORY_IN_USE,
                    () -> expenseCategoryService.delete(CATEGORY_ID));

            verify(expenseCategoryRepository, never()).delete(any(ExpenseCategoryEntity.class));
        }

        @Test
        void unknownCategory_throwsNotFound() {
            when(expenseCategoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.empty());

            assertApiError(HttpStatus.NOT_FOUND, ErrorCodes.CATEGORY_NOT_FOUND,
                    () -> expenseCategoryService.delete(CATEGORY_ID));

            verifyNoInteractions(workshopExpenseLineRepository);
            verify(expenseCategoryRepository, never()).delete(any(ExpenseCategoryEntity.class));
        }
    }

    private static ExpenseCategoryEntity category(UUID id, String name, boolean active) {
        return ExpenseCategoryEntity.builder()
                .id(id)
                .name(name)
                .active(active)
                .createdAt(CREATED_AT)
                .updatedAt(CREATED_AT)
                .build();
    }

    private static Answer<ExpenseCategoryEntity> savedWithId() {
        return invocation -> {
            ExpenseCategoryEntity category = invocation.getArgument(0);
            category.setId(CATEGORY_ID);
            return category;
        };
    }

    private static void assertApiError(HttpStatus status, String code, Executable action) {
        ApiException exception = assertThrows(ApiException.class, action);
        assertEquals(status, exception.getStatus());
        assertEquals(code, exception.getCode());
    }
}
