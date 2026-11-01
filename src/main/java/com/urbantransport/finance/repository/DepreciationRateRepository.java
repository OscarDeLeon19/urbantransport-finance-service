package com.urbantransport.finance.repository;

import com.urbantransport.finance.domain.DepreciationRateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface DepreciationRateRepository extends JpaRepository<DepreciationRateEntity, UUID> {

    Optional<DepreciationRateEntity> findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(Instant at);
}
