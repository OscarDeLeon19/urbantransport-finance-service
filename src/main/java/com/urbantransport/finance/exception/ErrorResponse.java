package com.urbantransport.finance.exception;

import lombok.Value;

import java.time.Instant;

@Value
public class ErrorResponse {
    Instant timestamp;
    int status;
    String code;
    String message;
    String path;
}
