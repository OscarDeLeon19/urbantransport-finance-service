package com.urbantransport.finance.service.impl;

import com.urbantransport.finance.domain.DepreciationRateEntity;
import com.urbantransport.finance.dto.CreateDepreciationRateRequest;
import com.urbantransport.finance.dto.DepreciationRateResponse;
import com.urbantransport.finance.dto.PageResponse;
import com.urbantransport.finance.event.DepreciationRateChangedPayload;
import com.urbantransport.finance.event.EventTypes;
import com.urbantransport.finance.exception.ApiException;
import com.urbantransport.finance.exception.ErrorCodes;
import com.urbantransport.finance.repository.DepreciationRateRepository;
import com.urbantransport.finance.service.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepreciationRateServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("America/Guatemala");
    private static final UUID RATE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final BigDecimal AMOUNT = new BigDecimal("0.7500");

    @Mock
    private DepreciationRateRepository depreciationRateRepository;

    @Mock
    private OutboxService outboxService;

    @Captor
    private ArgumentCaptor<DepreciationRateEntity> rateCaptor;

    @Captor
    private ArgumentCaptor<Instant> instantCaptor;

    @Captor
    private ArgumentCaptor<Pageable> pageableCaptor;

    private DepreciationRateServiceImpl depreciationRateService;

    @BeforeEach
    void setUp() {
        depreciationRateService = new DepreciationRateServiceImpl(depreciationRateRepository, outboxService);
        ReflectionTestUtils.setField(depreciationRateService, "zone", ZONE);
    }

    @Nested
    class Create {

        @Test
        void withoutDate_isEffectiveNowAndRecordsEvent() {
            when(depreciationRateRepository.save(any(DepreciationRateEntity.class))).thenAnswer(invocation ->
                    withId(invocation.getArgument(0)));
            Instant before = Instant.now();

            DepreciationRateResponse response = depreciationRateService.create(
                    new CreateDepreciationRateRequest(new BigDecimal("0.75"), null), USER_ID);

            Instant after = Instant.now();
            verify(depreciationRateRepository).save(rateCaptor.capture());
            DepreciationRateEntity saved = rateCaptor.getValue();
            assertEquals(AMOUNT, saved.getAmountPerKm());
            assertEquals(USER_ID, saved.getCreatedBy());
            assertBetween(before, after, saved.getEffectiveFrom());
            verify(outboxService).record(EventTypes.AGGREGATE_DEPRECIATION_RATE, RATE_ID,
                    EventTypes.DEPRECIATION_RATE_CHANGED, DepreciationRateChangedPayload.from(withId(saved)));
            assertEquals(RATE_ID, response.getId());
            assertEquals(AMOUNT, response.getAmountPerKm());
            assertEquals(USER_ID, response.getCreatedBy());
        }

        @Test
        void todayDate_isEffectiveNowInsteadOfMidnight() {
            when(depreciationRateRepository.save(any(DepreciationRateEntity.class))).thenAnswer(invocation ->
                    withId(invocation.getArgument(0)));
            Instant before = Instant.now();

            DepreciationRateResponse response = depreciationRateService.create(
                    new CreateDepreciationRateRequest(AMOUNT, LocalDate.now(ZONE)), USER_ID);

            assertBetween(before, Instant.now(), response.getEffectiveFrom());
        }

        @ParameterizedTest
        @ValueSource(ints = {-5, 1, 10})
        void otherDate_startsAtMidnightInGuatemala(int daysFromToday) {
            LocalDate effectiveDate = LocalDate.now(ZONE).plusDays(daysFromToday);
            when(depreciationRateRepository.save(any(DepreciationRateEntity.class))).thenAnswer(invocation ->
                    withId(invocation.getArgument(0)));

            DepreciationRateResponse response = depreciationRateService.create(
                    new CreateDepreciationRateRequest(AMOUNT, effectiveDate), USER_ID);

            assertEquals(effectiveDate.atStartOfDay(ZONE).toInstant(), response.getEffectiveFrom());
        }

        @Test
        void amount_isStoredWithFourDecimals() {
            when(depreciationRateRepository.save(any(DepreciationRateEntity.class))).thenAnswer(invocation ->
                    withId(invocation.getArgument(0)));

            DepreciationRateResponse response = depreciationRateService.create(
                    new CreateDepreciationRateRequest(new BigDecimal("1.23456"), null), USER_ID);

            assertEquals(new BigDecimal("1.2346"), response.getAmountPerKm());
        }
    }

    @Nested
    class FindCurrent {

        @Test
        void existingRate_returnsLatestEffectiveAtNow() {
            Instant before = Instant.now();
            when(depreciationRateRepository.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                    any(Instant.class))).thenReturn(Optional.of(rate(before.minusSeconds(3600))));

            DepreciationRateResponse response = depreciationRateService.findCurrent();

            verify(depreciationRateRepository).findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                    instantCaptor.capture());
            assertBetween(before, Instant.now(), instantCaptor.getValue());
            assertEquals(RATE_ID, response.getId());
            assertEquals(AMOUNT, response.getAmountPerKm());
        }

        @Test
        void withoutRate_throwsNotFound() {
            when(depreciationRateRepository.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                    any(Instant.class))).thenReturn(Optional.empty());

            assertApiError(HttpStatus.NOT_FOUND, ErrorCodes.DEPRECIATION_RATE_NOT_FOUND,
                    () -> depreciationRateService.findCurrent());

            verifyNoInteractions(outboxService);
        }
    }

    @Nested
    class Search {

        @Test
        void outOfRangePaging_isClampedAndSortedByEffectiveFrom() {
            when(depreciationRateRepository.findAll(any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(rate(Instant.now())), PageRequest.of(0, 100), 1));

            PageResponse<DepreciationRateResponse> result = depreciationRateService.search(-3, 1000);

            verify(depreciationRateRepository).findAll(pageableCaptor.capture());
            Pageable pageable = pageableCaptor.getValue();
            assertEquals(0, pageable.getPageNumber());
            assertEquals(100, pageable.getPageSize());
            assertEquals(Sort.by(Sort.Direction.DESC, "effectiveFrom"), pageable.getSort());
            assertEquals(1, result.getTotalElements());
            assertEquals(RATE_ID, result.getContent().getFirst().getId());
        }

        @Test
        void zeroSize_usesSizeOne() {
            when(depreciationRateRepository.findAll(any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 1), 0));

            PageResponse<DepreciationRateResponse> result = depreciationRateService.search(1, 0);

            verify(depreciationRateRepository).findAll(pageableCaptor.capture());
            assertEquals(1, pageableCaptor.getValue().getPageNumber());
            assertEquals(1, pageableCaptor.getValue().getPageSize());
            assertTrue(result.getContent().isEmpty());
        }
    }

    private static DepreciationRateEntity rate(Instant effectiveFrom) {
        return DepreciationRateEntity.builder()
                .id(RATE_ID)
                .amountPerKm(AMOUNT)
                .effectiveFrom(effectiveFrom)
                .createdBy(USER_ID)
                .build();
    }

    private static DepreciationRateEntity withId(DepreciationRateEntity rate) {
        return DepreciationRateEntity.builder()
                .id(RATE_ID)
                .amountPerKm(rate.getAmountPerKm())
                .effectiveFrom(rate.getEffectiveFrom())
                .createdBy(rate.getCreatedBy())
                .build();
    }

    private static void assertBetween(Instant from, Instant to, Instant actual) {
        assertFalse(actual.isBefore(from), actual + " es anterior a " + from);
        assertFalse(actual.isAfter(to), actual + " es posterior a " + to);
    }

    private static void assertApiError(HttpStatus status, String code, Executable action) {
        ApiException exception = assertThrows(ApiException.class, action);
        assertEquals(status, exception.getStatus());
        assertEquals(code, exception.getCode());
    }
}
