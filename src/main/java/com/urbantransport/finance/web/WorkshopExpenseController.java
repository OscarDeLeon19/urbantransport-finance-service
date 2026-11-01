package com.urbantransport.finance.web;

import com.urbantransport.finance.dto.CreateWorkshopExpenseRequest;
import com.urbantransport.finance.dto.PageResponse;
import com.urbantransport.finance.dto.WorkshopExpenseResponse;
import com.urbantransport.finance.dto.WorkshopExpenseSummaryResponse;
import com.urbantransport.finance.exception.ApiException;
import com.urbantransport.finance.exception.ErrorCodes;
import com.urbantransport.finance.service.WorkshopExpenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/workshop-expenses")
@RequiredArgsConstructor
public class WorkshopExpenseController {

    private static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";

    private final WorkshopExpenseService workshopExpenseService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('BRANCH_ADMIN')")
    public WorkshopExpenseResponse register(@Valid @RequestBody CreateWorkshopExpenseRequest request,
                                            @AuthenticationPrincipal Jwt jwt) {
        return workshopExpenseService.register(request, branchIdOf(jwt), UUID.fromString(jwt.getSubject()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BRANCH_ADMIN', 'SYSTEM_ADMIN')")
    public PageResponse<WorkshopExpenseSummaryResponse> search(@RequestParam(required = false) UUID branchId,
                                                               @RequestParam(required = false) UUID busId,
                                                               @RequestParam(required = false) UUID categoryId,
                                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "20") int size,
                                                               @AuthenticationPrincipal Jwt jwt) {
        return workshopExpenseService.search(resolveBranchFilter(jwt, branchId), busId, categoryId, from, to,
                page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('BRANCH_ADMIN', 'SYSTEM_ADMIN')")
    public WorkshopExpenseResponse findById(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return workshopExpenseService.findById(id, isSystemAdmin(jwt) ? null : branchIdOf(jwt));
    }

    private UUID resolveBranchFilter(Jwt jwt, UUID requestedBranchId) {
        if (isSystemAdmin(jwt)) {
            return requestedBranchId;
        }
        UUID ownBranchId = branchIdOf(jwt);
        if (requestedBranchId != null && !requestedBranchId.equals(ownBranchId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCodes.BRANCH_FORBIDDEN,
                    "Solo puedes consultar gastos de tu sucursal");
        }
        return ownBranchId;
    }

    private boolean isSystemAdmin(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles != null && roles.contains(SYSTEM_ADMIN);
    }

    private UUID branchIdOf(Jwt jwt) {
        String branchId = jwt.getClaimAsString("branchId");
        if (branchId == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCodes.BRANCH_REQUIRED, "El usuario no tiene sucursal asignada");
        }
        return UUID.fromString(branchId);
    }
}
