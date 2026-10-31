package com.urbantransport.finance;

import com.jayway.jsonpath.JsonPath;
import com.urbantransport.finance.client.FleetBusResponse;
import com.urbantransport.finance.client.FleetClient;
import com.urbantransport.finance.domain.DepreciationRateEntity;
import com.urbantransport.finance.domain.ExpenseCategoryEntity;
import com.urbantransport.finance.domain.ExpenseLineType;
import com.urbantransport.finance.domain.WorkshopExpenseEntity;
import com.urbantransport.finance.domain.WorkshopExpenseLineEntity;
import com.urbantransport.finance.repository.DepreciationRateRepository;
import com.urbantransport.finance.repository.ExpenseCategoryRepository;
import com.urbantransport.finance.repository.TripDepreciationRepository;
import com.urbantransport.finance.repository.WorkshopExpenseLineRepository;
import com.urbantransport.finance.repository.WorkshopExpenseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {

    protected static final ZoneId ZONE = ZoneId.of("America/Guatemala");
    protected static final UUID USER_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    protected static final List<String> SEEDED_CATEGORIES = List.of("Llantas", "Motor", "Frenos", "Suspensión",
            "Cambio de aceite", "Eléctrico", "Carrocería", "Otros");

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected ExpenseCategoryRepository expenseCategoryRepository;

    @Autowired
    protected WorkshopExpenseRepository workshopExpenseRepository;

    @Autowired
    protected WorkshopExpenseLineRepository workshopExpenseLineRepository;

    @Autowired
    protected DepreciationRateRepository depreciationRateRepository;

    @Autowired
    protected TripDepreciationRepository tripDepreciationRepository;

    @MockitoBean
    protected FleetClient fleetClient;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE workshop_expense_lines, workshop_expenses, depreciation_rates, "
                + "trip_depreciations, outbox_events, processed_events CASCADE");
        jdbcTemplate.update("DELETE FROM expense_categories WHERE name NOT IN ("
                + String.join(",", Collections.nCopies(SEEDED_CATEGORIES.size(), "?")) + ")",
                SEEDED_CATEGORIES.toArray());
        jdbcTemplate.update("UPDATE expense_categories SET active = true");
    }

    protected static RequestPostProcessor branchAdmin(UUID branchId) {
        return userWithRole("BRANCH_ADMIN", branchId);
    }

    protected static RequestPostProcessor systemAdmin() {
        return userWithRole("SYSTEM_ADMIN", null);
    }

    protected static RequestPostProcessor userWithRole(String role, UUID branchId) {
        return jwt()
                .jwt(token -> {
                    token.subject(USER_ID.toString()).claim("roles", List.of(role));
                    if (branchId != null) {
                        token.claim("branchId", branchId.toString());
                    }
                })
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    protected static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    protected static UUID idAt(MvcResult result, String path) throws Exception {
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), path));
    }

    protected void givenBus(UUID busId, UUID branchId) {
        when(fleetClient.getBus(busId)).thenReturn(new FleetBusResponse(busId, "P-123ABC", "DISPONIBLE", branchId));
    }

    protected UUID categoryId(String name) {
        return expenseCategoryRepository.findAll().stream()
                .filter(category -> category.getName().equals(name))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    protected ExpenseCategoryEntity saveCategory(String name, boolean active) {
        return expenseCategoryRepository.save(ExpenseCategoryEntity.builder().name(name).active(active).build());
    }

    protected WorkshopExpenseEntity saveExpense(UUID branchId, UUID busId, LocalDate serviceDate, UUID categoryId,
                                                String amount) {
        WorkshopExpenseEntity expense = workshopExpenseRepository.save(WorkshopExpenseEntity.builder()
                .busId(busId)
                .branchId(branchId)
                .serviceDate(serviceDate)
                .total(new BigDecimal(amount))
                .createdBy(USER_ID)
                .build());
        workshopExpenseLineRepository.save(WorkshopExpenseLineEntity.builder()
                .expenseId(expense.getId())
                .categoryId(categoryId)
                .description("Linea de prueba")
                .type(ExpenseLineType.PART)
                .amount(new BigDecimal(amount))
                .build());
        return expense;
    }

    protected DepreciationRateEntity saveRate(String amountPerKm, Instant effectiveFrom) {
        return depreciationRateRepository.save(DepreciationRateEntity.builder()
                .amountPerKm(new BigDecimal(amountPerKm))
                .effectiveFrom(effectiveFrom)
                .createdBy(USER_ID)
                .build());
    }

    protected void insertDepreciation(UUID tripId, UUID branchId, UUID busId, String km, String rate, String amount,
                                      Instant calculatedAt) {
        jdbcTemplate.update("INSERT INTO trip_depreciations (trip_id, bus_id, branch_id, km, rate_applied, amount, "
                        + "calculated_at) VALUES (?, ?, ?, ?, ?, ?, ?)", tripId, busId, branchId, new BigDecimal(km),
                new BigDecimal(rate), new BigDecimal(amount), Timestamp.from(calculatedAt));
    }

    protected List<String> outboxEventTypes(UUID aggregateId) {
        return jdbcTemplate.queryForList(
                "SELECT event_type FROM outbox_events WHERE aggregate_id = ? ORDER BY created_at",
                String.class, aggregateId);
    }

    protected String outboxPayload(UUID aggregateId) {
        return jdbcTemplate.queryForObject("SELECT payload::text FROM outbox_events WHERE aggregate_id = ?",
                String.class, aggregateId);
    }

    protected int count(String sql, Object... args) {
        Integer total = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return total == null ? 0 : total;
    }
}
