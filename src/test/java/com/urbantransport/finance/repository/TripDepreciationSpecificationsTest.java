package com.urbantransport.finance.repository;

import com.urbantransport.finance.domain.TripDepreciationEntity;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripDepreciationSpecificationsTest {

    private static final UUID BRANCH_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BUS_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant FROM = Instant.parse("2026-10-01T06:00:00Z");
    private static final Instant TO_EXCLUSIVE = Instant.parse("2026-11-01T06:00:00Z");

    @Mock
    private Root<TripDepreciationEntity> root;

    @Mock
    private CriteriaQuery<Object> query;

    @Mock
    private CriteriaBuilder cb;

    @Mock
    private Path<Object> branchPath;

    @Mock
    private Path<Object> busPath;

    @Mock
    private Path<Instant> calculatedAtPath;

    @Mock
    private Predicate branchPredicate;

    @Mock
    private Predicate busPredicate;

    @Mock
    private Predicate fromPredicate;

    @Mock
    private Predicate toPredicate;

    @Mock
    private Predicate combined;

    @Test
    void allFilters_useInclusiveStartAndExclusiveEnd() {
        doReturn(branchPath).when(root).get("branchId");
        doReturn(busPath).when(root).get("busId");
        doReturn(calculatedAtPath).when(root).get("calculatedAt");
        when(cb.equal(branchPath, BRANCH_ID)).thenReturn(branchPredicate);
        when(cb.equal(busPath, BUS_ID)).thenReturn(busPredicate);
        when(cb.greaterThanOrEqualTo(calculatedAtPath, FROM)).thenReturn(fromPredicate);
        when(cb.lessThan(calculatedAtPath, TO_EXCLUSIVE)).thenReturn(toPredicate);
        when(cb.and(branchPredicate, busPredicate, fromPredicate, toPredicate)).thenReturn(combined);

        Predicate result = TripDepreciationSpecifications.filter(BRANCH_ID, BUS_ID, FROM, TO_EXCLUSIVE)
                .toPredicate(root, query, cb);

        assertSame(combined, result);
        verify(cb, never()).lessThanOrEqualTo(calculatedAtPath, TO_EXCLUSIVE);
    }

    @Test
    void withoutFilters_returnsEmptyConjunction() {
        when(cb.and()).thenReturn(combined);

        Predicate result = TripDepreciationSpecifications.filter(null, null, null, null).toPredicate(root, query, cb);

        assertSame(combined, result);
        verifyNoInteractions(root, query);
    }

    @Test
    void onlyBranch_filtersByBranch() {
        doReturn(branchPath).when(root).get("branchId");
        when(cb.equal(branchPath, BRANCH_ID)).thenReturn(branchPredicate);
        when(cb.and(branchPredicate)).thenReturn(combined);

        Predicate result = TripDepreciationSpecifications.filter(BRANCH_ID, null, null, null)
                .toPredicate(root, query, cb);

        assertSame(combined, result);
        verify(root, never()).get("busId");
        verify(root, never()).get("calculatedAt");
    }
}
