package com.urbantransport.finance.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "depreciation_rates")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepreciationRateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "amount_per_km", nullable = false, precision = 10, scale = 4)
    private BigDecimal amountPerKm;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
}
