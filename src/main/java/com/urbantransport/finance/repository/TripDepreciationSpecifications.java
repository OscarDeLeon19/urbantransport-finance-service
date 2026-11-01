package com.urbantransport.finance.repository;

import com.urbantransport.finance.domain.TripDepreciationEntity;
import jakarta.persistence.criteria.Predicate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TripDepreciationSpecifications {

    public static Specification<TripDepreciationEntity> filter(UUID branchId, UUID busId, Instant from,
                                                               Instant toExclusive) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (branchId != null) {
                predicates.add(cb.equal(root.get("branchId"), branchId));
            }
            if (busId != null) {
                predicates.add(cb.equal(root.get("busId"), busId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("calculatedAt"), from));
            }
            if (toExclusive != null) {
                predicates.add(cb.lessThan(root.get("calculatedAt"), toExclusive));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
