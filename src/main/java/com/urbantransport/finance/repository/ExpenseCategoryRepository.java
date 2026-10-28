package com.urbantransport.finance.repository;

import com.urbantransport.finance.domain.ExpenseCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategoryEntity, UUID> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
}
