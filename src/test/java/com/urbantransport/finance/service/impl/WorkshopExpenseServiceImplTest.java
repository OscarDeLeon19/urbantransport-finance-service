package com.urbantransport.finance.service.impl;

import com.urbantransport.finance.client.FleetBusResponse;
import com.urbantransport.finance.client.FleetClient;
import com.urbantransport.finance.domain.ExpenseCategoryEntity;
import com.urbantransport.finance.domain.ExpenseLineType;
import com.urbantransport.finance.domain.WorkshopExpenseEntity;
import com.urbantransport.finance.domain.WorkshopExpenseLineEntity;
import com.urbantransport.finance.dto.CreateWorkshopExpenseRequest;
import com.urbantransport.finance.dto.PageResponse;
import com.urbantransport.finance.dto.WorkshopExpenseLineRequest;
import com.urbantransport.finance.dto.WorkshopExpenseResponse;
import com.urbantransport.finance.dto.WorkshopExpenseSummaryResponse;
import com.urbantransport.finance.event.EventTypes;
import com.urbantransport.finance.event.WorkshopExpenseRegisteredPayload;
import com.urbantransport.finance.exception.ApiException;
import com.urbantransport.finance.exception.ErrorCodes;
import com.urbantransport.finance.repository.ExpenseCategoryRepository;
import com.urbantransport.finance.repository.WorkshopExpenseLineRepository;
import com.urbantransport.finance.repository.WorkshopExpenseRepository;
import com.urbantransport.finance.service.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
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
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkshopExpenseServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("America/Guatemala");
    private static final UUID EXPENSE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BUS_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID BRANCH_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID OTHER_BRANCH_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID USER_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID TIRES_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID OIL_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final String TIRES = "Llantas";
    private static final String OIL = "Cambio de aceite";
    private static final Instant CREATED_AT = Instant.parse("2026-10-01T15:00:00Z");
    private static final List<UUID> LINE_IDS = List.of(
            UUID.fromString("88888888-8888-8888-8888-888888888881"),
            UUID.fromString("88888888-8888-8888-8888-888888888882"),
            UUID.fromString("88888888-8888-8888-8888-888888888883"));

    @Mock
    private WorkshopExpenseRepository workshopExpenseRepository;

    @Mock
    private WorkshopExpenseLineRepository workshopExpenseLineRepository;

    @Mock
    private ExpenseCategoryRepository expenseCategoryRepository;

    @Mock
    private OutboxService outboxService;

    @Mock
    private FleetClient fleetClient;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Captor
    private ArgumentCaptor<WorkshopExpenseEntity> expenseCaptor;

    @Captor
    private ArgumentCaptor<List<WorkshopExpenseLineEntity>> linesCaptor;

    @Captor
    private ArgumentCaptor<Pageable> pageableCaptor;

    private WorkshopExpenseServiceImpl workshopExpenseService;

    @BeforeEach
    void setUp() {
        workshopExpenseService = new WorkshopExpenseServiceImpl(workshopExpenseRepository,
                workshopExpenseLineRepository, expenseCategoryRepository, outboxService, fleetClient,
                transactionTemplate);
        ReflectionTestUtils.setField(workshopExpenseService, "zone", ZONE);
    }

    @Nested
    class Register {

        @Test
        void validRequest_savesExpenseLinesAndTotalAndRecordsEvent() {
            LocalDate serviceDate = today().minusDays(2);
            when(expenseCategoryRepository.findAllById(List.of(TIRES_ID, OIL_ID)))
                    .thenReturn(List.of(category(TIRES_ID, TIRES, true), category(OIL_ID, OIL, true)));
            when(fleetClient.getBus(BUS_ID)).thenReturn(bus(BRANCH_ID));
            when(workshopExpenseRepository.save(any(WorkshopExpenseEntity.class))).thenAnswer(invocation -> {
                WorkshopExpenseEntity expense = invocation.getArgument(0);
                expense.setId(EXPENSE_ID);
                return expense;
            });
            runTransactionsInline();
            CreateWorkshopExpenseRequest request = new CreateWorkshopExpenseRequest(BUS_ID, serviceDate, List.of(
                    line(TIRES_ID, "  Cambio de llantas ", ExpenseLineType.PART, "1200"),
                    line(OIL_ID, "Cambio de aceite", ExpenseLineType.LABOR, "150.5")));

            WorkshopExpenseResponse response = workshopExpenseService.register(request, BRANCH_ID, USER_ID);

            verify(workshopExpenseRepository).save(expenseCaptor.capture());
            WorkshopExpenseEntity expense = expenseCaptor.getValue();
            assertEquals(BUS_ID, expense.getBusId());
            assertEquals(BRANCH_ID, expense.getBranchId());
            assertEquals(serviceDate, expense.getServiceDate());
            assertEquals(new BigDecimal("1350.50"), expense.getTotal());
            assertEquals(USER_ID, expense.getCreatedBy());

            verify(workshopExpenseLineRepository).saveAll(linesCaptor.capture());
            List<WorkshopExpenseLineEntity> lines = linesCaptor.getValue();
            assertEquals(2, lines.size());
            assertTrue(lines.stream().allMatch(line -> EXPENSE_ID.equals(line.getExpenseId())));
            assertEquals(TIRES_ID, lines.getFirst().getCategoryId());
            assertEquals("Cambio de llantas", lines.getFirst().getDescription());
            assertEquals(ExpenseLineType.PART, lines.getFirst().getType());
            assertEquals(new BigDecimal("1200.00"), lines.getFirst().getAmount());
            assertEquals(new BigDecimal("150.50"), lines.get(1).getAmount());

            Map<UUID, String> names = Map.of(TIRES_ID, TIRES, OIL_ID, OIL);
            verify(outboxService).record(EventTypes.AGGREGATE_WORKSHOP_EXPENSE, EXPENSE_ID,
                    EventTypes.WORKSHOP_EXPENSE_REGISTERED, WorkshopExpenseRegisteredPayload.from(expense, lines, names));

            assertEquals(EXPENSE_ID, response.getId());
            assertEquals(new BigDecimal("1350.50"), response.getTotal());
            assertEquals(2, response.getLines().size());
            assertEquals(TIRES, response.getLines().getFirst().getCategoryName());
            assertEquals(OIL, response.getLines().get(1).getCategoryName());
        }

        @Test
        void withoutServiceDate_usesTodayInGuatemala() {
            stubValidRegistration(List.of(TIRES_ID));

            WorkshopExpenseResponse response = workshopExpenseService.register(new CreateWorkshopExpenseRequest(BUS_ID,
                    null, List.of(line(TIRES_ID, "Llanta", ExpenseLineType.PART, "100"))), BRANCH_ID, USER_ID);

            assertEquals(today(), response.getServiceDate());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void blankDescription_isSavedAsNull(String description) {
            stubValidRegistration(List.of(TIRES_ID));

            workshopExpenseService.register(new CreateWorkshopExpenseRequest(BUS_ID, today(),
                    List.of(line(TIRES_ID, description, ExpenseLineType.PART, "100"))), BRANCH_ID, USER_ID);

            verify(workshopExpenseLineRepository).saveAll(linesCaptor.capture());
            assertNull(linesCaptor.getValue().getFirst().getDescription());
        }

        @Test
        void repeatedCategory_isLookedUpOnceAndKeepsBothLines() {
            stubValidRegistration(List.of(TIRES_ID));

            WorkshopExpenseResponse response = workshopExpenseService.register(new CreateWorkshopExpenseRequest(BUS_ID,
                    today(), List.of(line(TIRES_ID, "Delantera", ExpenseLineType.PART, "600"),
                    line(TIRES_ID, "Trasera", ExpenseLineType.PART, "600"))), BRANCH_ID, USER_ID);

            assertEquals(2, response.getLines().size());
            assertEquals(new BigDecimal("1200.00"), response.getTotal());
        }

        @Test
        void unknownCategory_throwsNotFoundWithoutCallingFleet() {
            when(expenseCategoryRepository.findAllById(List.of(TIRES_ID, OIL_ID)))
                    .thenReturn(List.of(category(TIRES_ID, TIRES, true)));
            CreateWorkshopExpenseRequest request = new CreateWorkshopExpenseRequest(BUS_ID, today(), List.of(
                    line(TIRES_ID, null, ExpenseLineType.PART, "100"),
                    line(OIL_ID, null, ExpenseLineType.LABOR, "50")));

            ApiException exception = assertApiError(HttpStatus.NOT_FOUND, ErrorCodes.CATEGORY_NOT_FOUND,
                    () -> workshopExpenseService.register(request, BRANCH_ID, USER_ID));

            assertTrue(exception.getMessage().startsWith("Línea 2"));
            verifyNoInteractions(fleetClient, transactionTemplate, workshopExpenseRepository,
                    workshopExpenseLineRepository, outboxService);
        }

        @Test
        void inactiveCategory_throwsBadRequestWithoutCallingFleet() {
            when(expenseCategoryRepository.findAllById(List.of(TIRES_ID)))
                    .thenReturn(List.of(category(TIRES_ID, TIRES, false)));
            CreateWorkshopExpenseRequest request = new CreateWorkshopExpenseRequest(BUS_ID, today(),
                    List.of(line(TIRES_ID, null, ExpenseLineType.PART, "100")));

            ApiException exception = assertApiError(HttpStatus.BAD_REQUEST, ErrorCodes.CATEGORY_INACTIVE,
                    () -> workshopExpenseService.register(request, BRANCH_ID, USER_ID));

            assertTrue(exception.getMessage().startsWith("Línea 1"));
            assertTrue(exception.getMessage().contains(TIRES));
            verifyNoInteractions(fleetClient, transactionTemplate, workshopExpenseRepository,
                    workshopExpenseLineRepository, outboxService);
        }

        @Test
        void busFromOtherBranch_throwsForbiddenWithoutSaving() {
            when(expenseCategoryRepository.findAllById(List.of(TIRES_ID)))
                    .thenReturn(List.of(category(TIRES_ID, TIRES, true)));
            when(fleetClient.getBus(BUS_ID)).thenReturn(bus(OTHER_BRANCH_ID));
            CreateWorkshopExpenseRequest request = new CreateWorkshopExpenseRequest(BUS_ID, today(),
                    List.of(line(TIRES_ID, null, ExpenseLineType.PART, "100")));

            assertApiError(HttpStatus.FORBIDDEN, ErrorCodes.BUS_NOT_IN_BRANCH,
                    () -> workshopExpenseService.register(request, BRANCH_ID, USER_ID));

            verifyNoInteractions(transactionTemplate, workshopExpenseRepository, workshopExpenseLineRepository,
                    outboxService);
        }

        @Test
        void fleetUnavailable_propagatesErrorWithoutSaving() {
            when(expenseCategoryRepository.findAllById(List.of(TIRES_ID)))
                    .thenReturn(List.of(category(TIRES_ID, TIRES, true)));
            when(fleetClient.getBus(BUS_ID)).thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    ErrorCodes.FLEET_UNAVAILABLE, "El servicio de flota no esta disponible"));
            CreateWorkshopExpenseRequest request = new CreateWorkshopExpenseRequest(BUS_ID, today(),
                    List.of(line(TIRES_ID, null, ExpenseLineType.PART, "100")));

            assertApiError(HttpStatus.SERVICE_UNAVAILABLE, ErrorCodes.FLEET_UNAVAILABLE,
                    () -> workshopExpenseService.register(request, BRANCH_ID, USER_ID));

            verifyNoInteractions(transactionTemplate, workshopExpenseRepository, workshopExpenseLineRepository,
                    outboxService);
        }

        private void stubValidRegistration(List<UUID> categoryIds) {
            when(expenseCategoryRepository.findAllById(categoryIds))
                    .thenReturn(categoryIds.stream().map(id -> category(id, TIRES, true)).toList());
            when(fleetClient.getBus(BUS_ID)).thenReturn(bus(BRANCH_ID));
            when(workshopExpenseRepository.save(any(WorkshopExpenseEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            runTransactionsInline();
        }
    }

    @Nested
    class Search {

        @Test
        void outOfRangePaging_isClampedAndSortedByNewest() {
            when(workshopExpenseRepository.findAll(ArgumentMatchers.<Specification<WorkshopExpenseEntity>>any(),
                    any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(expense(BRANCH_ID)), PageRequest.of(0, 100), 1));

            PageResponse<WorkshopExpenseSummaryResponse> result = workshopExpenseService.search(BRANCH_ID, BUS_ID,
                    TIRES_ID, today().minusDays(7), today(), -1, 500);

            verify(workshopExpenseRepository).findAll(ArgumentMatchers.<Specification<WorkshopExpenseEntity>>any(),
                    pageableCaptor.capture());
            Pageable pageable = pageableCaptor.getValue();
            assertEquals(0, pageable.getPageNumber());
            assertEquals(100, pageable.getPageSize());
            assertEquals(Sort.by(Sort.Direction.DESC, "createdAt"), pageable.getSort());
            assertEquals(1, result.getTotalElements());
            assertEquals(EXPENSE_ID, result.getContent().getFirst().getId());
            assertEquals(new BigDecimal("1350.00"), result.getContent().getFirst().getTotal());
        }

        @Test
        void zeroSizeWithoutFilters_usesSizeOne() {
            when(workshopExpenseRepository.findAll(ArgumentMatchers.<Specification<WorkshopExpenseEntity>>any(),
                    any(Pageable.class))).thenReturn(new PageImpl<>(List.of(), PageRequest.of(3, 1), 0));

            workshopExpenseService.search(null, null, null, null, null, 3, 0);

            verify(workshopExpenseRepository).findAll(ArgumentMatchers.<Specification<WorkshopExpenseEntity>>any(),
                    pageableCaptor.capture());
            assertEquals(3, pageableCaptor.getValue().getPageNumber());
            assertEquals(1, pageableCaptor.getValue().getPageSize());
        }

        @Test
        void sameDayRange_isAccepted() {
            when(workshopExpenseRepository.findAll(ArgumentMatchers.<Specification<WorkshopExpenseEntity>>any(),
                    any(Pageable.class))).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

            PageResponse<WorkshopExpenseSummaryResponse> result = workshopExpenseService.search(BRANCH_ID, null, null,
                    today(), today(), 0, 20);

            assertEquals(0, result.getTotalElements());
        }

        @Test
        void toBeforeFrom_throwsInvalidDateRange() {
            LocalDate from = today();
            LocalDate to = today().minusDays(1);

            assertApiError(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_DATE_RANGE,
                    () -> workshopExpenseService.search(BRANCH_ID, null, null, from, to, 0, 20));

            verifyNoInteractions(workshopExpenseRepository);
        }
    }

    @Nested
    class FindById {

        @Test
        void ownBranchExpense_returnsLinesWithCategoryNames() {
            when(workshopExpenseRepository.findById(EXPENSE_ID)).thenReturn(Optional.of(expense(BRANCH_ID)));
            when(workshopExpenseLineRepository.findByExpenseId(EXPENSE_ID)).thenReturn(List.of(
                    savedLine(LINE_IDS.get(0), TIRES_ID, "1200.00"), savedLine(LINE_IDS.get(1), OIL_ID, "100.00"),
                    savedLine(LINE_IDS.get(2), OIL_ID, "50.00")));
            when(expenseCategoryRepository.findAllById(List.of(TIRES_ID, OIL_ID)))
                    .thenReturn(List.of(category(TIRES_ID, TIRES, true), category(OIL_ID, OIL, false)));

            WorkshopExpenseResponse response = workshopExpenseService.findById(EXPENSE_ID, BRANCH_ID);

            assertEquals(EXPENSE_ID, response.getId());
            assertEquals(CREATED_AT, response.getCreatedAt());
            assertEquals(3, response.getLines().size());
            assertEquals(TIRES, response.getLines().getFirst().getCategoryName());
            assertEquals(OIL, response.getLines().get(2).getCategoryName());
        }

        @Test
        void withoutBranchScope_returnsExpenseFromAnyBranch() {
            when(workshopExpenseRepository.findById(EXPENSE_ID)).thenReturn(Optional.of(expense(OTHER_BRANCH_ID)));
            when(workshopExpenseLineRepository.findByExpenseId(EXPENSE_ID)).thenReturn(List.of());
            when(expenseCategoryRepository.findAllById(List.of())).thenReturn(List.of());

            WorkshopExpenseResponse response = workshopExpenseService.findById(EXPENSE_ID, null);

            assertEquals(OTHER_BRANCH_ID, response.getBranchId());
            assertTrue(response.getLines().isEmpty());
        }

        @Test
        void expenseFromOtherBranch_throwsForbidden() {
            when(workshopExpenseRepository.findById(EXPENSE_ID)).thenReturn(Optional.of(expense(OTHER_BRANCH_ID)));

            assertApiError(HttpStatus.FORBIDDEN, ErrorCodes.EXPENSE_NOT_IN_BRANCH,
                    () -> workshopExpenseService.findById(EXPENSE_ID, BRANCH_ID));

            verifyNoInteractions(workshopExpenseLineRepository, expenseCategoryRepository);
        }

        @Test
        void unknownExpense_throwsNotFound() {
            when(workshopExpenseRepository.findById(EXPENSE_ID)).thenReturn(Optional.empty());

            assertApiError(HttpStatus.NOT_FOUND, ErrorCodes.EXPENSE_NOT_FOUND,
                    () -> workshopExpenseService.findById(EXPENSE_ID, BRANCH_ID));

            verify(workshopExpenseLineRepository, never()).findByExpenseId(any());
        }
    }

    private static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    private static FleetBusResponse bus(UUID branchId) {
        return new FleetBusResponse(BUS_ID, "P-123ABC", "DISPONIBLE", branchId);
    }

    private static ExpenseCategoryEntity category(UUID id, String name, boolean active) {
        return ExpenseCategoryEntity.builder().id(id).name(name).active(active).build();
    }

    private static WorkshopExpenseLineRequest line(UUID categoryId, String description, ExpenseLineType type,
                                                   String amount) {
        return new WorkshopExpenseLineRequest(categoryId, description, type, new BigDecimal(amount));
    }

    private static WorkshopExpenseEntity expense(UUID branchId) {
        return WorkshopExpenseEntity.builder()
                .id(EXPENSE_ID)
                .busId(BUS_ID)
                .branchId(branchId)
                .serviceDate(LocalDate.now(ZONE))
                .total(new BigDecimal("1350.00"))
                .createdBy(USER_ID)
                .createdAt(CREATED_AT)
                .build();
    }

    private static WorkshopExpenseLineEntity savedLine(UUID id, UUID categoryId, String amount) {
        return WorkshopExpenseLineEntity.builder()
                .id(id)
                .expenseId(EXPENSE_ID)
                .categoryId(categoryId)
                .type(ExpenseLineType.PART)
                .amount(new BigDecimal(amount))
                .build();
    }

    private void runTransactionsInline() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                invocation.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
    }

    private static ApiException assertApiError(HttpStatus status, String code, Executable action) {
        ApiException exception = assertThrows(ApiException.class, action);
        assertEquals(status, exception.getStatus());
        assertEquals(code, exception.getCode());
        return exception;
    }
}
