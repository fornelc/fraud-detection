package com.frauddetection.dto;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record AlertEvent(
    String accountId,
    String transactionId,
    double riskScore,
    String riskLevel,
    String message,
    LocalDateTime timestamp
) {}
