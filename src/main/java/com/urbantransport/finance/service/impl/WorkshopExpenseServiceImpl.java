package com.urbantransport.finance.service.impl;

import com.urbantransport.finance.client.FleetBusResponse;
import com.urbantransport.finance.client.FleetClient;
import com.urbantransport.finance.domain.ExpenseCategoryEntity;
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
import com.urbantransport.finance.repository.WorkshopExpenseSpecifications;
import com.urbantransport.finance.service.OutboxService;
import com.urbantransport.finance.service.WorkshopExpenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkshopExpenseServiceImpl implements WorkshopExpenseService {

    private static final int MAX_PAGE_SIZE = 100;

    private final WorkshopExpenseRepository workshopExpenseRepository;
    private final WorkshopExpenseLineRepository workshopExpenseLineRepository;
    private final ExpenseCategoryRepository expenseCategoryRepository;
    private final OutboxService outboxService;
    private final FleetClient fleetClient;
    private final TransactionTemplate transactionTemplate;

    @Value("${app.zone:America/Guatemala}")
    private ZoneId zone;

    @Override
    public WorkshopExpenseResponse register(CreateWorkshopExpenseRequest request, UUID branchId, UUID createdBy) {
        Map<UUID, String> categoryNames = validateCategories(request.lines());

        FleetBusResponse bus = fleetClient.getBus(request.busId());
        if (!bus.branchId().equals(branchId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCodes.BUS_NOT_IN_BRANCH,
                    "El bus no pertenece a tu sucursal");
        }

        LocalDate serviceDate = request.serviceDate() != null ? request.serviceDate() : LocalDate.now(zone);
        List<WorkshopExpenseLineEntity> lines = request.lines().stream()
                .map(this::toLine)
                .toList();
        BigDecimal total = lines.stream()
                .map(WorkshopExpenseLineEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        WorkshopExpenseEntity expense = transactionTemplate.execute(status -> {
            WorkshopExpenseEntity saved = workshopExpenseRepository.save(WorkshopExpenseEntity.builder()
                    .busId(bus.id())
                    .branchId(branchId)
                    .serviceDate(serviceDate)
                    .total(total)
                    .createdBy(createdBy)
                    .build());

            lines.forEach(line -> line.setExpenseId(saved.getId()));
            workshopExpenseLineRepository.saveAll(lines);

            outboxService.record(EventTypes.AGGREGATE_WORKSHOP_EXPENSE, saved.getId(),
                    EventTypes.WORKSHOP_EXPENSE_REGISTERED,
                    WorkshopExpenseRegisteredPayload.from(saved, lines, categoryNames));
            return saved;
        });

        return WorkshopExpenseResponse.fromEntity(expense, lines, categoryNames);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<WorkshopExpenseSummaryResponse> search(UUID branchId, UUID busId, UUID categoryId,
                                                               LocalDate from, LocalDate to, int page, int size) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_DATE_RANGE,
                    "La fecha final no puede ser anterior a la inicial");
        }
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.of(workshopExpenseRepository
                .findAll(WorkshopExpenseSpecifications.filter(branchId, busId, categoryId, from, to), pageable)
                .map(WorkshopExpenseSummaryResponse::fromEntity));
    }

    @Override
    @Transactional(readOnly = true)
    public WorkshopExpenseResponse findById(UUID id, UUID scopeBranchId) {
        WorkshopExpenseEntity expense = getOrThrow(id);
        if (scopeBranchId != null && !expense.getBranchId().equals(scopeBranchId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCodes.EXPENSE_NOT_IN_BRANCH,
                    "El gasto no pertenece a tu sucursal");
        }

        List<WorkshopExpenseLineEntity> lines = workshopExpenseLineRepository.findByExpenseId(id);
        Map<UUID, String> categoryNames = expenseCategoryRepository
                .findAllById(lines.stream().map(WorkshopExpenseLineEntity::getCategoryId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(ExpenseCategoryEntity::getId, ExpenseCategoryEntity::getName));
        return WorkshopExpenseResponse.fromEntity(expense, lines, categoryNames);
    }

    private WorkshopExpenseEntity getOrThrow(UUID id) {
        return workshopExpenseRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCodes.EXPENSE_NOT_FOUND,
                        "Gasto no encontrado"));
    }

    private Map<UUID, String> validateCategories(List<WorkshopExpenseLineRequest> lines) {
        Map<UUID, ExpenseCategoryEntity> categories = expenseCategoryRepository
                .findAllById(lines.stream().map(WorkshopExpenseLineRequest::categoryId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(ExpenseCategoryEntity::getId, category -> category));

        for (int i = 0; i < lines.size(); i++) {
            ExpenseCategoryEntity category = categories.get(lines.get(i).categoryId());
            if (category == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, ErrorCodes.CATEGORY_NOT_FOUND,
                        "Línea " + (i + 1) + ": categoría no encontrada");
            }
            if (!category.isActive()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.CATEGORY_INACTIVE,
                        "Línea " + (i + 1) + ": la categoría " + category.getName() + " está inactiva");
            }
        }

        return categories.values().stream()
                .collect(Collectors.toMap(ExpenseCategoryEntity::getId, ExpenseCategoryEntity::getName));
    }

    private WorkshopExpenseLineEntity toLine(WorkshopExpenseLineRequest line) {
        return WorkshopExpenseLineEntity.builder()
                .categoryId(line.categoryId())
                .description(normalizeDescription(line.description()))
                .type(line.type())
                .amount(line.amount().setScale(2, RoundingMode.HALF_UP))
                .build();
    }

    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.trim();
    }
}
