package com.urbantransport.finance.repository;

import com.urbantransport.finance.domain.WorkshopExpenseEntity;
import com.urbantransport.finance.domain.WorkshopExpenseLineEntity;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WorkshopExpenseSpecifications {

    public static Specification<WorkshopExpenseEntity> filter(UUID branchId, UUID busId, UUID categoryId,
                                                              LocalDate from, LocalDate to) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (branchId != null) {
                predicates.add(cb.equal(root.get("branchId"), branchId));
            }
            if (busId != null) {
                predicates.add(cb.equal(root.get("busId"), busId));
            }
            if (categoryId != null) {
                Subquery<UUID> lines = criteriaQuery.subquery(UUID.class);
                Root<WorkshopExpenseLineEntity> line = lines.from(WorkshopExpenseLineEntity.class);
                lines.select(line.get("expenseId"))
                        .where(cb.equal(line.get("expenseId"), root.get("id")),
                                cb.equal(line.get("categoryId"), categoryId));
                predicates.add(cb.exists(lines));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("serviceDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("serviceDate"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
