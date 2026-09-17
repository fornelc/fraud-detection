package com.frauddetection.dto;

import java.time.LocalDateTime;

public record TransactionResponse(
    String transactionId,
    String accountId,
    double amount,
    double riskScore,
    boolean flagged,
    String status,
    LocalDateTime timestamp
) {}
