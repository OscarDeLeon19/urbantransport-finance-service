package com.urbantransport.finance.exception;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ErrorCodes {

    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String ACCESS_DENIED = "ACCESS_DENIED";
    public static final String BRANCH_REQUIRED = "BRANCH_REQUIRED";
    public static final String BRANCH_FORBIDDEN = "BRANCH_FORBIDDEN";
    public static final String FLEET_UNAVAILABLE = "FLEET_UNAVAILABLE";
    public static final String FLEET_ERROR = "FLEET_ERROR";
    public static final String CATEGORY_ALREADY_EXISTS = "CATEGORY_ALREADY_EXISTS";
    public static final String CATEGORY_NOT_FOUND = "CATEGORY_NOT_FOUND";
    public static final String CATEGORY_INACTIVE = "CATEGORY_INACTIVE";
    public static final String CATEGORY_IN_USE = "CATEGORY_IN_USE";
}
