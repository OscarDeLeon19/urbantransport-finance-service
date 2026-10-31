package com.urbantransport.finance.service.impl;

import com.urbantransport.finance.domain.DepreciationRateEntity;
import com.urbantransport.finance.domain.ProcessedEventEntity;
import com.urbantransport.finance.domain.TripDepreciationEntity;
import com.urbantransport.finance.dto.PageResponse;
import com.urbantransport.finance.dto.TripDepreciationResponse;
import com.urbantransport.finance.event.DepreciationCalculatedPayload;
import com.urbantransport.finance.event.EventTypes;
import com.urbantransport.finance.event.TripCompletedPayload;
import com.urbantransport.finance.exception.ApiException;
import com.urbantransport.finance.exception.ErrorCodes;
import com.urbantransport.finance.repository.DepreciationRateRepository;
import com.urbantransport.finance.repository.ProcessedEventRepository;
import com.urbantransport.finance.repository.TripDepreciationRepository;
import com.urbantransport.finance.service.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripDepreciationServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("America/Guatemala");
    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TRIP_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID BUS_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID BRANCH_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID OTHER_BRANCH_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID RATE_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final Instant ARRIVAL = Instant.parse("2026-10-20T18:30:00Z");
    private static final Instant CALCULATED_AT = Instant.parse("2026-10-20T18:31:00Z");

    @Mock
    private TripDepreciationRepository tripDepreciationRepository;

    @Mock
    private DepreciationRateRepository depreciationRateRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private OutboxService outboxService;

    @Captor
    private ArgumentCaptor<TripDepreciationEntity> depreciationCaptor;

    @Captor
    private ArgumentCaptor<ProcessedEventEntity> processedEventCaptor;

    @Captor
    private ArgumentCaptor<Pageable> pageableCaptor;

    private TripDepreciationServiceImpl tripDepreciationService;

    @BeforeEach
    void setUp() {
        tripDepreciationService = new TripDepreciationServiceImpl(tripDepreciationRepository,
                depreciationRateRepository, processedEventRepository, outboxService);
        ReflectionTestUtils.setField(tripDepreciationService, "zone", ZONE);
    }

    @Nested
    class ApplyTripCompleted {

        @Test
        void rateAtArrival_savesKmTimesRateAndRecordsEvent() {
            when(depreciationRateRepository.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(ARRIVAL))
                    .thenReturn(Optional.of(rate("0.7500")));
            when(tripDepreciationRepository.save(any(TripDepreciationEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            tripDepreciationService.applyTripCompleted(EVENT_ID, EventTypes.TRIP_COMPLETED, payload("120.5"));

            verify(tripDepreciationRepository).save(depreciationCaptor.capture());
            TripDepreciationEntity saved = depreciationCaptor.getValue();
            assertEquals(TRIP_ID, saved.getTripId());
            assertEquals(BUS_ID, saved.getBusId());
            assertEquals(BRANCH_ID, saved.getBranchId());
            assertEquals(new BigDecimal("120.50"), saved.getKm());
            assertEquals(new BigDecimal("0.7500"), saved.getRateApplied());
            assertEquals(new BigDecimal("90.38"), saved.getAmount());
            verify(outboxService).record(EventTypes.AGGREGATE_TRIP_DEPRECIATION, TRIP_ID,
                    EventTypes.DEPRECIATION_CALCULATED, DepreciationCalculatedPayload.from(saved, RATE_ID, ARRIVAL));
            assertMarkedProcessed();
        }

        @ParameterizedTest
        @CsvSource({"100, 1.2500, 125.00", "0, 0.7500, 0.00", "33.333, 0.3333, 11.11"})
        void kmAndRate_areRoundedToTwoDecimals(String km, String rate, String expectedAmount) {
            when(depreciationRateRepository.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(ARRIVAL))
                    .thenReturn(Optional.of(rate(rate)));
            when(tripDepreciationRepository.save(any(TripDepreciationEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            tripDepreciationService.applyTripCompleted(EVENT_ID, EventTypes.TRIP_COMPLETED, payload(km));

            verify(tripDepreciationRepository).save(depreciationCaptor.capture());
            assertEquals(new BigDecimal(expectedAmount), depreciationCaptor.getValue().getAmount());
        }

        @Test
        void withoutRate_savesZeroRateAndRecordsEventWithoutRateId() {
            when(depreciationRateRepository.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(ARRIVAL))
                    .thenReturn(Optional.empty());
            when(tripDepreciationRepository.save(any(TripDepreciationEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            tripDepreciationService.applyTripCompleted(EVENT_ID, EventTypes.TRIP_COMPLETED, payload("80"));

            verify(tripDepreciationRepository).save(depreciationCaptor.capture());
            TripDepreciationEntity saved = depreciationCaptor.getValue();
            assertEquals(new BigDecimal("0.0000"), saved.getRateApplied());
            assertEquals(new BigDecimal("0.00"), saved.getAmount());
            assertEquals(new BigDecimal("80.00"), saved.getKm());
            verify(outboxService).record(EventTypes.AGGREGATE_TRIP_DEPRECIATION, TRIP_ID,
                    EventTypes.DEPRECIATION_CALCULATED, DepreciationCalculatedPayload.from(saved, null, ARRIVAL));
            assertMarkedProcessed();
        }

        @Test
        void tripAlreadyCalculated_onlyMarksEvent() {
            when(tripDepreciationRepository.existsById(TRIP_ID)).thenReturn(true);

            tripDepreciationService.applyTripCompleted(EVENT_ID, EventTypes.TRIP_COMPLETED, payload("120"));

            verify(tripDepreciationRepository, never()).save(any());
            verifyNoInteractions(depreciationRateRepository, outboxService);
            assertMarkedProcessed();
        }

        @Test
        void alreadyProcessedEvent_isIgnored() {
            when(processedEventRepository.existsById(EVENT_ID)).thenReturn(true);

            tripDepreciationService.applyTripCompleted(EVENT_ID, EventTypes.TRIP_COMPLETED, payload("120"));

            verifyNoInteractions(tripDepreciationRepository, depreciationRateRepository, outboxService);
            verify(processedEventRepository, never()).save(any());
        }

        private void assertMarkedProcessed() {
            verify(processedEventRepository).save(processedEventCaptor.capture());
            assertEquals(EVENT_ID, processedEventCaptor.getValue().getEventId());
            assertEquals(EventTypes.TRIP_COMPLETED, processedEventCaptor.getValue().getEventType());
        }
    }

    @Nested
    class Search {

        @Test
        void outOfRangePaging_isClampedAndSortedByCalculation() {
            when(tripDepreciationRepository.findAll(ArgumentMatchers.<Specification<TripDepreciationEntity>>any(),
                    any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(depreciation(BRANCH_ID)), PageRequest.of(0, 100), 1));

            PageResponse<TripDepreciationResponse> result = tripDepreciationService.search(BRANCH_ID, BUS_ID,
                    today().minusDays(7), today(), -1, 500);

            verify(tripDepreciationRepository).findAll(ArgumentMatchers.<Specification<TripDepreciationEntity>>any(),
                    pageableCaptor.capture());
            Pageable pageable = pageableCaptor.getValue();
            assertEquals(0, pageable.getPageNumber());
            assertEquals(100, pageable.getPageSize());
            assertEquals(Sort.by(Sort.Direction.DESC, "calculatedAt"), pageable.getSort());
            assertEquals(1, result.getTotalElements());
            TripDepreciationResponse first = result.getContent().getFirst();
            assertEquals(TRIP_ID, first.getTripId());
            assertEquals(new BigDecimal("90.38"), first.getAmount());
            assertEquals(CALCULATED_AT, first.getCalculatedAt());
        }

        @Test
        void zeroSizeWithoutFilters_usesSizeOne() {
            when(tripDepreciationRepository.findAll(ArgumentMatchers.<Specification<TripDepreciationEntity>>any(),
                    any(Pageable.class))).thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 1), 0));

            tripDepreciationService.search(null, null, null, null, 2, 0);

            verify(tripDepreciationRepository).findAll(ArgumentMatchers.<Specification<TripDepreciationEntity>>any(),
                    pageableCaptor.capture());
            assertEquals(2, pageableCaptor.getValue().getPageNumber());
            assertEquals(1, pageableCaptor.getValue().getPageSize());
        }

        @Test
        void toBeforeFrom_throwsInvalidDateRange() {
            LocalDate from = today();
            LocalDate to = today().minusDays(1);

            assertApiError(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_DATE_RANGE,
                    () -> tripDepreciationService.search(BRANCH_ID, null, from, to, 0, 20));

            verifyNoInteractions(tripDepreciationRepository);
        }
    }

    @Nested
    class FindByTripId {

        @Test
        void ownBranchDepreciation_isReturned() {
            when(tripDepreciationRepository.findById(TRIP_ID)).thenReturn(Optional.of(depreciation(BRANCH_ID)));

            TripDepreciationResponse response = tripDepreciationService.findByTripId(TRIP_ID, BRANCH_ID);

            assertEquals(TRIP_ID, response.getTripId());
            assertEquals(BUS_ID, response.getBusId());
            assertEquals(new BigDecimal("120.50"), response.getKm());
            assertEquals(new BigDecimal("0.7500"), response.getRateApplied());
        }

        @Test
        void withoutBranchScope_returnsDepreciationFromAnyBranch() {
            when(tripDepreciationRepository.findById(TRIP_ID)).thenReturn(Optional.of(depreciation(OTHER_BRANCH_ID)));

            TripDepreciationResponse response = tripDepreciationService.findByTripId(TRIP_ID, null);

            assertEquals(OTHER_BRANCH_ID, response.getBranchId());
        }

        @Test
        void depreciationFromOtherBranch_throwsForbidden() {
            when(tripDepreciationRepository.findById(TRIP_ID)).thenReturn(Optional.of(depreciation(OTHER_BRANCH_ID)));

            assertApiError(HttpStatus.FORBIDDEN, ErrorCodes.DEPRECIATION_NOT_IN_BRANCH,
                    () -> tripDepreciationService.findByTripId(TRIP_ID, BRANCH_ID));
        }

        @Test
        void tripWithoutDepreciation_throwsNotFound() {
            when(tripDepreciationRepository.findById(TRIP_ID)).thenReturn(Optional.empty());

            assertApiError(HttpStatus.NOT_FOUND, ErrorCodes.DEPRECIATION_NOT_FOUND,
                    () -> tripDepreciationService.findByTripId(TRIP_ID, BRANCH_ID));
        }
    }

    private static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    private static TripCompletedPayload payload(String km) {
        return new TripCompletedPayload(TRIP_ID, BUS_ID, BRANCH_ID, ARRIVAL, new BigDecimal(km));
    }

    private static DepreciationRateEntity rate(String amountPerKm) {
        return DepreciationRateEntity.builder()
                .id(RATE_ID)
                .amountPerKm(new BigDecimal(amountPerKm))
                .effectiveFrom(ARRIVAL.minusSeconds(86_400))
                .createdBy(UUID.fromString("77777777-7777-7777-7777-777777777777"))
                .build();
    }

    private static TripDepreciationEntity depreciation(UUID branchId) {
        return TripDepreciationEntity.builder()
                .tripId(TRIP_ID)
                .busId(BUS_ID)
                .branchId(branchId)
                .km(new BigDecimal("120.50"))
                .rateApplied(new BigDecimal("0.7500"))
                .amount(new BigDecimal("90.38"))
                .calculatedAt(CALCULATED_AT)
                .build();
    }

    private static void assertApiError(HttpStatus status, String code, Executable action) {
        ApiException exception = assertThrows(ApiException.class, action);
        assertEquals(status, exception.getStatus());
        assertEquals(code, exception.getCode());
    }
}
