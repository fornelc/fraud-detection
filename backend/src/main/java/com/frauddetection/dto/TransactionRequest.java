package com.frauddetection.dto;

public record TransactionRequest(
    String transactionId,
    String accountId,
    String accountName,
    String accountEmail,
    double amount,
    String deviceId,
    String deviceType,
    String deviceFingerprint,
    String ipAddress,
    String ipCountry,
    String merchantId,
    String merchantName,
    String merchantCategory
) {}
