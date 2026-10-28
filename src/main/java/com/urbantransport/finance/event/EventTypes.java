package com.urbantransport.finance.event;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EventTypes {

    public static final String AGGREGATE_WORKSHOP_EXPENSE = "WorkshopExpense";
    public static final String AGGREGATE_DEPRECIATION_RATE = "DepreciationRate";
    public static final String AGGREGATE_TRIP_DEPRECIATION = "TripDepreciation";

    public static final String WORKSHOP_EXPENSE_REGISTERED = "WorkshopExpenseRegistered";
    public static final String DEPRECIATION_RATE_CHANGED = "DepreciationRateChanged";
    public static final String DEPRECIATION_CALCULATED = "DepreciationCalculated";

    public static final String TRIP_COMPLETED = "TripCompleted";
}
