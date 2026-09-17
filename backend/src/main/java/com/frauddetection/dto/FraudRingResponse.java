package com.frauddetection.dto;

import java.util.List;

public record FraudRingResponse(
    String account1Id,
    String account2Id,
    List<String> sharedDevices,
    List<String> sharedIpAddresses,
    long sharedTransactionCount
) {}
