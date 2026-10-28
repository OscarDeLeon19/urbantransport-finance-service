package com.urbantransport.finance.web;

import com.urbantransport.finance.dto.CreateDepreciationRateRequest;
import com.urbantransport.finance.dto.DepreciationRateResponse;
import com.urbantransport.finance.dto.PageResponse;
import com.urbantransport.finance.service.DepreciationRateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/depreciation-rates")
@RequiredArgsConstructor
public class DepreciationRateController {

    private final DepreciationRateService depreciationRateService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public DepreciationRateResponse create(@Valid @RequestBody CreateDepreciationRateRequest request,
                                           @AuthenticationPrincipal Jwt jwt) {
        return depreciationRateService.create(request, UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/current")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public DepreciationRateResponse findCurrent() {
        return depreciationRateService.findCurrent();
    }

    @GetMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public PageResponse<DepreciationRateResponse> search(@RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "20") int size) {
        return depreciationRateService.search(page, size);
    }
}
