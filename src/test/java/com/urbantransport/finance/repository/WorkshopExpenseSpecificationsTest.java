package com.urbantransport.finance.repository;

import com.urbantransport.finance.domain.WorkshopExpenseEntity;
import com.urbantransport.finance.domain.WorkshopExpenseLineEntity;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkshopExpenseSpecificationsTest {

    private static final UUID BRANCH_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BUS_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CATEGORY_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final LocalDate TO = LocalDate.now(ZoneId.of("America/Guatemala"));
    private static final LocalDate FROM = TO.minusDays(30);

    @Mock
    private Root<WorkshopExpenseEntity> root;

    @Mock
    private Root<WorkshopExpenseLineEntity> lineRoot;

    @Mock
    private CriteriaQuery<Object> query;

    @Mock
    private Subquery<UUID> subquery;

    @Mock
    private CriteriaBuilder cb;

    @Mock
    private Path<Object> branchPath;

    @Mock
    private Path<Object> busPath;

    @Mock
    private Path<Object> idPath;

    @Mock
    private Path<UUID> lineExpensePath;

    @Mock
    private Path<Object> lineCategoryPath;

    @Mock
    private Path<LocalDate> serviceDatePath;

    @Mock
    private Predicate branchPredicate;

    @Mock
    private Predicate busPredicate;

    @Mock
    private Predicate sameExpensePredicate;

    @Mock
    private Predicate categoryPredicate;

    @Mock
    private Predicate existsPredicate;

    @Mock
    private Predicate fromPredicate;

    @Mock
    private Predicate toPredicate;

    @Mock
    private Predicate combined;

    @Test
    void allFilters_combineOnePredicatePerFilter() {
        doReturn(branchPath).when(root).get("branchId");
        doReturn(busPath).when(root).get("busId");
        doReturn(idPath).when(root).get("id");
        doReturn(serviceDatePath).when(root).get("serviceDate");
        doReturn(subquery).when(query).subquery(UUID.class);
        doReturn(lineRoot).when(subquery).from(WorkshopExpenseLineEntity.class);
        doReturn(lineExpensePath).when(lineRoot).get("expenseId");
        doReturn(lineCategoryPath).when(lineRoot).get("categoryId");
        doReturn(subquery).when(subquery).select(any());
        when(cb.equal(branchPath, BRANCH_ID)).thenReturn(branchPredicate);
        when(cb.equal(busPath, BUS_ID)).thenReturn(busPredicate);
        when(cb.equal(lineExpensePath, idPath)).thenReturn(sameExpensePredicate);
        when(cb.equal(lineCategoryPath, CATEGORY_ID)).thenReturn(categoryPredicate);
        when(cb.exists(subquery)).thenReturn(existsPredicate);
        when(cb.greaterThanOrEqualTo(serviceDatePath, FROM)).thenReturn(fromPredicate);
        when(cb.lessThanOrEqualTo(serviceDatePath, TO)).thenReturn(toPredicate);
        when(cb.and(branchPredicate, busPredicate, existsPredicate, fromPredicate, toPredicate)).thenReturn(combined);

        Predicate result = WorkshopExpenseSpecifications.filter(BRANCH_ID, BUS_ID, CATEGORY_ID, FROM, TO)
                .toPredicate(root, query, cb);

        assertSame(combined, result);
        verify(subquery).select(lineExpensePath);
        verify(subquery).where(sameExpensePredicate, categoryPredicate);
    }

    @Test
    void withoutFilters_returnsEmptyConjunction() {
        when(cb.and()).thenReturn(combined);

        Predicate result = WorkshopExpenseSpecifications.filter(null, null, null, null, null)
                .toPredicate(root, query, cb);

        assertSame(combined, result);
        verifyNoInteractions(root, query);
        verify(cb, never()).equal(any(), any(Object.class));
    }

    @Test
    void onlyCategory_usesExistsOverExpenseLines() {
        doReturn(idPath).when(root).get("id");
        doReturn(subquery).when(query).subquery(UUID.class);
        doReturn(lineRoot).when(subquery).from(WorkshopExpenseLineEntity.class);
        doReturn(lineExpensePath).when(lineRoot).get("expenseId");
        doReturn(lineCategoryPath).when(lineRoot).get("categoryId");
        doReturn(subquery).when(subquery).select(any());
        when(cb.equal(lineExpensePath, idPath)).thenReturn(sameExpensePredicate);
        when(cb.equal(lineCategoryPath, CATEGORY_ID)).thenReturn(categoryPredicate);
        when(cb.exists(subquery)).thenReturn(existsPredicate);
        when(cb.and(existsPredicate)).thenReturn(combined);

        Predicate result = WorkshopExpenseSpecifications.filter(null, null, CATEGORY_ID, null, null)
                .toPredicate(root, query, cb);

        assertSame(combined, result);
        verify(root, never()).get("branchId");
        verify(root, never()).get("busId");
        verify(root, never()).get("serviceDate");
    }
}
