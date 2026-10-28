package com.urbantransport.finance.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "trip_depreciations")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TripDepreciationEntity {

    @Id
    @Column(name = "trip_id")
    private UUID tripId;

    @Column(name = "bus_id", nullable = false)
    private UUID busId;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal km;

    @Column(name = "rate_applied", nullable = false, precision = 10, scale = 4)
    private BigDecimal rateApplied;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @CreationTimestamp
    @Column(name = "calculated_at", nullable = false, updatable = false)
    private Instant calculatedAt;
}
