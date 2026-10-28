package com.urbantransport.finance.repository;

import com.urbantransport.finance.domain.WorkshopExpenseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface WorkshopExpenseRepository extends JpaRepository<WorkshopExpenseEntity, UUID>,
        JpaSpecificationExecutor<WorkshopExpenseEntity> {
}
