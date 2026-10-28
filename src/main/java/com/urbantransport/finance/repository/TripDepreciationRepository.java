package com.urbantransport.finance.repository;

import com.urbantransport.finance.domain.TripDepreciationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface TripDepreciationRepository extends JpaRepository<TripDepreciationEntity, UUID>,
        JpaSpecificationExecutor<TripDepreciationEntity> {
}
