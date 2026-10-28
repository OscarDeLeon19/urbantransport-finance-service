package com.urbantransport.finance.repository;

import com.urbantransport.finance.domain.WorkshopExpenseLineEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WorkshopExpenseLineRepository extends JpaRepository<WorkshopExpenseLineEntity, UUID> {

    boolean existsByCategoryId(UUID categoryId);

    List<WorkshopExpenseLineEntity> findByExpenseId(UUID expenseId);
}
